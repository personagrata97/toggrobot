package com.toggrobot

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback

/**
 * Konfiguratoru uygulamanin icinde, BILGISAYAR gorunumunde acar.
 * Site mobil engel ekranini sadece EKRAN GENISLIGINE gore gosteriyor (test: 800 px engelli,
 * 1024 px ve ustu acik; tarayici kimligi ve dokunmatik ekran fark etmiyor). Bu yuzden sayfa
 * WebView'in kendisini [DESKTOP_WIDTH] dp genisliginde kurup ekrana sigacak sekilde kucultuyoruz: sayfa
 * gercekten masaustu genisliginde bir pencere olcer (meta etiketiyle "genis taklidi" yapmak gercek sitede
 * ise yaramadi). Dokunma ve parmakla yakinlastirma olcekli gorunumde de calisir.
 * Telefon tarayicisindaki "mobil uygulamaya yonlendirme" burada olmaz: sitenin baska
 * uygulamaya gecirme denemeleri (intent://, market:// vb.) engellenir, sayfa burada kalir.
 */
class WebActivity : BaseActivity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = screen(
            "Togg Konfigüratör", back = true,
            Triple(R.drawable.ic_refresh, "Yenile") { web.reload() },
        )
        // screen() kaydirma kutusu verir; WebView kendi kaydirdigi icin ust kutuyu dogrudan kullan
        val scroll = content.parent as View
        val root = scroll.parent as LinearLayout
        root.removeView(scroll)

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }
        root.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)))

        // Kapsayici ekranin kalanini doldurur; WebView icinde masaustu genisliginde durup olceklenir
        val holder = android.widget.FrameLayout(this).apply { clipChildren = true }
        root.addView(holder, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        web = WebView(this)
        val desktopPx = dp(DESKTOP_WIDTH)
        holder.addView(web, android.widget.FrameLayout.LayoutParams(desktopPx, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        holder.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
            val w = r - l
            val h = b - t
            if (w <= 0 || h <= 0) return@addOnLayoutChangeListener
            val scale = w.toFloat() / desktopPx
            web.pivotX = 0f
            web.pivotY = 0f
            web.scaleX = scale
            web.scaleY = scale
            val needH = (h / scale).toInt()
            val lp = web.layoutParams
            if (lp.height != needH) {
                lp.height = needH
                v.post { web.layoutParams = lp }
            }
        }
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = Api.DESKTOP_UA
            useWideViewPort = true
            loadWithOverviewMode = false
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return true
                // Sadece web sayfalari: uygulamaya yonlendirme denemelerini yok say
                return scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                // Kayit icin: sayfanin olctugu genislik ve engel ekrani var mi
                view?.evaluateJavascript(
                    "(function(){return window.innerWidth+'|'+/uygulamamızdan|dijital mağazalardan/i.test(document.body.innerText)})()"
                ) { r -> android.util.Log.i("ToggWeb", "genislik|engel=$r") }
            }
        }
        web.loadUrl(intent.getStringExtra("url") ?: Api.SITE)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val DESKTOP_WIDTH = 1100

        fun intent(ctx: android.content.Context, url: String = Api.SITE) =
            Intent(ctx, WebActivity::class.java).putExtra("url", url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        @Suppress("unused")
        fun external(url: String = Api.SITE) = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }
}
