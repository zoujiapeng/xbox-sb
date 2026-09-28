CC ?= gcc
CFLAGS ?= -O2 -Wall -Wextra -std=c17 -D_GNU_SOURCE
LDFLAGS ?= -pthread

all: padmaxd-linux

padmaxd-linux: padmaxd_linux.c
	$(CC) $(CFLAGS) -o $@ $< $(LDFLAGS)

clean:
	rm -f padmaxd-linux
