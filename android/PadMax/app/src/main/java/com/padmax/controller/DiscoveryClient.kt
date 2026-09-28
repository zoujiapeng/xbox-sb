package com.padmax.controller

import android.util.Base64
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

private const val DISCOVERY_PORT = 28551
private const val DISCOVERY_REQUEST = "PMAX_DISCOVERY_V1"

data class PadMaxServer(
    val name: String,
    val host: String,
    val port: Int,
    val secure: Boolean,
    val salt: ByteArray,
    val serverId: String
)

object DiscoveryClient {
    fun discover(timeoutMs: Int = 1000): List<PadMaxServer> {
        val found = linkedMapOf<String, PadMaxServer>()
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.broadcast = true
            socket.soTimeout = timeoutMs
            socket.bind(InetSocketAddress(0))
            val payload = DISCOVERY_REQUEST.toByteArray(Charsets.US_ASCII)
            val packet = DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)
            socket.send(packet)
            val end = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(512)
            while (System.currentTimeMillis() < end) {
                try {
                    val resp = DatagramPacket(buf, buf.size)
                    socket.receive(resp)
                    parseResponse(String(resp.data, 0, resp.length, Charsets.UTF_8), resp.address.hostAddress)?.let {
                        found[it.host + ":" + it.port] = it
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }
        return found.values.toList()
    }

    fun parseResponse(text: String, host: String): PadMaxServer? {
        val parts = text.trim().split('|')
        if (parts.size < 6 || parts[0] != "PMAX_SERVER_V1") return null
        val name = parts[1].ifBlank { "PadMax Server" }
        val port = parts[2].toIntOrNull() ?: return null
        val secure = parts[3] == "1"
        val salt = if (parts[4].isBlank()) ByteArray(0) else Base64.decode(parts[4], Base64.NO_WRAP)
        return PadMaxServer(name, host, port, secure, salt, parts[5])
    }
}
