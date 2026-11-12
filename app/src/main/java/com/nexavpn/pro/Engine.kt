package com.nexavpn.pro

/** Un moteur ouvre un proxy SOCKS5 local ; tun2socks y branche tout le trafic du téléphone. */
interface Engine {
    val socksPort: Int
    val supportsUdp: Boolean

    /** Établit la connexion (bloquant). Lève une exception en cas d'échec. */
    fun connect()
    fun isAlive(): Boolean
    fun stop()
}
