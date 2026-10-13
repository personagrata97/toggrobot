package com.toggrobot

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ön planda calisan servis: her N saniyede bir siteyi kontrol eder. */
class CheckerService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var cfg: Config
    private var runner: BotRunner? = null
    private var webView: WebView? = null
    private var overlayAttached = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var runStartedAt = 0L
    private var failStreak = 0
    private val tick = Runnable { runOnce() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        cfg = Config(this)
        Notifier.createChannels(this)
        goForeground("Baslatiliyor...")
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "toggrobot:checker").apply {
                setReferenceCounted(false)
                acquire()
            }
        createWebView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cfg.running = false
            stopSelf()
            return START_NOT_STICKY
        }
        cfg.running = true
        main.removeCallbacks(tick)
        main.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(tick)
        runner?.cancel()
        webView?.let { wv ->
            if (overlayAttached) runCatching { getSystemService(WindowManager::class.java).removeView(wv) }
            wv.destroy()
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        Store.append(this, "Servis durdu")
        super.onDestroy()
    }

    private fun goForeground(text: String) {
        val n = Notifier.serviceNotification(this, text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifier.ID_SERVICE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.ID_SERVICE, n)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(Notifier.ID_SERVICE, Notifier.serviceNotification(this, text))
    }

    private fun createWebView() {
        val wv = WebView(this)
        val dm = resources.displayMetrics
        val w = if (cfg.desktopMode) (1366 * dm.density).toInt() else dm.widthPixels
        val h = if (cfg.desktopMode) (900 * dm.density).toInt() else dm.heightPixels
        if (cfg.overlayMode && Settings.canDrawOverlays(this)) {
            // Gorunmez (alpha=0) ve dokunulamaz pencere: WebView "gercek" ekranda gibi davranir
            val lp = WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.alpha = 0f
            runCatching {
                getSystemService(WindowManager::class.java).addView(wv, lp)
                overlayAttached = true
            }
        }
        if (!overlayAttached) {
            wv.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            )
            wv.layout(0, 0, w, h)
        }
        webView = wv
        runner = BotRunner(this, wv) { msg -> Store.append(this, "  · $msg") }
    }

    private fun runOnce() {
        val r = runner ?: return
        cfg = Config(this)
        runStartedAt = System.currentTimeMillis()
        updateNotification("Kontrol ediliyor... (${Store.time()})")
        r.start(cfg, debug = false) { res -> onResult(res) }
    }

    private fun onResult(res: JSONObject) {
        val summary = Evaluator.handle(this, cfg, res)
        if (res.has("fatal") || !res.optBoolean("variantFound")) failStreak++ else failStreak = 0
        if (failStreak == 15) {
            Notifier.alert(
                this, Notifier.ID_PROBLEM, "Togg Robot siteyi okuyamiyor",
                "15 denemedir versiyon bulunamadi. Uygulamada 'Simdi test et' ile kontrol edin.", cfg.url
            )
        }
        updateNotification("${Store.time()} — $summary")
        val elapsed = System.currentTimeMillis() - runStartedAt
        val delay = (cfg.intervalSec * 1000L - elapsed).coerceAtLeast(5_000L)
        main.postDelayed(tick, delay)
    }

    companion object {
        const val ACTION_STOP = "com.toggrobot.STOP"

        fun start(ctx: Context) {
            Config(ctx).running = true
            ctx.startForegroundService(Intent(ctx, CheckerService::class.java))
        }

        fun stop(ctx: Context) {
            Config(ctx).running = false
            ctx.stopService(Intent(ctx, CheckerService::class.java))
        }
    }
}

/** Sonucu yorumlar, gerekirse bildirim gonderir, ozet metni dondurur. */
object Evaluator {
    fun fmt(v: Long): String = String.format(Locale("tr", "TR"), "%,d TL", v)

    fun handle(ctx: Context, cfg: Config, res: JSONObject, notify: Boolean = true): String {
        val p = cfg.prefs
        val now = System.currentTimeMillis()
        val summary: String
        if (res.has("fatal")) {
            summary = "HATA: " + res.optString("fatal")
        } else {
            val sel = res.optBoolean("variantSelectable")
            val vPrice = res.optLong("variantPrice", 0)
            val fPrice = res.optLong("finalPrice", 0)
            val fullOk = res.optBoolean("fullOk")
            val basicHit = cfg.basicAlarm && sel && vPrice in 1L until cfg.basicThreshold.toLong()
            val fullHit = fullOk && fPrice in 1L until cfg.fullThreshold.toLong()

            val parts = mutableListOf<String>()
            parts += when {
                !res.optBoolean("variantFound") -> "Versiyon bulunamadi"
                !sel -> "Versiyon SECILEMIYOR"
                else -> "Versiyon secilebilir: ${if (vPrice > 0) fmt(vPrice) else "fiyat okunamadi"}"
            }
            if (cfg.fullMode && sel) {
                val missing = res.optJSONArray("missing")?.join(", ") ?: ""
                val errors = listOf(res.optJSONArray("errors")?.join(" | ") ?: "", res.optJSONArray("violations")?.join(" | ") ?: "")
                    .filter { it.isNotEmpty() }.joinToString(" | ")
                val priceText = if (fPrice > 0) fmt(fPrice) else "fiyat yok"
                parts += if (fullOk) "Tam konfig: $priceText"
                else "Tam konfig olmadi ($missing${if (errors.isNotEmpty()) " hata: $errors" else ""}) son fiyat: $priceText"
            }
            summary = parts.joinToString(" • ")

            if (notify) {
                maybeAlert(ctx, cfg, "lastFull", fullHit, now, Notifier.ID_FULL,
                    "🎉 TOGG: Istedigin konfigurasyon ${fmt(fPrice)}!",
                    "T10F V2 RWD Uzun Menzil + Kis + Akilli Destek + Kula + Siyah vegan deri + Panoramik tavan secilebiliyor (Meridian / cift renk yok). Son fiyat ${fmt(fPrice)} (< ${fmt(cfg.fullThreshold.toLong())}). Dokun: siteyi ac.")
                maybeAlert(ctx, cfg, "lastBasic", basicHit && !fullHit, now, Notifier.ID_BASIC,
                    "🚗 TOGG T10F V2 RWD Uzun Menzil: ${fmt(vPrice)}",
                    "Versiyon secilebiliyor ve fiyat ${fmt(vPrice)} (< ${fmt(cfg.basicThreshold.toLong())}). Dokun: siteyi ac.")
            }
        }
        p.edit().putString("lastResult", res.apply { remove("pageText") }.toString())
            .putString("lastSummary", "${Store.time()} — $summary").apply()
        Store.append(ctx, "SONUC: $summary")
        return summary
    }

    private fun maybeAlert(ctx: Context, cfg: Config, key: String, hit: Boolean, now: Long, id: Int, title: String, text: String) {
        val p = cfg.prefs
        if (!hit) {
            p.edit().putLong(key, 0).apply()
            return
        }
        val last = p.getLong(key, 0)
        if (last == 0L || now - last >= cfg.repeatMin * 60_000L) {
            Notifier.alert(ctx, id, title, text, cfg.url)
            p.edit().putLong(key, now).apply()
            Store.append(ctx, "BILDIRIM: $title")
        }
    }

    private fun org.json.JSONArray.join(sep: String): String =
        (0 until length()).joinToString(sep) { optString(it) }
}

/** Basit dosya log'u (son ~400 satir). */
object Store {
    private const val MAX = 400
    private val df = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun time(): String = df.format(Date())

    @Synchronized
    fun append(ctx: Context, line: String) {
        val f = File(ctx.filesDir, "log.txt")
        val lines = if (f.exists()) f.readLines().toMutableList() else mutableListOf()
        lines += "${time()} $line"
        val keep = if (lines.size > MAX) lines.takeLast(MAX) else lines
        f.writeText(keep.joinToString("\n"))
    }

    fun read(ctx: Context, last: Int = 120): String {
        val f = File(ctx.filesDir, "log.txt")
        if (!f.exists()) return ""
        return f.readLines().takeLast(last).reversed().joinToString("\n")
    }

    fun clear(ctx: Context) {
        File(ctx.filesDir, "log.txt").delete()
    }
}
