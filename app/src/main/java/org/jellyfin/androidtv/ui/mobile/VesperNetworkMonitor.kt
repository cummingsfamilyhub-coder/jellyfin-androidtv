package org.jellyfin.androidtv.ui.mobile

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps Sendspin reconnects aligned with Android's active network. */
internal class VesperNetworkMonitor(context: Context) : Closeable {
    private val connectivity = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _online = MutableStateFlow(hasUsableNetwork())

    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _online.value = true
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _online.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        override fun onLost(network: Network) {
            _online.value = hasUsableNetwork()
        }
    }

    init {
        connectivity.registerDefaultNetworkCallback(callback)
    }

    private fun hasUsableNetwork(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun close() {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }
}
