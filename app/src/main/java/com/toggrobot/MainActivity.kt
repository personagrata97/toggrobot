package com.toggrobot

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
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
import android.provider.Settings as AndroidSettings
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : UiActivity() {

    private lateinit var s: Settings
    private lateinit var status: TextView
    private lateinit var robotsBox: LinearLayout
    private lateinit var siteBox: LinearLayout
    private lateinit var logView: TextView
    private val ui = Handler(Looper.getMainLooper())
    private var setupBusy = false
    private val refresher = object : Runnable {
        override fun run() {
            refreshStatus()
            ui.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = Settings(this)
        Notifier.createChannels(this)
        setContentView(build())
    }

    override fun onResume() {
        super.onResume()
        renderRobots()
        renderSite()
        ui.post(refresher)
        ui.postDelayed({ setupFlow() }, 600)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(refresher)
    }

    @SuppressLint("SetTextI18n")
    private fun build(): ScrollView {
        val root = vbox(14)

        status = TextView(this).apply { textSize = 15f; setTextColor(Color.BLACK) }
        root.card().addView(status)
        root.row(
            button("▶ Başlat") { Scheduler.start(this); toast("Başlatıldı"); refreshStatus() },
            button("■ Durdur") { Scheduler.stop(this); toast("Durduruldu"); refreshStatus() },
            button("↻ Şimdi kontrol") { checkNow() },
        )

        root.header("Robotlar")
        root.text("Her robot kendi isteklerine bakar. Biri sen, biri arkadaşın olabilir.", 13f)
        robotsBox = vbox()
        root.addView(robotsBox)
        root.addView(button("+ Yeni robot ekle") { startActivity(Intent(this, RobotEditActivity::class.java)) })

        root.header("Sitede şu an seçilebilenler")
        root.text("Sitenin kendi verisinden: hangi kombinasyonlar gerçekten seçilebiliyor, anahtar teslim fiyatı ve ÖTV oranı.", 13f)
        siteBox = vbox()
        root.addView(siteBox)

        root.header("Ayarlar")
        val interval = root.numberField("Kontrol aralığı (dakika)", s.intervalMin.toLong())
        val repeat = root.numberField("Aynı sonuç için hatırlatma aralığı (dakika, boş = tekrar etme)", s.repeatMin.toLong())
        root.addView(CheckBox(this).apply {
            text = "Kesin zamanlama: telefon uykudayken de tam zamanında kontrol et (önerilir)"
            isChecked = s.exactMode
            setOnCheckedChangeListener { _, v -> s.exactMode = v }
        })
        root.row(
            button("Kaydet") {
                s.intervalMin = interval.text.toString().toIntOrNull() ?: 1
                s.repeatMin = repeat.text.toString().toIntOrNull() ?: 0
                if (s.running) Scheduler.scheduleIn(this, 1_000)
                toast("Kaydedildi")
            },
            button("İzinleri kontrol et") { recheckPermissions() },
        )
        root.row(
            button("Bildirim testi") { Notifier.alert(this, 9, "Test bildirimi 🔔", "Bildirimler çalışıyor.", Api.SITE) },
            button("Siteyi aç") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Api.SITE))) },
        )

        root.header("Kayıt")
        root.addView(button("Kaydı temizle") { Log.clear(this); refreshStatus() })
        logView = TextView(this).apply { typeface = Typeface.MONOSPACE; textSize = 11f; setTextIsSelectable(true) }
        root.addView(logView)

        return ScrollView(this).apply { addView(root) }
    }

    private fun checkNow() {
        toast("Kontrol ediliyor...")
        Thread {
            val o = Checker.run(applicationContext)
            ui.post {
                toast(if (o.ok) "Kontrol tamam" else "Hata: ${o.summary.lineSequence().first()}")
                refreshStatus(); renderSite()
            }
        }.start()
    }

    @SuppressLint("SetTextI18n")
    private fun refreshStatus() {
        val last = s.prefs.getLong("lastCheck", 0)
        val warn = buildString {
            if (needsNotif()) append("\n⚠ Bildirim izni yok → 'İzinleri kontrol et'")
            if (needsBattery()) append("\n⚠ Pil kısıtlaması açık → 'İzinleri kontrol et'")
            if (needsExact()) append("\n⚠ Tam zamanlı alarm izni yok → 'İzinleri kontrol et'")
        }
        status.text = (if (s.running) "🟢 ÇALIŞIYOR — her ${s.intervalMin} dk" else "🔴 DURDU") +
            "\nSon kontrol: " + (if (last > 0) Log.time(last) else "henüz yok") +
            "\n" + (s.prefs.getString("lastSummary", "") ?: "") + warn
        logView.text = Log.read(this)
    }

    private fun renderRobots() {
        robotsBox.removeAllViews()
        val robots = s.robots
        if (robots.isEmpty()) robotsBox.text("Henüz robot yok.")
        for (r in robots) {
            val c = robotsBox.card()
            val title = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            title.addView(TextView(this).apply {
                text = r.name; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(Color.BLACK)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            title.addView(CheckBox(this).apply {
                text = "Aktif"; isChecked = r.enabled
                setOnCheckedChangeListener { _, v -> r.enabled = v; s.saveRobot(r) }
            })
            c.addView(title)
            c.text(r.describe(), 13f)
            if (r.anyMaxPrice > 0) c.text("Ayrıca: bu versiyon herhangi bir şekilde ${Fmt.price(r.anyMaxPrice)} altına düşerse haber ver", 12f)
            c.row(
                button("Düzenle") { startActivity(Intent(this, RobotEditActivity::class.java).putExtra("id", r.id)) },
                button("Sil") {
                    AlertDialog.Builder(this).setMessage("'${r.name}' silinsin mi?")
                        .setPositiveButton("Sil") { _, _ -> s.deleteRobot(r.id); renderRobots() }
                        .setNegativeButton("Vazgeç", null).show()
                },
            )
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderSite() {
        siteBox.removeAllViews()
        var any = false
        for ((col, label) in Api.COLLECTIONS) {
            val products = Cache.products(this, col) ?: continue
            any = true
            for (p in products) {
                val c = siteBox.card()
                c.addView(TextView(this).apply {
                    text = p.name; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(Color.BLACK)
                })
                if (p.variants.isEmpty()) c.text("Şu an seçilebilir konfigürasyon yok.")
                for (v in p.variants) c.text("• " + Fmt.variantLine(v), 12f)
                c.text("($label • liste fiyatı ${Fmt.price(p.basePrice25)}'den başlıyor, ÖTV %25 ile)", 11f, Color.GRAY)
            }
        }
        if (!any) siteBox.text("Henüz veri yok. '↻ Şimdi kontrol' ile çek.")
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
                startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
            needsExact() && asked("askExact") < 2 -> {
                mark("askExact")
                startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
            !p.getBoolean("firstStartDone", false) && !needsNotif() -> {
                p.edit().putBoolean("firstStartDone", true).apply()
                Scheduler.start(this)
                checkNow()
                toast("İzinler tamam, robot çalışmaya başladı 👍")
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
        if (!needsNotif() && !needsBattery() && !needsExact()) toast("Tüm izinler tamam 👍") else setupFlow()
    }
}
