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

        content.section("Robotlarım", "+ Yeni robot") { startActivity(Intent(this, RobotEditActivity::class.java)) }
        val robots = s.robots
        if (robots.isEmpty()) {
            content.card().tv("Henüz robot yok. '+ Yeni robot' ile istediğin aracı tanımla.", 14f, Palette.MUTED)
        }
        robots.forEach { robotCard(it) }

        content.section("Sitede şu an", "Tümünü gör") { startActivity(Intent(this, SiteActivity::class.java)) }
        siteSummary()
    }

    // ---------- Durum karti ----------

    @SuppressLint("SetTextI18n")
    private fun heroCard() {
        val running = s.running
        val c = content.card(pad = 20, color = Palette.NAVY, stroke = false)
        c.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Palette.NAVY, Palette.NAVY2)).apply { cornerRadius = dpf(16) }

        val top = c.hrow()
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        statusRow.addView(TextView(this).apply {
            width = dp(10); height = dp(10)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (running) Color.parseColor("#32D583") else Color.parseColor("#98A2B3"))
            }
        })
        statusRow.addView(TextView(this).apply {
            text = if (running) "  Takipte" else "  Durduruldu"
            textSize = 22f; setTextColor(Color.WHITE); setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        left.addView(statusRow)
        val last = s.prefs.getLong("lastCheck", 0)
        left.tv(
            (if (!running) "Başlatmak için düğmeye dokun"
            else if (Night.isNight(s, System.currentTimeMillis())) "Gece modu: ${s.nightIntervalH} saatte bir kontrol"
            else "Her ${s.intervalMin} dakikada bir kontrol" + (if (s.nightMode) "\nGece: ${Night.describe(s)}" else "")) +
                (if (last > 0) "\nSon kontrol: ${Log.time(last)}" else ""),
            13f, Color.parseColor("#C7D2E0"), top = 4
        )
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

        val fails = s.prefs.getInt("fails", 0)
        if (fails > 0) {
            c.addView(badge("Siteye ulaşılamadı (${fails}x) — tekrar denenecek", Palette.AMBER, Palette.AMBER_BG).apply {
                (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(12)
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(12) })
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
        val c = content.card { startActivity(Intent(this, RobotEditActivity::class.java).putExtra("id", r.id)) }
        val head = c.hrow()
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.tv(r.name, 17f, bold = true)
        titles.tv(r.product.ifEmpty { "Versiyon seçilmedi" }, 13f, Palette.MUTED)
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
            st.state == "match" -> badge("✓ ${st.count} uygun araç · ${Fmt.price(st.best)}", Palette.GREEN, Palette.GREEN_BG)
            st.state == "missing" -> badge("Versiyon sitede bulunamadı", Palette.RED, Palette.RED_BG)
            else -> badge("Uygun araç yok", Palette.MUTED, Palette.GRAY_BG)
        }
        c.addView(statusView, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(12) })
        if (r.enabled && st != null && st.state == "none" && st.cheapest > 0) {
            c.tv("Bu versiyonun şu an en ucuzu ${Fmt.price(st.cheapest)} (ÖTV %${st.cheapestSct})", 12f, Palette.MUTED, top = 6)
        }

        // Istekler
        val chips = chipGroup()
        if (r.maxPrice > 0) chips.addView(infoChip("< ${Fmt.price(r.maxPrice)}", Palette.NAVY, Color.parseColor("#DCE7F5")))
        for ((title, vals) in r.want) {
            if (vals.isEmpty()) continue
            val t = when {
                vals.size == 1 && vals.first().equals("VAR", true) -> title
                vals.size == 1 && vals.first().equals("YOK", true) -> "$title ✕"
                else -> vals.joinToString(" / ") { Fmt.value(it) }
            }
            val neg = vals.size == 1 && vals.first().equals("YOK", true)
            chips.addView(if (neg) infoChip(t, Palette.RED, Palette.RED_BG) else infoChip(t))
        }
        c.addView(chips, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
    }

    // ---------- Site ozeti ----------

    @SuppressLint("SetTextI18n")
    private fun siteSummary() {
        var any = false
        for ((col, _) in Api.COLLECTIONS) {
            val products = Cache.products(this, col) ?: continue
            for (p in products) {
                any = true
                val c = content.card(pad = 14) { startActivity(Intent(this, SiteActivity::class.java)) }
                val row = c.hrow()
                val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                t.tv(p.name, 15f, bold = true)
                t.tv(
                    if (p.variants.isEmpty()) "Seçilebilir konfigürasyon yok"
                    else "${p.variants.size} konfigürasyon seçilebilir",
                    13f, Palette.MUTED
                )
                row.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                p.variants.firstOrNull()?.let { v ->
                    val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
                    right.tv(Fmt.price(v.turnkey), 15f, bold = true).gravity = Gravity.END
                    right.tv("ÖTV %${v.sctRate}", 12f, if (v.sctRate <= 25) Palette.GREEN else Palette.AMBER).gravity = Gravity.END
                    row.addView(right)
                }
            }
        }
        if (!any) content.card().tv("Henüz veri yok. Sağ üstteki ↻ ile kontrol et.", 14f, Palette.MUTED)
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
