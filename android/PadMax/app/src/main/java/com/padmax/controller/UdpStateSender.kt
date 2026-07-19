package com.padmax.controller

import android.net.TrafficStats
import android.os.Process
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class UdpStateSender(private val host:String,private val port:Int,private val clientId:Long=CryptoBox.randomClientId(),private val crypto:CryptoBox?=null,private val sendHz:Int=120):AutoCloseable{
 private val latest=AtomicReference(ControllerState());private val running=AtomicBoolean(false);private var thread:Thread?=null
 fun update(state:ControllerState){latest.set(state.immutableCopy().sanitize())}
 fun start(){if(!running.compareAndSet(false,true))return;thread=Thread({runLoop()},"PadMax-UdpStateSender").apply{priority=Thread.MAX_PRIORITY;start()}}
 private fun runLoop(){Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);TrafficStats.setThreadStatsTag(0x504D4158);DatagramSocket().use{socket->socket.trafficClass=0x10;val address=InetAddress.getByName(host);var seq=1;val frameNs=1_000_000_000L/sendHz.coerceAtLeast(30);var next=System.nanoTime();while(running.get()){val state=latest.get();val bytes=if(crypto!=null)PacketCodec.encodeSecure(seq,clientId,state,crypto)else PacketCodec.encodePlain(seq,clientId,state);socket.send(DatagramPacket(bytes,bytes.size,address,port));seq++;next+=frameNs;val sleepNs=next-System.nanoTime();if(sleepNs>1_500_000L)Thread.sleep(sleepNs/1_000_000L,(sleepNs%1_000_000L).toInt())else{Thread.yield();if(sleepNs< -frameNs*4)next=System.nanoTime()}}}}
 override fun close(){running.set(false);thread?.join(300);thread=null}
}
