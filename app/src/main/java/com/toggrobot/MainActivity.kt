package com.toggrobot

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var cfg: Config
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var testResult: TextView
    private lateinit var testWeb: WebView
    private var testRunner: BotRunner? = null
    private var lastPageText = ""
    private val ui = Handler(Looper.getMainLooper())
    private val fields = mutableListOf<() -> Unit>() // kaydetme fonksiyonlari
    private val refresher = object : Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this, 2000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cfg = Config(this)
        Notifier.createChannels(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        ui.post(refresher)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(refresher)
    }

    override fun onDestroy() {
        testRunner?.cancel()
        testWeb.destroy()
        super.onDestroy()
    }

    @SuppressLint("SetTextI18n")
    private fun buildUi(): View {
        fields.clear()
        if (::testWeb.isInitialized) {
            testRunner?.cancel()
            testWeb.destroy()
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(30))
        }

        fun header(t: String) = root.addView(TextView(this).apply {
            text = t; textSize = 17f; setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(4))
        })

        fun row(vararg views: View) = root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            views.forEach { addView(it, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) }
        })

        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false; setOnClickListener { onClick() }
        }

        status = TextView(this).apply { textSize = 15f; setPadding(0, dp(4), 0, dp(4)) }
        root.addView(status)

        row(
            button("▶ Başlat (her dakika)") { saveAll(); CheckerService.start(this); toast("Başlatıldı"); refresh() },
            button("■ Durdur") { CheckerService.stop(this); toast("Durduruldu"); refresh() },
        )
        row(
            button("Pil kısıtlamasını kaldır") { askBatteryWhitelist() },
            button("Bildirim testi") {
                Notifier.alert(this, 999, "Test bildirimi", "Bildirimler çalışıyor 👍", cfg.url)
            },
        )

        header("Canlı test")
        root.addView(TextView(this).apply {
            text = "Botu aşağıdaki pencerede bir kez çalıştırır, ne yaptığını izleyebilirsin. " +
                "Bir şey ters giderse 'Sayfa metnini kopyala' ile metni kopyalayıp geliştiriciye gönder."
            textSize = 13f
        })
        row(
            button("Şimdi test et") { runTest() },
            button("Sayfa metnini kopyala") { copyPageText() },
        )
        testResult = TextView(this).apply { textSize = 13f; setTextIsSelectable(true) }
        root.addView(testResult)
        testWeb = WebView(this)
        root.addView(testWeb, LinearLayout.LayoutParams(MATCH_PARENT, dp(520)))

        header("Hedefler ve eşikler")
        intField(root, "Kontrol aralığı (saniye)", { cfg.intervalSec }) { cfg.intervalSec = it.coerceAtLeast(30) }
        intField(root, "Basit alarm: versiyon fiyatı şundan düşükse (TL)", { cfg.basicThreshold }) { cfg.basicThreshold = it }
        intField(root, "Tam alarm: son fiyat şundan düşükse (TL)", { cfg.fullThreshold }) { cfg.fullThreshold = it }
        intField(root, "Alarm tekrar aralığı (dakika)", { cfg.repeatMin }) { cfg.repeatMin = it.coerceAtLeast(1) }
        checkField(root, "Tam konfigürasyonu dene (paketler, renk, koltuk, tavan)", { cfg.fullMode }) { cfg.fullMode = it }

        header("Anahtar kelimeler")
        root.addView(TextView(this).apply {
            text = "Virgül = VE, / = VEYA. Türkçe karakter fark etmez (ş=s, ı=i...)."
            textSize = 12f
        })
        textField(root, "Model", { cfg.kwModel }) { cfg.kwModel = it }
        textField(root, "Versiyon", { cfg.kwVariant }) { cfg.kwVariant = it }
        textField(root, "Kış paketi", { cfg.kwKis }) { cfg.kwKis = it }
        textField(root, "Akıllı destek paketi", { cfg.kwAkilli }) { cfg.kwAkilli = it }
        textField(root, "Dış renk", { cfg.kwRenk }) { cfg.kwRenk = it }
        textField(root, "Koltuk", { cfg.kwKoltuk }) { cfg.kwKoltuk = it }
        textField(root, "Donanım (tavan)", { cfg.kwTavan }) { cfg.kwTavan = it }
        textField(root, "İleri butonu kelimeleri", { cfg.nextWords }) { cfg.nextWords = it }
        textField(root, "Adım sekmeleri (yedek)", { cfg.tabWords }) { cfg.tabWords = it }

        header("Gelişmiş")
        textField(root, "Site adresi", { cfg.url }) { cfg.url = it.trim() }
        intField(root, "Tıklamalar arası bekleme (ms)", { cfg.stepDelayMs }) { cfg.stepDelayMs = it.coerceIn(300, 10000) }
        checkField(root, "Masaüstü görünümü kullan", { cfg.desktopMode }) { cfg.desktopMode = it }
        checkField(root, "Resimleri yükleme (veri tasarrufu)", { cfg.blockImages }) { cfg.blockImages = it }
        checkField(root, "Görünmez pencere modu (daha güvenilir, izin ister)", { cfg.overlayMode }) {
            cfg.overlayMode = it
            if (it && !Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        row(
            button("Kaydet") { saveAll(); toast("Kaydedildi (çalışıyorsa yeniden başlat)") },
            button("Varsayılanlar") {
                val running = cfg.running
                cfg.prefs.edit().clear().apply(); cfg.running = running
                setContentView(buildUi()); toast("Varsayılanlar yüklendi")
            },
        )

        header("Kayıt (en yeni üstte)")
        row(button("Kaydı temizle") { Store.clear(this); refresh() })
        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 11f; setTextIsSelectable(true)
        }
        root.addView(logView)

        testRunner = BotRunner(this, testWeb) { msg ->
            testResult.text = "${testResult.text}\n· $msg"
        }
        return ScrollView(this).apply { addView(root) }
    }

    private fun label(root: LinearLayout, t: String) = root.addView(TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.DKGRAY); setPadding(0, dp(6), 0, 0)
    })

    private fun intField(root: LinearLayout, t: String, get: () -> Int, set: (Int) -> Unit) {
        label(root, t)
        val e = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(get().toString()) }
        root.addView(e)
        fields += { e.text.toString().trim().toIntOrNull()?.let(set) }
    }

    private fun textField(root: LinearLayout, t: String, get: () -> String, set: (String) -> Unit) {
        label(root, t)
        val e = EditText(this).apply { setSingleLine(); setText(get()) }
        root.addView(e)
        fields += { set(e.text.toString()) }
    }

    private fun checkField(root: LinearLayout, t: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        val c = CheckBox(this).apply { text = t; isChecked = get() }
        c.setOnCheckedChangeListener { _, v -> set(v) }
        root.addView(c)
    }

    private fun saveAll() {
        fields.forEach { it() }
    }

    @SuppressLint("SetTextI18n")
    private fun runTest() {
        saveAll()
        val r = testRunner ?: return
        testResult.text = "Test başladı ${Store.time()}..."
        lastPageText = ""
        r.start(Config(this), debug = true) { res ->
            lastPageText = res.optString("pageText")
            val summary = Evaluator.handle(this, cfg, res, notify = false)
            val sb = StringBuilder()
            sb.append("\n\n=== SONUÇ ===\n").append(summary)
            sb.append("\nSeçilenler: ").append(res.optJSONArray("done")?.toString() ?: "-")
            sb.append("\nSayfadaki fiyatlar: ").append(res.optJSONArray("allPrices")?.toString() ?: "-")
            res.optString("finalPriceText").takeIf { it.isNotEmpty() }?.let { sb.append("\nToplam okunan yer: ").append(it) }
            testResult.text = "${testResult.text}$sb"
            refresh()
        }
    }

    private fun copyPageText() {
        if (lastPageText.isEmpty()) {
            toast("Önce 'Şimdi test et' çalıştır")
            return
        }
        val text = "SONUC:\n${cfg.prefs.getString("lastResult", "")}\n\nSAYFA:\n$lastPageText"
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("togg", text))
        toast("Kopyalandı")
    }

    @SuppressLint("BatteryLife")
    private fun askBatteryWhitelist() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            toast("Zaten kısıtlama yok 👍")
            return
        }
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }

    @SuppressLint("SetTextI18n")
    private fun refresh() {
        val running = cfg.running
        val last = cfg.prefs.getString("lastSummary", "henüz kontrol yok")
        val pm = getSystemService(PowerManager::class.java)
        val battery = if (pm.isIgnoringBatteryOptimizations(packageName)) "" else "\n⚠ Pil optimizasyonu açık: arka planda durabilir."
        status.text = (if (running) "🟢 ÇALIŞIYOR" else "🔴 DURDU") + "\nSon: $last$battery"
        logView.text = Store.read(this)
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
