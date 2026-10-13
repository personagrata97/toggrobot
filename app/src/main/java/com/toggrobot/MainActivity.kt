package com.toggrobot

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : BaseActivity() {

    private lateinit var s: Settings
    private lateinit var content: LinearLayout
    private val ui = Handler(Looper.getMainLooper())
    private var setupBusy = false
    private var checking = false
    private val refresher = object : Runnable {
        override fun run() {
            render()
            ui.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = Settings(this)
        Notifier.createChannels(this)
        content = screen(
            "Togg Robot", back = false,
            Triple(R.drawable.ic_refresh, "Şimdi kontrol et") { checkNow() },
            Triple(R.drawable.ic_settings, "Ayarlar") { startActivity(Intent(this, SettingsActivity::class.java)) },
        )
        addFab("Yeni robot") { startActivity(Intent(this, RobotEditActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        ui.post(refresher)
        ui.postDelayed({ setupFlow() }, 600)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(refresher)
    }

    private fun render() {
        content.removeAllViews()
        heroCard()
        permissionBanner()

        content.section("Robotlarım")
        val robots = s.robots
        if (robots.isEmpty()) {
            content.card(pad = 20).tv("Henüz robot yok. Sağ alttaki 'Yeni robot' ile istediğin aracı tanımla.", 15f, Palette.MUTED)
        }
        robots.forEach { robotCard(it) }

        content.section("Sitede şu an", "Tümü") { startActivity(Intent(this, SiteActivity::class.java)) }
        siteSummary()
    }

    // ---------- Durum karti ----------

    @SuppressLint("SetTextI18n")
    private fun heroCard() {
        val running = s.running
        val c = content.card(pad = 22, color = Palette.NAVY)
        (c.parent as View).background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Palette.NAVY, Palette.NAVY2))
            .apply { cornerRadius = dpf(26) }

        val top = c.hrow()
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.tv(if (running) "TAKİP AÇIK" else "TAKİP KAPALI", 12f, Color.parseColor("#8FB3D9")).apply {
            letterSpacing = 0.12f; typeface = Palette.MEDIUM
        }
        left.tv(if (running) "Senin için bakıyorum" else "Robot duraklatıldı", 22f, Color.WHITE, bold = true, top = 4)
        top.addView(left, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        top.addView(MaterialSwitch(this).apply {
            // Koyu kart uzerinde gorunur olsun: acikken turkuaz
            trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Palette.ACCENT, Color.parseColor("#3A4A63"))
            )
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            trackDecorationTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            isChecked = running
            setOnCheckedChangeListener { _, on ->
                if (on) { Scheduler.start(this@MainActivity); checkNow() } else Scheduler.stop(this@MainActivity)
                ui.post { render() }
            }
        })

        // Bilgi kutucuklari
        val last = s.prefs.getLong("lastCheck", 0)
        val night = Night.isNight(s, System.currentTimeMillis())
        val stats = c.hrow(top = 18)
        fun stat(label: String, value: String) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = GradientDrawable().apply { cornerRadius = dpf(16); setColor(Color.parseColor("#1AFFFFFF")) }
            }
            box.tv(label, 12f, Color.parseColor("#9FB6D1"))
            box.tv(value, 15f, Color.WHITE).typeface = Palette.MEDIUM
            stats.addView(box, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        }
        stat("Son kontrol", if (last > 0) Log.time(last).substringAfter(' ').substring(0, 5) else "—")
        stat("Sıklık", if (night) "${s.nightIntervalH} saatte bir" else "${s.intervalMin} dakikada bir")
        stat("Gece", if (s.nightMode) "%02d–%02d".format(s.nightStart, s.nightEnd) else "Kapalı")

        val fails = s.prefs.getInt("fails", 0)
        if (fails > 0) {
            c.addView(badge("Siteye ulaşılamadı (${fails}x), tekrar denenecek", Palette.AMBER, Palette.AMBER_BG),
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(14) })
        }
    }

    private fun permissionBanner() {
        val missing = buildList {
            if (needsNotif()) add("bildirim")
            if (needsBattery()) add("pil kısıtlaması")
            if (needsExact()) add("tam zamanlı alarm")
        }
        if (missing.isEmpty()) return
        val c = content.card(color = Palette.AMBER_BG, stroke = false)
        c.tv("İzin eksik: ${missing.joinToString(", ")}", 14f, Palette.AMBER, bold = true)
        c.tv("Eksik izin olursa robot arka planda duraklayabilir ya da bildirim gelmeyebilir.", 13f, Palette.AMBER)
        c.fullWidth(outlinedButton("İzinleri ver") { recheckPermissions() })
    }

    // ---------- Robot kartlari ----------

    @SuppressLint("SetTextI18n")
    private fun robotCard(r: Robot) {
        val c = content.card(pad = 18) { startActivity(Intent(this, RobotEditActivity::class.java).putExtra("id", r.id)) }
        val head = c.hrow()
        // Bas harf avatari
        head.addView(TextView(this).apply {
            text = r.name.trim().firstOrNull()?.toString()?.uppercase(java.util.Locale("tr", "TR")) ?: "R"
            textSize = 18f; typeface = Palette.BOLD; gravity = Gravity.CENTER
            setTextColor(Palette.ACCENT)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Palette.ACCENT_SOFT) }
        }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(14) })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.tv(r.name, 17f, bold = true)
        titles.tv(r.product.ifEmpty { "Versiyon seçilmedi" }, 13.5f, Palette.MUTED)
        head.addView(titles, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        head.addView(MaterialSwitch(this).apply {
            isChecked = r.enabled
            setOnCheckedChangeListener { _, v -> r.enabled = v; s.saveRobot(r); ui.post { render() } }
        })

        // Durum
        val st = RobotStatus.load(s.prefs, r.id)
        val statusView = when {
            !r.enabled -> badge("Kapalı", Palette.MUTED, Palette.GRAY_BG)
            st == null -> badge("Henüz kontrol edilmedi", Palette.MUTED, Palette.GRAY_BG)
            st.state == "match" -> badge("✓  ${st.count} uygun araç · ${Fmt.price(st.best)}", Palette.GREEN, Palette.GREEN_BG)
            st.state == "missing" -> badge("Versiyon sitede bulunamadı", Palette.RED, Palette.RED_BG)
            else -> badge("Şu an uygun araç yok", Palette.MUTED, Palette.GRAY_BG)
        }
        c.addView(statusView, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(16) })

        // Hedef fiyat ve guncel en ucuz
        val prices = c.hrow(top = 14)
        fun col(label: String, value: String, color: Int) {
            val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            v.tv(label, 12.5f, Palette.MUTED)
            v.tv(value, 16f, color, bold = true)
            prices.addView(v, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        col("Hedef fiyat", if (r.maxPrice > 0) "${Fmt.price(r.maxPrice)} altı" else "Fark etmez", Palette.TEXT)
        if (r.enabled && st != null && st.cheapest > 0) {
            col("Versiyonun en ucuzu", "${Fmt.price(st.cheapest)}", if (st.cheapestSct <= 25) Palette.GREEN else Palette.TEXT)
        }

        // Istekler
        val chips = chipGroup()
        for ((title, vals) in r.want) {
            if (vals.isEmpty()) continue
            val neg = vals.size == 1 && vals.first().equals("YOK", true)
            val t = when {
                vals.size == 1 && vals.first().equals("VAR", true) -> title
                neg -> "$title yok"
                else -> vals.joinToString(" / ") { Fmt.value(it) }
            }
            chips.addView(if (neg) infoChip(t, Palette.RED, Palette.RED_BG) else infoChip(t))
        }
        if (chips.childCount > 0) {
            c.divider()
            c.addView(chips, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, WRAP_CONTENT))
        }
    }

    // ---------- Site ozeti ----------

    @SuppressLint("SetTextI18n")
    private fun siteSummary() {
        val all = Api.COLLECTIONS.keys.flatMap { Cache.products(this, it) ?: emptyList() }
        if (all.isEmpty()) {
            content.card(pad = 20).tv("Henüz veri yok. Sağ üstteki ↻ ile kontrol et.", 15f, Palette.MUTED)
            return
        }
        val c = content.card(pad = 18) { startActivity(Intent(this, SiteActivity::class.java)) }
        all.forEachIndexed { i, p ->
            if (i > 0) c.divider()
            val row = c.hrow()
            val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            t.tv(p.name, 15.5f).typeface = Palette.MEDIUM
            t.tv(if (p.variants.isEmpty()) "Seçilebilir konfigürasyon yok" else "${p.variants.size} konfigürasyon seçilebilir", 13f, Palette.MUTED)
            row.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            p.variants.firstOrNull()?.let { v ->
                val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
                right.tv(Fmt.price(v.turnkey), 15.5f, bold = true).gravity = Gravity.END
                right.tv("ÖTV %${v.sctRate}", 12.5f, if (v.sctRate <= 25) Palette.GREEN else Palette.AMBER).gravity = Gravity.END
                row.addView(right)
            }
        }
    }

    private fun checkNow() {
        if (checking) return
        checking = true
        toast("Kontrol ediliyor…")
        Thread {
            val o = Checker.run(applicationContext)
            ui.post {
                checking = false
                if (!o.ok) toast(o.summary.lineSequence().first().removePrefix("⚠ "))
                render()
            }
        }.start()
    }

    // ---------- Izinler ----------

    private fun needsNotif() = Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    private fun needsBattery() = !getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun needsExact() = Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Eksik izinlerin sistem pencerelerini sirayla acar (her biri en fazla 2 kez). */
    @SuppressLint("BatteryLife", "InlinedApi")
    private fun setupFlow() {
        if (setupBusy || isFinishing) return
        val p = s.prefs
        fun asked(k: String) = p.getInt(k, 0)
        fun mark(k: String) = p.edit().putInt(k, asked(k) + 1).apply()
        when {
            needsNotif() && asked("askNotif") < 2 -> {
                mark("askNotif"); setupBusy = true
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            needsBattery() && asked("askBattery") < 2 -> {
                mark("askBattery")
                runCatching {
                    startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                }
            }
            needsExact() && asked("askExact") < 2 -> {
                mark("askExact")
                runCatching {
                    startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                }
            }
            !p.getBoolean("firstStartDone", false) && !needsNotif() -> {
                p.edit().putBoolean("firstStartDone", true).apply()
                Scheduler.start(this)
                checkNow()
                toast("Hazır! Robot takibe başladı 👍")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        setupBusy = false
        ui.postDelayed({ setupFlow() }, 400)
    }

    private fun recheckPermissions() {
        s.prefs.edit().remove("askNotif").remove("askBattery").remove("askExact").apply()
        setupFlow()
    }
}
