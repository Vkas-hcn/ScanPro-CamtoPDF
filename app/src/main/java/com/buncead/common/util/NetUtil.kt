package com.buncead.common.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.bockcock.adjust.ConfigBean
import com.scanpro.cam.pdf.CamPdfApp

object NetUtil {


    fun isUseNet_noVpn(): Boolean {
        try {
            val context: Context = CamPdfApp.app
            val cm: ConnectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val network: Network = cm.activeNetwork ?: return false
            val capabilities: NetworkCapabilities = cm.getNetworkCapabilities(network) ?: return false

            val bool1: Boolean = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

            if (ConfigBean.op_vpn_limit == 0){
                val isVpn: Boolean = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                Util.iii { "NetUtil check() open vpn limit" }
                return bool1 && !isVpn // 有网络 并 没有vpn状态
            }else{
                Util.iii { "NetUtil check() close vpn limit" }
                return bool1
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }


}