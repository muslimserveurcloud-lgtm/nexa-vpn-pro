package com.nexavpn.pro.engine

import android.content.Context
import com.nexavpn.pro.model.ServerConfig

interface Engine {
    val socksPort: Int
    val supportsUdp: Boolean
    val protocolName: String

    fun connect(context: Context, server: ServerConfig): Boolean
    fun isAlive(): Boolean
    fun stop()
}
