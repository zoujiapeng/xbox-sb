#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <netinet/in.h>
#include <pthread.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#define CONTROL_PORT 28550
#define DISCOVERY_PORT 28551
#define MAGIC 0x58414D50u
#define VERSION 1u
#define TYPE_STATE 0x0003u
#define PLAIN_PACKET_BYTES 88

static volatile int running = 1;
struct input_state { uint64_t timestamp_ns; uint64_t buttons; float lx,ly,rx,ry,lt,rt; float ax,ay,az,gx,gy,gz; };
static uint32_t crc_table[256];
static void crc_init(void){for(uint32_t i=0;i<256;i++){uint32_t c=i;for(int k=0;k<8;k++)c=(c&1u)?(0xEDB88320u^(c>>1)):(c>>1);crc_table[i]=c;}}
static uint32_t crc32_compute(const uint8_t*d,size_t len){uint32_t crc=0xFFFFFFFFu;for(size_t i=0;i<len;i++)crc=crc_table[(crc^d[i])&0xFFu]^(crc>>8);return ~crc;}
static uint16_t le16(const uint8_t*p){return(uint16_t)p[0]|((uint16_t)p[1]<<8);}static uint32_t le32(const uint8_t*p){return(uint32_t)p[0]|((uint32_t)p[1]<<8)|((uint32_t)p[2]<<16)|((uint32_t)p[3]<<24);}static uint64_t le64(const uint8_t*p){uint64_t v=0;for(int i=7;i>=0;--i)v=(v<<8)|p[i];return v;}static float lef32(const uint8_t*p){uint32_t u=le32(p);float f;memcpy(&f,&u,sizeof(f));return f;}
static int parse_packet(const uint8_t*buf,size_t len,uint64_t*client_id,struct input_state*s){if(len!=PLAIN_PACKET_BYTES)return-1;if(le32(buf)!=MAGIC||le16(buf+4)!=VERSION||le16(buf+6)!=TYPE_STATE)return-2;uint32_t expected=le32(buf+PLAIN_PACKET_BYTES-4),actual=crc32_compute(buf,PLAIN_PACKET_BYTES-4);if(expected!=actual)return-3;*client_id=le64(buf+12);const uint8_t*p=buf+20;s->timestamp_ns=le64(p);s->buttons=le64(p+8);s->lx=lef32(p+16);s->ly=lef32(p+20);s->rx=lef32(p+24);s->ry=lef32(p+28);s->lt=lef32(p+32);s->rt=lef32(p+36);s->ax=lef32(p+40);s->ay=lef32(p+44);s->az=lef32(p+48);s->gx=lef32(p+52);s->gy=lef32(p+56);s->gz=lef32(p+60);return 0;}
static int emit_event(int fd,int type,int code,int val){struct input_event ev;memset(&ev,0,sizeof(ev));gettimeofday(&ev.time,NULL);ev.type=(uint16_t)type;ev.code=(uint16_t)code;ev.value=val;return write(fd,&ev,sizeof(ev))==sizeof(ev)?0:-1;}
static float clampf(float v,float lo,float hi){return v<lo?lo:(v>hi?hi:v);}static int axis16(float v){return(int)(clampf(v,-1,1)*32767);}static int trig8(float v){return(int)(clampf(v,0,1)*255);}static int has(uint64_t b,int bit){return((b>>bit)&1ull)!=0;}
static void set_abs(struct uinput_user_dev*u,int axis,int min,int max,int flat,int fuzz){u->absmin[axis]=min;u->absmax[axis]=max;u->absflat[axis]=flat;u->absfuzz[axis]=fuzz;}
static int setup_uinput(void){int fd=open("/dev/uinput",O_WRONLY|O_NONBLOCK);if(fd<0){perror("open /dev/uinput");return-1;}ioctl(fd,UI_SET_EVBIT,EV_KEY);ioctl(fd,UI_SET_EVBIT,EV_ABS);int keys[]={BTN_SOUTH,BTN_EAST,BTN_WEST,BTN_NORTH,BTN_TL,BTN_TR,BTN_TL2,BTN_TR2,BTN_SELECT,BTN_START,BTN_MODE,BTN_THUMBL,BTN_THUMBR};for(size_t i=0;i<sizeof(keys)/sizeof(keys[0]);i++)ioctl(fd,UI_SET_KEYBIT,keys[i]);int axes[]={ABS_X,ABS_Y,ABS_RX,ABS_RY,ABS_Z,ABS_RZ,ABS_HAT0X,ABS_HAT0Y};for(size_t i=0;i<sizeof(axes)/sizeof(axes[0]);i++)ioctl(fd,UI_SET_ABSBIT,axes[i]);struct uinput_user_dev u;memset(&u,0,sizeof(u));snprintf(u.name,UINPUT_MAX_NAME_SIZE,"PadMax Pro Virtual Gamepad");u.id.bustype=BUS_USB;u.id.vendor=0x504D;u.id.product=0x4158;u.id.version=1;set_abs(&u,ABS_X,-32767,32767,256,16);set_abs(&u,ABS_Y,-32767,32767,256,16);set_abs(&u,ABS_RX,-32767,32767,256,16);set_abs(&u,ABS_RY,-32767,32767,256,16);set_abs(&u,ABS_Z,0,255,0,0);set_abs(&u,ABS_RZ,0,255,0,0);set_abs(&u,ABS_HAT0X,-1,1,0,0);set_abs(&u,ABS_HAT0Y,-1,1,0,0);if(write(fd,&u,sizeof(u))<0||ioctl(fd,UI_DEV_CREATE)<0){perror("uinput create");close(fd);return-1;}sleep(1);return fd;}
static void apply_state(int fd,const struct input_state*s){uint64_t b=s->buttons;int map[][2]={{BTN_SOUTH,0},{BTN_EAST,1},{BTN_WEST,2},{BTN_NORTH,3},{BTN_TL,4},{BTN_TR,5},{BTN_SELECT,6},{BTN_START,7},{BTN_MODE,8},{BTN_THUMBL,9},{BTN_THUMBR,10}};for(size_t i=0;i<sizeof(map)/sizeof(map[0]);i++)emit_event(fd,EV_KEY,map[i][0],has(b,map[i][1]));emit_event(fd,EV_KEY,BTN_TL2,s->lt>.5f);emit_event(fd,EV_KEY,BTN_TR2,s->rt>.5f);emit_event(fd,EV_ABS,ABS_X,axis16(s->lx));emit_event(fd,EV_ABS,ABS_Y,axis16(-s->ly));emit_event(fd,EV_ABS,ABS_RX,axis16(s->rx));emit_event(fd,EV_ABS,ABS_RY,axis16(-s->ry));emit_event(fd,EV_ABS,ABS_Z,trig8(s->lt));emit_event(fd,EV_ABS,ABS_RZ,trig8(s->rt));emit_event(fd,EV_ABS,ABS_HAT0X,has(b,14)-has(b,13));emit_event(fd,EV_ABS,ABS_HAT0Y,has(b,12)-has(b,11));emit_event(fd,EV_SYN,SYN_REPORT,0);}
static void*discovery_thread(void*arg){(void)arg;int sock=socket(AF_INET,SOCK_DGRAM,0);int yes=1;setsockopt(sock,SOL_SOCKET,SO_REUSEADDR,&yes,sizeof(yes));struct sockaddr_in addr={0};addr.sin_family=AF_INET;addr.sin_addr.s_addr=INADDR_ANY;addr.sin_port=htons(DISCOVERY_PORT);if(bind(sock,(struct sockaddr*)&addr,sizeof(addr))<0){perror("bind discovery");return NULL;}char buf[512];while(running){struct sockaddr_in peer;socklen_t n=sizeof(peer);ssize_t r=recvfrom(sock,buf,sizeof(buf)-1,0,(struct sockaddr*)&peer,&n);if(r<0){if(errno==EINTR)continue;break;}buf[r]=0;if(strncmp(buf,"PMAX_DISCOVERY_V1",18))continue;const char*resp="PMAX_SERVER_V1|Linux PadMax|28550|0||linux-uinput";sendto(sock,resp,strlen(resp),0,(struct sockaddr*)&peer,n);}close(sock);return NULL;}
static void on_signal(int sig){(void)sig;running=0;}
int main(void){signal(SIGINT,on_signal);signal(SIGTERM,on_signal);crc_init();int ufd=setup_uinput();if(ufd<0)return 1;pthread_t dt;pthread_create(&dt,NULL,discovery_thread,NULL);int sock=socket(AF_INET,SOCK_DGRAM,0),yes=1;setsockopt(sock,SOL_SOCKET,SO_REUSEADDR,&yes,sizeof(yes));struct sockaddr_in addr={0};addr.sin_family=AF_INET;addr.sin_addr.s_addr=INADDR_ANY;addr.sin_port=htons(CONTROL_PORT);if(bind(sock,(struct sockaddr*)&addr,sizeof(addr))<0){perror("bind control");return 1;}printf("PadMax Linux server listening UDP %d.\n",CONTROL_PORT);uint8_t buf[2048];while(running){struct sockaddr_in peer;socklen_t n=sizeof(peer);ssize_t r=recvfrom(sock,buf,sizeof(buf),0,(struct sockaddr*)&peer,&n);if(r<0){if(errno==EINTR)continue;break;}uint64_t id;struct input_state st;if(parse_packet(buf,(size_t)r,&id,&st)==0)apply_state(ufd,&st);}close(sock);ioctl(ufd,UI_DEV_DESTROY);close(ufd);running=0;pthread_join(dt,NULL);return 0;}
