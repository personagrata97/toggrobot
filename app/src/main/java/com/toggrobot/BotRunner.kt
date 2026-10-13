package com.toggrobot

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/**
 * Bir WebView uzerinde tek bir kontrol turunu yurutur:
 * sayfayi temiz acar, bot.js'i enjekte eder, sonucu callback ile dondurur.
 */
@SuppressLint("SetJavaScriptEnabled")
class BotRunner(
    private val context: Context,
    val webView: WebView,
    private val onLog: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val botJs: String by lazy {
        context.assets.open("bot.js").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private var runId: String? = null
    private var runCfg: JSONObject? = null
    private var callback: ((JSONObject) -> Unit)? = null
    private val timeout = Runnable { finish(JSONObject().put("fatal", "Zaman asimi (sayfa yuklenemedi ya da bot takildi)")) }

    init {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        @Suppress("DEPRECATION")
        s.databaseEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.mediaPlaybackRequiresUserGesture = true
        s.offscreenPreRaster = true
        webView.addJavascriptInterface(Bridge(), "ToggBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (runId != null) onLog("Sayfa aciliyor: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                inject()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true && runId != null) {
                    finish(JSONObject().put("fatal", "Sayfa hatasi: ${error?.description}"))
                }
            }
        }
    }

    fun applyConfig(cfg: Config) {
        val s = webView.settings
        s.blockNetworkImage = cfg.blockImages
        s.userAgentString = if (cfg.desktopMode) DESKTOP_UA else null
    }

    val isRunning get() = runId != null

    fun start(cfg: Config, debug: Boolean, cb: (JSONObject) -> Unit) {
        cancel()
        applyConfig(cfg)
        val id = "r" + System.currentTimeMillis()
        runId = id
        runCfg = cfg.botConfig(id, debug)
        callback = cb
        // Her turda temiz oturum: eski secimler hatirlanmasin
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        main.postDelayed(timeout, 100_000)
        webView.loadUrl(cfg.url)
    }

    fun cancel() {
        main.removeCallbacks(timeout)
        runId = null
        callback = null
        webView.stopLoading()
    }

    private fun inject() {
        val cfg = runCfg ?: return
        if (runId == null) return
        // SPA'nin kendini kurmasi icin kisa bekle
        main.postDelayed({
            if (runId == cfg.optString("runId")) {
                webView.evaluateJavascript("$botJs\n;TOGGBOT.start($cfg);", null)
            }
        }, 1200)
    }

    private fun finish(res: JSONObject) {
        val cb = callback ?: return
        main.removeCallbacks(timeout)
        runId = null
        callback = null
        cb(res)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun report(json: String) {
            main.post {
                val obj = try { JSONObject(json) } catch (e: Exception) { JSONObject().put("fatal", "Gecersiz sonuc: $e") }
                if (obj.optString("runId") == runId) finish(obj)
            }
        }

        @JavascriptInterface
        fun log(msg: String) {
            main.post { if (runId != null) onLog(msg) }
        }
    }

    companion object {
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
    }
}
