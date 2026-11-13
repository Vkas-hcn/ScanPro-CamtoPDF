package com.mymy.mymy.ads//package com.mymy.mymy.ads
//
//import android.content.Context
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.ImageView
//import android.widget.TextView
//import com.buncead.common.pdfpro.R
//import com.google.android.gms.ads.nativead.NativeAd
//import com.google.android.gms.ads.nativead.NativeAdView
//
//object NatShow {
//
//
//
//    fun displayNativeAd(parent: ViewGroup, ad: NativeAd) {
//
//        val inflater: LayoutInflater = parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
//        val adView: NativeAdView = inflater.inflate(R.layout.nati_home_bt, parent,false) as NativeAdView
//
//        val nt_headline: TextView = adView.findViewById<TextView>(R.id.nt_headline)
//        val nt_body: TextView = adView.findViewById<TextView>(R.id.nt_body)
//        val nt_cta: TextView = adView.findViewById<TextView>(R.id.nt_cta)
////        val nt_media: MediaView = adView.findViewById<MediaView>(R.id.nt_media)
//        val nt_icon: ImageView = adView.findViewById<ImageView>(R.id.nt_icon)
//        val nt_advertiser: TextView = adView.findViewById<TextView>(R.id.nt_advertiser)
//
//        // 必须 set 到 NativeAdView
//        adView.headlineView = nt_headline
//        adView.bodyView = nt_body
//        adView.callToActionView = nt_cta
////        adView.mediaView = nt_media
//        adView.iconView = nt_icon
//        adView.advertiserView = nt_advertiser
//
//        // 数据填充
//        nt_headline.text = ad.headline
//        nt_body.text = ad.body
//        nt_cta.text = ad.callToAction
////        nt_media.mediaContent = ad.mediaContent
//
//        ad.icon?.let {
//            nt_icon.setImageDrawable(it.drawable)
//            nt_icon.visibility = View.VISIBLE
//        } ?: run {
//            nt_icon.visibility = View.GONE
//        }
//
//        ad.advertiser?.let {
//            nt_advertiser.text = it
//            nt_advertiser.visibility = View.VISIBLE
//        } ?: run {
//            nt_advertiser.visibility = View.GONE
//        }
//
//        adView.setNativeAd(ad)
//        parent.removeAllViews()
//
//        parent.addView(adView)
//
//        parent.visibility = View.VISIBLE
//    }
//
//}