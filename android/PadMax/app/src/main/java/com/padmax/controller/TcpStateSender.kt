package com.padmax.controller

import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class TcpStateSender(
    private val host: String,
    private val port: Int,
    private val clientId: Long = CryptoBox.randomClientId(),
    private val sendHz: Int = 120,
    private val connectTimeoutMs: Int = 900
) : StateSender {
    private val latest = AtomicReference(ControllerState())
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private var socket: Socket? = null

    fun connect() {
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(host, port), connectTimeoutMs)
        socket = s
    }

    override fun update(state: ControllerState) { latest.set(state.immutableCopy().sanitize()) }

    override fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread({ runLoop() }, "PadMax-TcpStateSender").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    private fun runLoop() {
        try {
            val out = BufferedOutputStream(socket!!.getOutputStream(), 1 shl 16)
            var seq = 1
            val frameNs = 1_000_000_000L / sendHz.coerceAtLeast(30)
            var next = System.nanoTime()
            while (running.get()) {
                out.write(PacketCodec.encodePlain(seq, clientId, latest.get()))
                out.flush()
                seq++
                next += frameNs
                val sleepNs = next - System.nanoTime()
                if (sleepNs > 1_500_000L) Thread.sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                else { Thread.yield(); if (sleepNs < -frameNs * 4) next = System.nanoTime() }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { socket?.close() }
        }
    }

    override fun close() {
        running.set(false)
        runCatching { socket?.close() }
        thread?.join(300)
        thread = null
    }
}
