#!/usr/bin/env python3
"""Send a plain PadMax packet to a Linux server for smoke testing."""
import socket, struct, time, random, zlib, sys, math
host = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1"
port = int(sys.argv[2]) if len(sys.argv) > 2 else 28550
client_id = random.getrandbits(64)
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
seq = 1
print(f"Sending to {host}:{port}, client=0x{client_id:016x}. Ctrl+C to stop.")
try:
    while True:
        t = time.time()
        lx = math.sin(t * 2.0) * 0.8
        payload = struct.pack('<QQffffffffffff', time.monotonic_ns(), 1, lx, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        header = struct.pack('<IHHIQ', 0x58414D50, 1, 3, seq, client_id)
        packet = header + payload
        packet += struct.pack('<I', zlib.crc32(packet) & 0xffffffff)
        sock.sendto(packet, (host, port))
        seq += 1
        time.sleep(1/120)
except KeyboardInterrupt:
    pass
