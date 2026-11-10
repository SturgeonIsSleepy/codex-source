package cp.player.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.Proxy
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Routing is resolved once per HTTP call. No callbacks, polling or extra service. */
internal object PlaybackNetwork {
    class RouteUnavailable(cause: Throwable? = null) : IOException(
        "VPN 禁止音频直连，请在 VPN 中仅让 ePlayer 绕行，或关闭独立分流", cause)
    const val SPLIT = "split_routing"
    fun splitEnabled(context: Context) = context.getSharedPreferences("eplayer_ui", 0).getBoolean(SPLIT, true)
    private val system = OkHttpClient.Builder().build()
    private val unboundDirect = system.newBuilder().proxy(Proxy.NO_PROXY).build()
    private val direct = linkedMapOf<Network, OkHttpClient>()

    fun calls(context: Context) = Call.Factory { request ->
        val client = if (splitEnabled(context)) directClient(context) else system
        client.newCall(request)
    }

    @Synchronized private fun directClient(context: Context): OkHttpClient {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: throw IOException("无法获取直连网络")
        val networks = manager.allNetworks.mapNotNull { network ->
            manager.getNetworkCapabilities(network)?.let { network to it }
        }
        val active = manager.activeNetwork
        val activeCaps = active?.let(manager::getNetworkCapabilities)
        // An excluded app already has a physical default network. Binding again is unnecessary,
        // and some vendor builds reject that redundant bind even though normal sockets work.
        if (activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) == true &&
            !activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return unboundDirect
        val network = networks.filter { (_, caps) ->
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }.maxByOrNull { (network, caps) ->
            (if (network == active) 8 else 0) +
                (if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 4 else 0) +
                (if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) 2 else 0)
        }?.first ?: throw RouteUnavailable()
        direct[network]?.let { return it }
        try { network.socketFactory.createSocket().close() }
        catch (failure: IOException) {
            throw RouteUnavailable(failure)
        }
        val client = system.newBuilder().proxy(Proxy.NO_PROXY)
            .socketFactory(network.socketFactory).dns(Dns { network.getAllByName(it).toList() })
            .connectionPool(ConnectionPool(2, 2, TimeUnit.MINUTES)).build()
        direct[network] = client
        while (direct.size > 2) direct.remove(direct.keys.first())?.connectionPool?.evictAll()
        return client
    }
}
