package com.kimkinam.camerashit

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** 무선 디버깅은 Wi-Fi 가 연결되어 있어야 켜지므로, 시작 전에 확인한다. */
object Wifi {
    /** VPN 이 활성 네트워크여도 Wi-Fi 연결이 하나라도 있으면 연결된 것으로 본다. */
    fun isConnected(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager.allNetworks.any { network ->
            manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }
}
