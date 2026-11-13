package hev.htproxy

/**
 * Pont JNI vers hev-socks5-tunnel (tun2socks), compilé par le workflow GitHub.
 * Le nom du package et de la classe ainsi que les signatures doivent correspondre exactement aux symboles JNI.
 */
object TProxyService {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic
    external fun TProxyStartService(configPath: String, fd: Int): Boolean

    @JvmStatic
    external fun TProxyStopService(): Boolean

    @JvmStatic
    external fun TProxyIsRunning(): Boolean

    @JvmStatic
    external fun TProxyGetStats(): LongArray
}
