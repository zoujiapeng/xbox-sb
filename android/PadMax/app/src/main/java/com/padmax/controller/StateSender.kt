package com.padmax.controller

interface StateSender : AutoCloseable {
    fun update(state: ControllerState)
    fun start()
}
