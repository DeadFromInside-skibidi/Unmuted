package com.unmuted.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real-time reactive network connectivity monitor using [ConnectivityManager.NetworkCallback]
 * with [NetworkCapabilities.NET_CAPABILITY_INTERNET] and [NetworkCapabilities.NET_CAPABILITY_VALIDATED].
 * Exposes a stable [isOnline] StateFlow for offline-graceful banners and caching UI.
 */
class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkInitialConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val activeValidNetworks = mutableSetOf<Network>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            evaluateNetwork(network)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            synchronized(activeValidNetworks) {
                if (hasInternet && isValidated) {
                    activeValidNetworks.add(network)
                } else {
                    activeValidNetworks.remove(network)
                }
                _isOnline.value = activeValidNetworks.isNotEmpty()
            }
        }

        override fun onLost(network: Network) {
            synchronized(activeValidNetworks) {
                activeValidNetworks.remove(network)
                _isOnline.value = activeValidNetworks.isNotEmpty()
            }
        }

        private fun evaluateNetwork(network: Network) {
            val caps = connectivityManager?.getNetworkCapabilities(network) ?: return
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            synchronized(activeValidNetworks) {
                if (hasInternet && isValidated) {
                    activeValidNetworks.add(network)
                } else {
                    activeValidNetworks.remove(network)
                }
                _isOnline.value = activeValidNetworks.isNotEmpty()
            }
        }
    }

    private var registered = false

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
            registered = true
        } catch (e: Exception) {
            Log.w("NetworkMonitor", "Could not register NetworkCallback, falling back to static check", e)
            _isOnline.value = checkInitialConnectivity()
        }
    }

    private fun checkInitialConnectivity(): Boolean {
        val cm = connectivityManager ?: return true
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun release() {
        if (registered) {
            try {
                connectivityManager?.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) {}
            registered = false
        }
    }
}
