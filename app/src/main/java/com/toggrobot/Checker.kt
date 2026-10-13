package com.toggrobot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tek bir kontrol turu: veriyi indir, robotlari degerlendir, gerekirse bildir. */
object Checker {
    private val lock = Any()

    data class Outcome(val ok: Boolean, val httpCode: Int, val summary: String)

    fun run(ctx: Context): Outcome = synchronized(lock) {
        val s = Settings(ctx)
        val robots = s.robots.filter { it.enabled }
        val p = s.prefs
        val now = System.currentTimeMillis()
        if (robots.isEmpty()) {
            return Outcome(true, 200, "Aktif robot yok").also { save(ctx, it, now) }
        }

        val products = ArrayList<Product>()
        var httpCode = 200
        var error: String? = null
        for (col in robots.map { it.collection }.toSet()) {
            try {
                val json = Api.fetch(col)
                if (Cache.put(ctx, col, json)) Log.add(ctx, "${Api.COLLECTIONS[col] ?: col}: site verisi değişti")
                products += Api.parse(col, json)
            } catch (e: Api.HttpError) {
                httpCode = e.code
                error = e.message
            } catch (e: Exception) {
                httpCode = -1
                error = "Bağlantı hatası: ${e.javaClass.simpleName} ${e.message ?: ""}".trim()
            }
        }

        val lines = ArrayList<String>()
        if (error != null) lines += "⚠ $error"
        for (r in robots) {
            val prod = products.firstOrNull { r.matchesProduct(it) }
            if (prod == null) {
                if (error == null) {
                    lines += "${r.name}: '${r.product}' sitede bulunamadı"
                    RobotStatus.save(p, r.id, RobotStatus("missing", 0, 0, 0, 0, now))
                    // Site versiyonun adini degistirdiyse robot sessizce kor kalmasin
                    val streak = p.getInt("miss_${r.id}", 0) + 1
                    p.edit().putInt("miss_${r.id}", streak).apply()
                    if (streak >= 3) Problems.raise(
                        ctx, "miss_${r.id}", "⚠️ ${r.name}: versiyon sitede bulunamıyor",
                        "'${r.product}' adlı versiyon sitede artık yok (satıştan kalkmış ya da adı değişmiş olabilir). Robotu açıp versiyonu yeniden seç."
                    )
                }
                continue
            }
            p.edit().putInt("miss_${r.id}", 0).apply()
            Problems.clear(ctx, "miss_${r.id}")
            // Robotun istedigi bir secenek basligi sitede yoksa (site degistiyse) eslesme hic olmaz -> haber ver
            val titles = prod.options.map { Robot.norm(it.title) }.toSet()
            val unknown = r.want.filter { it.value.isNotEmpty() && Robot.norm(it.key) !in titles }.keys
            if (unknown.isNotEmpty()) {
                lines += "⚠ ${r.name}: sitede olmayan seçenek: ${unknown.joinToString()}"
                Problems.raise(
                    ctx, "opt_${r.id}", "⚠️ ${r.name}: ayarların siteyle uyuşmuyor",
                    "Sitede artık olmayan seçenek(ler): ${unknown.joinToString()}. Site değişmiş olabilir; robotu açıp seçenekleri yeniden işaretle."
                )
            } else Problems.clear(ctx, "opt_${r.id}")
            val full = r.fullHits(prod)
            val any = r.anyHits(prod)
            val cheap = prod.variants.firstOrNull()
            RobotStatus.save(
                p, r.id,
                RobotStatus(
                    if (full.isNotEmpty()) "match" else "none", full.size,
                    full.firstOrNull()?.turnkey ?: 0, cheap?.turnkey ?: 0, cheap?.sctRate ?: 0, now,
                )
            )
            if (full.isNotEmpty()) {
                lines += "✅ ${r.name}: ${full.size} uygun, en ucuz ${Fmt.price(full.first().turnkey)}"
            } else {
                val cheapest = prod.variants.firstOrNull()
                lines += "${r.name}: uygun yok" + (cheapest?.let { " (en ucuz ${Fmt.price(it.turnkey)}, ÖTV %${it.sctRate})" } ?: " (seçilebilir konfigürasyon yok)")
            }
            alertIfNeeded(ctx, s, r, prod, "full", full, now) { hits ->
                "🎉 ${r.name}: istediğin konfigürasyon ${Fmt.price(hits.first().turnkey)}!" to
                    "${prod.name}\n" + hits.take(3).joinToString("\n\n") { Fmt.variantLine(it) }
            }
            alertIfNeeded(ctx, s, r, prod, "any", if (full.isEmpty()) any else emptyList(), now) { hits ->
                "🚗 ${r.name}: ${prod.name} ${Fmt.price(hits.first().turnkey)}" to
                    "Seçeneklerin tam tutmuyor ama ${Fmt.price(r.anyMaxPrice)} altında seçilebilir konfigürasyon var:\n" +
                    hits.take(3).joinToString("\n\n") { Fmt.variantLine(it) }
            }
        }
        val out = Outcome(error == null, httpCode, lines.joinToString("\n"))
        save(ctx, out, now)

        // Ust uste hata: bir kez haber ver
        val fails = if (error == null) 0 else p.getInt("fails", 0) + 1
        p.edit().putInt("fails", fails)
            .putInt("statChecks", p.getInt("statChecks", 0) + 1)
            .putInt("statFails", p.getInt("statFails", 0) + (if (error == null) 0 else 1)).apply()
        if (fails >= 3) {
            Problems.raise(ctx, "net", "⚠️ Togg Robot siteye ulaşamıyor", "$fails denemedir hata: $error\nİnternet bağlantını kontrol et. Düzelince haber vereceğim.")
        } else if (error == null && Problems.clear(ctx, "net")) {
            Notifier.info(ctx, Notifier.ID_PROBLEM, "Togg Robot tekrar bağlandı", "Siteye yeniden ulaşılıyor, takip sürüyor.")
            Telegram.send(ctx, "✅ Togg Robot siteye tekrar ulaşıyor, takip sürüyor.")
        }
        Heartbeat.maybeSend(ctx, s, products, now)
        return out
    }

    private fun save(ctx: Context, o: Outcome, now: Long) {
        val p = Settings(ctx).prefs
        val prev = p.getString("lastSummary", "")
        p.edit().putLong("lastCheck", now).putString("lastSummary", o.summary).apply()
        if (prev != o.summary) Log.add(ctx, o.summary.replace("\n", " | "))
        Notifier.status(ctx, o.summary)
    }

    /**
     * Ayni sonuc icin surekli bildirim atmaz: yeni bir uygun konfigurasyon cikarsa veya fiyat degisirse hemen,
     * ayni durum surerse `repeatMin` dakikada bir hatirlatir. Uygun konfigurasyon kaybolursa sessizce haber verir.
     */
    private fun alertIfNeeded(
        ctx: Context, s: Settings, r: Robot, prod: Product, kind: String, hits: List<Variant>, now: Long,
        text: (List<Variant>) -> Pair<String, String>,
    ) {
        val p = s.prefs
        val keyK = "n_${r.id}_${kind}_key"
        val keyT = "n_${r.id}_${kind}_time"
        val id = Notifier.idFor(r.id, kind)
        val lastKey = p.getString(keyK, "") ?: ""
        if (hits.isEmpty()) {
            if (lastKey.isNotEmpty()) {
                p.edit().putString(keyK, "").putLong(keyT, 0).apply()
                if (kind == "full") {
                    Notifier.info(ctx, id, "${r.name}: uygun konfigürasyon artık yok", "${prod.name} için az önce bulunan seçenek siteden kalktı.")
                    Telegram.send(ctx, "ℹ️ ${r.name}: ${prod.name} için bulunan uygun konfigürasyon siteden kalktı.", Telegram.parseChats(r.tgChats))
                }
            }
            return
        }
        val key = hits.joinToString(",") { "${it.id}:${it.turnkey}" }
        val lastT = p.getLong(keyT, 0)
        val repeat = s.repeatMin
        if (key != lastKey || (repeat > 0 && now - lastT >= repeat * 60_000L)) {
            val (title, body) = text(hits)
            Notifier.alert(ctx, id, title, body, Api.SITE)
            Telegram.send(ctx, "$title\n\n$body\n\n${Api.SITE}", Telegram.parseChats(r.tgChats))
            p.edit().putString(keyK, key).putLong(keyT, now).apply()
            Log.add(ctx, "BİLDİRİM: $title")
        }
    }
}

/** Alarm zamanlamasi: arada telefon tamamen uyuyabilir, sadece kontrol aninda ~1 sn uyanir. */
object Scheduler {
    private fun pending(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, AlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun start(ctx: Context) {
        Settings(ctx).running = true
        Settings(ctx).prefs.edit().putInt("fails", 0).apply()
        scheduleIn(ctx, 1_000)
        Log.add(ctx, "Başlatıldı")
    }

    fun stop(ctx: Context) {
        Settings(ctx).running = false
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx))
        Notifier.cancelStatus(ctx)
        Log.add(ctx, "Durduruldu")
    }

    fun scheduleNext(ctx: Context, lastHttp: Int) {
        val s = Settings(ctx)
        if (!s.running) return
        var delay = Night.delay(s, System.currentTimeMillis())
        // Site "cok sik geliyorsun" derse (429/403) veya sunucu hatasi varsa nazikce yavasla
        val fails = s.prefs.getInt("fails", 0)
        if (lastHttp == 429 || lastHttp == 403 || lastHttp >= 500 || lastHttp < 0) {
            delay = (delay * (1L shl fails.coerceAtMost(4))).coerceAtMost(maxOf(15 * 60_000L, delay))
        }
        scheduleIn(ctx, delay)
    }

    fun scheduleIn(ctx: Context, delayMs: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val at = System.currentTimeMillis() + delayMs
        val pi = pending(ctx)
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        when {
            Settings(ctx).exactMode && canExact -> {
                val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            }
            canExact -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        if (!Settings(ctx).running) return
        val pending = goAsync()
        val wl = ctx.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "toggrobot:check")
        wl.acquire(50_000)
        Thread {
            var http = 200
            try {
                http = Checker.run(ctx).httpCode
            } catch (e: Throwable) {
                Log.add(ctx, "Beklenmeyen hata: $e")
            } finally {
                Scheduler.scheduleNext(ctx, http)
                if (wl.isHeld) wl.release()
                pending.finish()
            }
        }.start()
    }
}

/** Telefon yeniden baslarsa / uygulama guncellenirse zamanlamayi geri kur. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Settings(context).running) Scheduler.scheduleIn(context, 10_000)
    }
}

/** Kucuk dosya kaydi (son 300 satir, sadece degisiklikler yazilir). */
object Log {
    private const val MAX = 300
    private val df = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US)

    fun time(t: Long = System.currentTimeMillis()): String = df.format(Date(t))

    @Synchronized
    fun add(ctx: Context, line: String) {
        val f = File(ctx.filesDir, "log.txt")
        val lines = if (f.exists()) f.readLines().toMutableList() else mutableListOf()
        lines += "${time()}  $line"
        f.writeText((if (lines.size > MAX) lines.takeLast(MAX) else lines).joinToString("\n"))
    }

    fun read(ctx: Context, last: Int = 100): String {
        val f = File(ctx.filesDir, "log.txt")
        return if (f.exists()) f.readLines().takeLast(last).reversed().joinToString("\n") else ""
    }

    fun clear(ctx: Context) {
        File(ctx.filesDir, "log.txt").delete()
    }
}

/** Bir robotun son kontrol sonucu (ana ekrandaki kart icin). */
data class RobotStatus(val state: String, val count: Int, val best: Long, val cheapest: Long, val cheapestSct: Int, val time: Long) {
    companion object {
        fun save(p: android.content.SharedPreferences, id: String, st: RobotStatus) {
            p.edit().putString(
                "rs_$id",
                org.json.JSONObject().put("state", st.state).put("count", st.count).put("best", st.best)
                    .put("cheapest", st.cheapest).put("sct", st.cheapestSct).put("time", st.time).toString()
            ).apply()
        }

        fun load(p: android.content.SharedPreferences, id: String): RobotStatus? {
            val s = p.getString("rs_$id", null) ?: return null
            val o = runCatching { org.json.JSONObject(s) }.getOrNull() ?: return null
            return RobotStatus(o.optString("state"), o.optInt("count"), o.optLong("best"), o.optLong("cheapest"), o.optInt("sct"), o.optLong("time"))
        }
    }
}

/** Gece modu hesaplari. Gece seyrek kontrol eder, gece bitince tam zamaninda sik kontrole doner. */
object Night {
    private fun hourOf(t: Long) = java.util.Calendar.getInstance().apply { timeInMillis = t }.get(java.util.Calendar.HOUR_OF_DAY)

    fun isNight(s: Settings, t: Long): Boolean {
        if (!s.nightMode || s.nightStart == s.nightEnd) return false
        val h = hourOf(t)
        return if (s.nightStart < s.nightEnd) h in s.nightStart until s.nightEnd
        else h >= s.nightStart || h < s.nightEnd // ör. 23 -> 7
    }

    /** Gecenin bitecegi an (bir sonraki nightEnd:00) */
    private fun nightEndAfter(s: Settings, t: Long): Long {
        val c = java.util.Calendar.getInstance().apply {
            timeInMillis = t
            set(java.util.Calendar.HOUR_OF_DAY, s.nightEnd)
            set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 5); set(java.util.Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= t) c.add(java.util.Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** Simdiki zamana gore bir sonraki kontrole kadar beklenecek sure */
    fun delay(s: Settings, now: Long): Long {
        if (!isNight(s, now)) return s.intervalMin * 60_000L
        val nightStep = s.nightIntervalH * 3_600_000L
        return minOf(nightStep, nightEndAfter(s, now) - now).coerceAtLeast(60_000L)
    }

    fun describe(s: Settings): String =
        if (!s.nightMode) "Gece modu kapalı"
        else "%02d:00–%02d:00 arası %d saatte bir".format(s.nightStart, s.nightEnd, s.nightIntervalH)
}

/** Sorun bildirimleri: ayni sorun icin bir kez haber verir, sorun gecince sifirlanir. */
object Problems {
    fun raise(ctx: Context, key: String, title: String, text: String) {
        val p = Settings(ctx).prefs
        if (p.getBoolean("prob_$key", false)) return
        p.edit().putBoolean("prob_$key", true).apply()
        Notifier.alert(ctx, Notifier.idFor("prob", key), title, text, Api.SITE)
        Telegram.send(ctx, "$title\n$text")
        Log.add(ctx, "SORUN: $title")
    }

    /** true: daha once bildirilmis bir sorun gecti */
    fun clear(ctx: Context, key: String): Boolean {
        val p = Settings(ctx).prefs
        if (!p.getBoolean("prob_$key", false)) return false
        p.edit().putBoolean("prob_$key", false).apply()
        return true
    }
}

/**
 * Gunluk "hayattayim" raporu (varsayilan her gun 10:00'dan sonraki ilk kontrolde).
 * Rapor gelmediyse robot calismiyor demektir: telefon kapali, uygulama kapatilmis vb.
 */
object Heartbeat {
    fun maybeSend(ctx: Context, s: Settings, products: List<Product>, now: Long) {
        val p = s.prefs
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val day = cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
        if (cal.get(java.util.Calendar.HOUR_OF_DAY) < s.reportHour || p.getInt("hbDay", 0) == day) return
        val checks = p.getInt("statChecks", 0)
        val fails = p.getInt("statFails", 0)
        p.edit().putInt("hbDay", day).putInt("statChecks", 0).putInt("statFails", 0).apply()
        if (!s.dailyReport) return

        val sb = StringBuilder("🟢 Togg Robot günlük rapor\n")
        sb.append("Son raporadan beri $checks kontrol").append(if (fails > 0) ", $fails hatalı" else ", hepsi başarılı").append(".\n")
        for (r in s.robots.filter { it.enabled }) {
            val st = RobotStatus.load(p, r.id)
            sb.append("\n• ${r.name}: ").append(
                when (st?.state) {
                    "match" -> "${st.count} uygun araç var! En ucuz ${Fmt.price(st.best)}"
                    "none" -> "uygun araç yok" + (if (st.cheapest > 0) " (versiyonun en ucuzu ${Fmt.price(st.cheapest)}, ÖTV %${st.cheapestSct})" else "")
                    "missing" -> "versiyon sitede bulunamıyor!"
                    else -> "henüz sonuç yok"
                }
            )
        }
        if (products.isEmpty()) sb.append("\n\n⚠ Bu kontrolde siteden veri alınamadı.")
        sb.append("\n\nBu raporu her gün alacaksın. Gelmezse robot çalışmıyor demektir (telefon kapalı / uygulama durdurulmuş).")
        Notifier.info(ctx, Notifier.idFor("hb", "day"), "Togg Robot çalışıyor ✓", sb.toString().lineSequence().drop(1).joinToString(" ").take(200))
        Telegram.send(ctx, sb.toString())
        Log.add(ctx, "Günlük rapor gönderildi")
    }
}
