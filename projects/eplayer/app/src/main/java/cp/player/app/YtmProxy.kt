package cp.player.app

import android.content.Context
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Client-neutral local SOCKS5/HTTP endpoint used only for YouTube Music metadata requests.
 * The native source and audio playback keep going direct, so a slow route cannot affect listening.
 *
 * Local SOCKS5 and HTTP are supported transports. vless://, vmess://, ss:// and trojan:// links describe
 * encrypted tunnels rather than proxy endpoints: they must be terminated by
 * the VPN client first, which can expose a local SOCKS5/HTTP port.
 */
internal object YtmProxy {
    private const val PREFS = "eplayer_ui"
    private const val KEY = "ytm_proxy"

    data class Config(val host: String, val port: Int, val kind: Proxy.Type = Proxy.Type.SOCKS) {
        fun address() = if (host.contains(':')) "[$host]:$port" else "$host:$port"
        fun toProxy() = Proxy(kind, InetSocketAddress.createUnresolved(host, port))
    }

    /**
     * Accepts host:port, socks5://host:port or http://host:port. Invalid input is not
     * silently interpreted as a direct route.
     */
    fun parse(raw: String?): Config? {
        var value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val lowered = value.lowercase()
        var kind = Proxy.Type.SOCKS
        when {
            lowered.startsWith("http://") -> { value = value.substring(7); kind = Proxy.Type.HTTP }
            lowered.startsWith("socks5://") -> value = value.substring(9)
            lowered.startsWith("socks://") -> value = value.substring(8)
            // Reject tunnel links and unsupported proxy schemes.
            lowered.contains("://") -> return null
        }
        val separator = value.lastIndexOf(':')
        if (separator <= 0 || separator == value.lastIndex) return null
        val host = value.substring(0, separator).trim().removeSurrounding("[", "]")
        val port = value.substring(separator + 1).trim().toIntOrNull() ?: return null
        if (host.isEmpty() || port !in 1..65535 || host.any { it.isWhitespace() || it in "/?#@" } ||
            (host.contains(':') && !value.startsWith("["))) return null
        return Config(host, port, kind)
    }

    /** "ss", "vmess", "vless", "trojan" and friends cannot be used as a proxy endpoint. */
    private fun isTunnelProtocol(scheme: String): Boolean =
        scheme in setOf("ss", "ssr", "vmess", "vless", "trojan", "hysteria", "hysteria2", "tuic", "wireguard", "wg")

    /** True when the input names a tunnel protocol that this app cannot terminate itself. */
    fun isTunnelLink(raw: String?): Boolean {
        val lowered = raw?.trim()?.lowercase().orEmpty()
        val scheme = lowered.substringBefore("://", "")
        return scheme.isNotEmpty() && lowered.contains("://") && isTunnelProtocol(scheme)
    }

    private const val MODE = "ytm_route_mode"
    fun mode(context: Context) = context.getSharedPreferences(PREFS, 0).getString(MODE, "auto") ?: "auto"
    fun mode(context: Context, value: String) { require(value in setOf("auto", "vpn", "manual")); context.getSharedPreferences(PREFS, 0).edit().putString(MODE, value).apply() }
    data class Route(val proxy: Config?, val key: String)
    /** Called on IO only, never as a timer. A TUN uses Android routing; no VPN core is embedded. */
    fun route(context: Context): Route {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        val network = manager?.activeNetwork
        val vpn = network?.let { manager.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) } == true
        val networkKey = network?.networkHandle ?: 0
        if (!cp.player.network.PlaybackNetwork.splitEnabled(context)) return Route(null, "system:$networkKey")
        val selection = mode(context)
        if (selection == "vpn" && !vpn) throw RecommendationUnavailable("当前应用未进入系统 VPN，请使用自动或本地代理")
        if (selection == "vpn" || selection == "auto" && vpn) return Route(null, "vpn:$networkKey")
        val saved = raw(context)
        if (selection == "manual") {
            val proxy = parse(saved) ?: throw RecommendationUnavailable("请设置有效的 SOCKS5 或 HTTP 代理地址")
            return Route(proxy, "manual:$proxy:$networkKey")
        }
        val preferred = parse(saved)
        val ports = (listOfNotNull(preferred?.takeIf { it.host == "127.0.0.1" || it.host == "localhost" }?.port) + listOf(10808, 1080, 7890, 2080, 10809, 7891)).distinct()
        for (port in ports) detect(port)?.let { return Route(it, "local:$it:$networkKey") }
        throw RecommendationUnavailable("未检测到 YTM 代理，请开启 VPN 或设置本地代理")
    }
    private fun detect(port: Int): Config? = try {
        java.net.Socket(Proxy.NO_PROXY).use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 120); socket.soTimeout = 120
            socket.getOutputStream().write(byteArrayOf(5, 1, 0)); socket.getOutputStream().flush()
            val reply = ByteArray(2); java.io.DataInputStream(socket.getInputStream()).readFully(reply)
            when {
                reply[0] == 5.toByte() && reply[1] == 0.toByte() -> Config("127.0.0.1", port)
                reply[0] == 'H'.code.toByte() && reply[1] == 'T'.code.toByte() -> Config("127.0.0.1", port, Proxy.Type.HTTP)
                else -> null
            }
        }
    } catch (_: java.io.IOException) { null }

    fun read(context: Context): Config? =
        parse(context.getSharedPreferences(PREFS, 0).getString(KEY, null))

    fun write(context: Context, raw: String) {
        context.getSharedPreferences(PREFS, 0).edit().putString(KEY, raw.trim()).apply()
    }

    fun raw(context: Context): String =
        context.getSharedPreferences(PREFS, 0).getString(KEY, "").orEmpty()
}
