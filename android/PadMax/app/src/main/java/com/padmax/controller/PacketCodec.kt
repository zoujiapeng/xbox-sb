package com.padmax.controller

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

object PacketCodec {
    const val MAGIC: Int = 0x58414D50
    const val VERSION: Short = 1
    const val TYPE_STATE: Short = 0x0003
    const val TYPE_STATE_SECURE: Short = 0x1003
    const val PAYLOAD_BYTES = 64
    const val PLAIN_PACKET_BYTES = 88

    fun encodePlain(seq:Int,clientId:Long,state:ControllerState):ByteArray {
        val packet=ByteBuffer.allocate(PLAIN_PACKET_BYTES).order(ByteOrder.LITTLE_ENDIAN);writeHeader(packet,TYPE_STATE,seq,clientId);writePayload(packet,state)
        val crc=CRC32();val arr=packet.array();crc.update(arr,0,PLAIN_PACKET_BYTES-4);packet.putInt(crc.value.toInt());return arr
    }
    fun encodeSecure(seq:Int,clientId:Long,state:ControllerState,crypto:CryptoBox):ByteArray {
        val payload=ByteBuffer.allocate(PAYLOAD_BYTES).order(ByteOrder.LITTLE_ENDIAN);writePayload(payload,state);val nonce=CryptoBox.nonce(clientId,seq);val sealed=crypto.seal(payload.array(),nonce)
        val packet=ByteBuffer.allocate(4+2+2+4+8+12+2+sealed.size).order(ByteOrder.LITTLE_ENDIAN);writeHeader(packet,TYPE_STATE_SECURE,seq,clientId);packet.put(nonce);packet.putShort(sealed.size.toShort());packet.put(sealed);return packet.array()
    }
    private fun writeHeader(packet:ByteBuffer,type:Short,seq:Int,clientId:Long){packet.putInt(MAGIC);packet.putShort(VERSION);packet.putShort(type);packet.putInt(seq);packet.putLong(clientId)}
    private fun writePayload(packet:ByteBuffer,s0:ControllerState){val s=s0.immutableCopy().sanitize();packet.putLong(System.nanoTime());packet.putLong(s.buttons);packet.putFloat(s.lx);packet.putFloat(s.ly);packet.putFloat(s.rx);packet.putFloat(s.ry);packet.putFloat(s.lt);packet.putFloat(s.rt);packet.putFloat(s.accelX);packet.putFloat(s.accelY);packet.putFloat(s.accelZ);packet.putFloat(s.gyroX);packet.putFloat(s.gyroY);packet.putFloat(s.gyroZ)}
}
