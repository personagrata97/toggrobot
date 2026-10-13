package com.toggrobot

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val s = Settings(this)
        val content = screen("Ayarlar", back = true)

        // ---- Kontrol ----
        content.section("Kontrol")
        val c1 = content.card()
        c1.tv("Kontrol sıklığı", 15f, bold = true)
        val intervals = listOf(1, 2, 3, 5, 10)
        c1.addView(
            segmented(intervals.map { "$it dk" }, intervals.indexOf(s.intervalMin).let { if (it < 0) 0 else it }) { i ->
                s.intervalMin = intervals[i]
                if (s.running) Scheduler.scheduleIn(this, 2_000)
            },
            android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }
        )
        c1.tv("Her kontrol tek küçük bir istek. Telefon aradaki sürede uyur.", 12f, Palette.MUTED, top = 6)
        c1.divider()
        c1.switchRow("Gece modu", "Gece seyrek kontrol ederek pil ve internetten tasarruf eder.", s.nightMode) {
            s.nightMode = it; if (s.running) Scheduler.scheduleIn(this, 2_000)
        }
        val nStart = c1.field("Gece başlangıcı (saat)", s.nightStart.toString(), numeric = true, suffix = ":00")
        val nEnd = c1.field("Gece bitişi (saat)", s.nightEnd.toString(), numeric = true, suffix = ":00",
            helper = "Bu saatte tam zamanında sık kontrole döner")
        val nInt = c1.field("Gece kontrol aralığı", s.nightIntervalH.toString(), numeric = true, suffix = "saat")
        saveNight = {
            nStart.text.toString().toIntOrNull()?.let { s.nightStart = it }
            nEnd.text.toString().toIntOrNull()?.let { s.nightEnd = it }
            nInt.text.toString().toIntOrNull()?.let { s.nightIntervalH = it }
        }
        c1.divider()
        c1.switchRow(
            "Kesin zamanlama",
            "Telefon uzun süre kullanılmasa da tam zamanında kontrol eder. Kapatırsan Android gece kontrolleri birkaç dakika geciktirebilir.",
            s.exactMode
        ) { s.exactMode = it; if (s.running) Scheduler.scheduleIn(this, 2_000) }

        // ---- Bildirim ----
        content.section("Bildirim")
        val c2 = content.card()
        c2.tv("Hatırlatma", 15f, bold = true)
        c2.tv("Uygun araç sitede durdukça ne sıklıkla tekrar haber versin?", 12f, Palette.MUTED)
        val repeats = listOf(0, 5, 15, 60)
        c2.addView(
            segmented(listOf("Bir kez", "5 dk", "15 dk", "1 saat"), repeats.indexOf(s.repeatMin).let { if (it < 0) 2 else it }) { i ->
                s.repeatMin = repeats[i]
            },
            android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }
        )
        c2.divider()
        c2.switchRow(
            "Günlük rapor",
            "Her gün saat ${s.reportHour}:00'dan sonra 'çalışıyorum' raporu gönderir. Rapor gelmezse robot durmuş demektir.",
            s.dailyReport
        ) { s.dailyReport = it }
        c2.fullWidth(outlinedButton("🔔 Alarm sesini dene") {
            Notifier.alert(this, 9, "Test bildirimi", "Uygun araç çıkınca böyle haber vereceğim.", Api.SITE)
        }, top = 14)

        // ---- Telegram ----
        content.section("Telegram")
        val c3 = content.card()
        c3.tv("iPhone'a ya da arkadaşına mesaj", 15f, bold = true)
        c3.tv(
            "1. Telegram'da @BotFather → /newbot → bota bir ad ver ve verdiği token'ı aşağıya yapıştır.\n" +
                "2. Kendi botunu Telegram'da aç, Başlat'a bas ve bir mesaj yaz.\n" +
                "3. 'Sohbetleri bul'a, ardından 'Test mesajı'na bas.",
            13f, Palette.MUTED, top = 4
        )
        val token = c3.field("Bot token", s.tgToken, helper = "Kimseyle paylaşma")
        val chats = c3.field("Sohbet numaraları", s.tgChats, helper = "Birden fazlaysa virgülle ayır")
        c3.buttonRow(
            outlinedButton("Sohbetleri bul") {
                s.tgToken = token.text.toString()
                Thread {
                    val r = runCatching { Telegram.findChats(s.tgToken) }
                    runOnUiThread {
                        r.onSuccess { found ->
                            if (found.isEmpty()) toast("Bota henüz kimse yazmamış. Telegram'da bota bir mesaj at, tekrar dene.")
                            else {
                                val merged = (Telegram.parseChats(chats.text.toString()) + found.keys).distinct()
                                chats.setText(merged.joinToString(", "))
                                s.tgChats = chats.text.toString()
                                toast("Bulundu: " + found.values.joinToString(", "))
                            }
                        }.onFailure { toast("Olmadı: ${it.message}. Token doğru mu?") }
                    }
                }.start()
            },
            primaryButton("Test mesajı") {
                s.tgToken = token.text.toString(); s.tgChats = chats.text.toString()
                Thread {
                    val n = Telegram.send(applicationContext, "✅ Togg Robot bağlandı. Uygun araç çıkınca buraya yazacağım.")
                    runOnUiThread { toast(if (n > 0) "$n sohbete gönderildi 👍" else "Gönderilemedi. Token ve sohbet numarasını kontrol et.") }
                }.start()
            },
        )

        // ---- Diger ----
        content.section("Diğer")
        val c4 = content.card()
        c4.fullWidth(outlinedButton("Kayıtları göster") { showLog() }, top = 0)
        c4.tv("Togg Robot · Togg'un resmi bir uygulaması değildir.", 12f, Palette.MUTED, top = 12)

        // Token/sohbet ekrandan cikarken de kaydedilsin
        saveOnPause = { s.tgToken = token.text.toString(); s.tgChats = chats.text.toString() }
    }

    private var saveOnPause: (() -> Unit)? = null
    private var saveNight: (() -> Unit)? = null

    override fun onPause() {
        super.onPause()
        saveOnPause?.invoke()
        val before = Settings(this).let { Triple(it.nightStart, it.nightEnd, it.nightIntervalH) }
        saveNight?.invoke()
        val s = Settings(this)
        if (s.running && before != Triple(s.nightStart, s.nightEnd, s.nightIntervalH)) Scheduler.scheduleIn(this, 2_000)
    }

    private fun showLog() {
        val tv = TextView(this).apply {
            text = Log.read(this@SettingsActivity).ifEmpty { "Kayıt yok." }
            typeface = Typeface.MONOSPACE; textSize = 11f; setTextIsSelectable(true)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("Kayıtlar")
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("Kapat", null)
            .setNeutralButton("Temizle") { _, _ -> Log.clear(this) }
            .show()
    }
}
