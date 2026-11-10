package cp.player.app

import android.content.Context
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * SOCKS5 endpoint of a local v2ray/Clash client, used only for YouTube Music metadata requests.
 * The native source and audio playback keep going direct, so a slow route cannot affect listening.
 *
 * v2ray is the only supported transport. vless://, vmess://, ss:// and trojan:// links describe
 * encrypted tunnels rather than proxies: they cannot be dialled directly and must be terminated by
 * v2rayNG first, which then exposes the socks5:// port this class points at.
 */
internal object YtmProxy {
    private const val PREFS = "eplayer_ui"
    private const val KEY = "ytm_proxy"

    data class Config(val host: String, val port: Int) {
        fun address() = if (host.contains(':')) "[$host]:$port" else "$host:$port"
        fun toProxy() = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(host, port))
    }

    /**
     * Accepts "host:port" or "socks5://host:port" and returns null for anything else, so a pasted
     * tunnel link or a typo becomes "direct connection" instead of a broken route.
     */
    fun parse(raw: String?): Config? {
        var value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val lowered = value.lowercase()
        when {
            lowered.startsWith("socks5://") -> value = value.substring(9)
            lowered.startsWith("socks://") -> value = value.substring(8)
            // Reject every other scheme, including http and the tunnel protocols.
            lowered.contains("://") -> return null
        }
        val separator = value.lastIndexOf(':')
        if (separator <= 0 || separator == value.lastIndex) return null
        val host = value.substring(0, separator).trim().removeSurrounding("[", "]")
        val port = value.substring(separator + 1).trim().toIntOrNull() ?: return null
        if (host.isEmpty() || port !in 1..65535 || host.any { it.isWhitespace() || it in "/?#@" } ||
            (host.contains(':') && !value.startsWith("["))) return null
        return Config(host, port)
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

    fun read(context: Context): Config? =
        parse(context.getSharedPreferences(PREFS, 0).getString(KEY, null))

    fun write(context: Context, raw: String) {
        context.getSharedPreferences(PREFS, 0).edit().putString(KEY, raw.trim()).apply()
    }

    fun raw(context: Context): String =
        context.getSharedPreferences(PREFS, 0).getString(KEY, "").orEmpty()
}
