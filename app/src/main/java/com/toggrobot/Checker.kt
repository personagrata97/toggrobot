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
                }
                continue
            }
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
        p.edit().putInt("fails", fails).apply()
        if (fails == 10) {
            Notifier.alert(ctx, Notifier.ID_PROBLEM, "Togg Robot siteye ulaşamıyor", "10 denemedir hata: $error", Api.SITE)
            Telegram.send(ctx, "⚠️ Togg Robot siteye ulaşamıyor: $error")
        }
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
        var delay = s.intervalMin * 60_000L
        // Site "cok sik geliyorsun" derse (429/403) veya sunucu hatasi varsa nazikce yavasla
        val fails = s.prefs.getInt("fails", 0)
        if (lastHttp == 429 || lastHttp == 403 || lastHttp >= 500 || lastHttp < 0) {
            delay = (delay * (1L shl fails.coerceAtMost(4))).coerceAtMost(15 * 60_000L)
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
