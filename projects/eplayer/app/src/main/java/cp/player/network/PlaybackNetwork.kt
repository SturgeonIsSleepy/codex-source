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
    const val SPLIT = "split_routing"
    fun splitEnabled(context: Context) = context.getSharedPreferences("eplayer_ui", 0).getBoolean(SPLIT, true)
    private val system = OkHttpClient.Builder().build()
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
        val network = networks.filter { (_, caps) ->
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }.maxByOrNull { (network, caps) ->
            (if (network == active) 8 else 0) +
                (if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 4 else 0) +
                (if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) 2 else 0)
        }?.first ?: throw IOException("没有可用的直连网络，请检查 VPN 的绕过设置或关闭独立分流")
        direct[network]?.let { return it }
        val client = system.newBuilder().proxy(Proxy.NO_PROXY)
            .socketFactory(network.socketFactory).dns(Dns { network.getAllByName(it).toList() })
            .connectionPool(ConnectionPool(2, 2, TimeUnit.MINUTES)).build()
        direct[network] = client
        while (direct.size > 2) direct.remove(direct.keys.first())?.connectionPool?.evictAll()
        return client
    }
}
