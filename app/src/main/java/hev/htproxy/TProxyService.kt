package hev.htproxy

/**
 * Pont JNI vers hev-socks5-tunnel (tun2socks), compilé par le workflow GitHub.
 * Le nom du package et de la classe doivent rester exactement ceux-ci : ils définissent les symboles JNI.
 */
object TProxyService {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic
    external fun TProxyStartService(configPath: String, fd: Int)
    @JvmStatic
    external fun TProxyStopService()
    @JvmStatic
    external fun TProxyGetStats(): LongArray
}
