package com.scanpro.cam.pdf.ads

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Warm Chromium once via about:blank, then destroy.
 * Keeping a WebView alive can block AdMob from obtaining a JavascriptEngine for App Open ads.
 */
object SpWebWarmup {

    private const val TAG = "SpAdsDiag"

    @Volatile
    private var warmed = false

    @SuppressLint("SetJavaScriptEnabled")
    fun ensureWarmupStarted(context: Context) {
        if (warmed) return
        val app = context.applicationContext
        val run = Runnable {
            if (warmed) return@Runnable
            runCatching {
                CookieManager.getInstance()
                val webView = WebView(app)
                webView.settings.javaScriptEnabled = true
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.i(TAG, "WebView warm-up page finished — destroying")
                        runCatching { view?.destroy() }
                        warmed = true
                    }
                }
                webView.loadUrl("about:blank")
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!warmed) {
                        runCatching { webView.destroy() }
                        warmed = true
                        Log.i(TAG, "WebView warm-up fallback destroy")
                    }
                }, 1_500L)
                Log.i(TAG, "WebView warm-up started")
            }.onFailure {
                Log.w(TAG, "WebView warm-up failed", it)
                warmed = true
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            run.run()
        } else {
            Handler(Looper.getMainLooper()).post(run)
        }
    }

    suspend fun awaitWarmupReady(context: Context, timeoutMs: Long = 2_000L) {
        ensureWarmupStarted(context)
        if (warmed) return
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val start = System.currentTimeMillis()
            fun poll() {
                if (!cont.isActive) return
                if (warmed || System.currentTimeMillis() - start > timeoutMs) {
                    Log.i(TAG, "WebView awaitWarmupReady warmed=$warmed")
                    cont.resume(Unit)
                } else {
                    handler.postDelayed({ poll() }, 50L)
                }
            }
            handler.post { poll() }
        }
    }
}
