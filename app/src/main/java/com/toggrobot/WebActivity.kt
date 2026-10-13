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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Konfiguratoru uygulamanin icinde, BILGISAYAR gorunumunde acar.
 * Site mobil engel ekranini sadece EKRAN GENISLIGINE gore gosteriyor (test: 800 px engelli,
 * 1024 px ve ustu acik; tarayici kimligi ve dokunmatik ekran fark etmiyor). Bu yuzden sayfa
 * daha yuklenirken genisligi [DESKTOP_WIDTH] px'e sabitliyoruz; kullanici parmakla yakinlastirabilir.
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

        web = WebView(this)
        root.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = Api.DESKTOP_UA
            useWideViewPort = true
            loadWithOverviewMode = true
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
                // Yedek: eski WebView surumlerinde de genisligi sabitle ve sayfaya yeniden olcmesini soyle
                view?.evaluateJavascript("$VIEWPORT_JS;window.dispatchEvent(new Event('resize'));", null)
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, VIEWPORT_JS, setOf("https://configurator.togg.com.tr"))
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

        /** Sayfanin viewport genisligini masaustu genisligine sabitler (meta etiketi eklenir eklenmez). */
        private val VIEWPORT_JS = """
            (function(){
              var W = $DESKTOP_WIDTH, want = 'width=' + W;
              function fix(){
                var m = document.querySelector('meta[name="viewport"]');
                if (m) { if (m.getAttribute('content') !== want) m.setAttribute('content', want); }
                else if (document.head) { m = document.createElement('meta'); m.name = 'viewport'; m.content = want; document.head.appendChild(m); }
              }
              fix();
              if (!window.__toggVp) {
                window.__toggVp = true;
                var mo = new MutationObserver(fix);
                mo.observe(document, { childList: true, subtree: true });
                setTimeout(function(){ mo.disconnect(); }, 15000);
              }
            })()
        """.trimIndent()

        fun intent(ctx: android.content.Context, url: String = Api.SITE) =
            Intent(ctx, WebActivity::class.java).putExtra("url", url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        @Suppress("unused")
        fun external(url: String = Api.SITE) = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }
}
