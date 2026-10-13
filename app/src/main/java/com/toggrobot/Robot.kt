package com.toggrobot

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Bir takip robotu. `want`: secenek basligi -> kabul edilen degerler.
 * Bos kume / olmayan baslik = "fark etmez". Ayni secenekte birden fazla deger = VEYA
 * (orn. Renk: kula, seyhan -> ikisinden biri olsun).
 */
data class Robot(
    var id: String,
    var name: String,
    var enabled: Boolean,
    var collection: String,
    var product: String,
    val want: MutableMap<String, MutableSet<String>>,
    /** Anahtar teslim fiyat bu degerin altindaysa alarm (0 = sinir yok). */
    var maxPrice: Long,
    /** Secenekler tutmasa bile bu versiyonda herhangi bir konfigurasyon bu fiyatin altindaysa haber ver (0 = kapali). */
    var anyMaxPrice: Long,
) {
    fun matchesProduct(p: Product) = p.collection == collection && norm(p.name) == norm(product)

    fun matches(v: Variant): Boolean = want.all { (title, allowed) ->
        allowed.isEmpty() || allowed.any { norm(it) == norm(v.choice[title] ?: "") }
    }

    fun fullHits(p: Product): List<Variant> =
        p.variants.filter { matches(it) && (maxPrice <= 0 || it.turnkey < maxPrice) }

    fun anyHits(p: Product): List<Variant> =
        if (anyMaxPrice <= 0) emptyList() else p.variants.filter { it.turnkey < anyMaxPrice }

    fun describe(): String {
        val model = Api.COLLECTIONS[collection] ?: collection
        val prefs = want.filter { it.value.isNotEmpty() }.entries.joinToString(", ") { (t, vs) ->
            val shown = vs.joinToString("/") { Fmt.value(it) }
            when {
                vs.size == 1 && vs.first().equals("VAR", true) -> t
                vs.size == 1 && vs.first().equals("YOK", true) -> "$t olmasın"
                else -> "$t: $shown"
            }
        }
        val price = if (maxPrice > 0) " • < ${Fmt.price(maxPrice)}" else ""
        return "${product.ifEmpty { model }}$price\n${prefs.ifEmpty { "Tüm seçenekler fark etmez" }}"
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("enabled", enabled)
        .put("collection", collection).put("product", product)
        .put("maxPrice", maxPrice).put("anyMaxPrice", anyMaxPrice)
        .put("want", JSONObject().apply { want.forEach { (k, v) -> put(k, JSONArray(v.toList())) } })

    companion object {
        fun norm(s: String) = s.trim().lowercase(Locale.ROOT)

        fun fromJson(o: JSONObject): Robot {
            val want = LinkedHashMap<String, MutableSet<String>>()
            o.optJSONObject("want")?.let { w ->
                w.keys().forEach { k ->
                    val a = w.optJSONArray(k)
                    val set = LinkedHashSet<String>()
                    if (a != null) for (i in 0 until a.length()) set += a.optString(i)
                    want[k] = set
                }
            }
            return Robot(
                id = o.optString("id"), name = o.optString("name"), enabled = o.optBoolean("enabled", true),
                collection = o.optString("collection"), product = o.optString("product"), want = want,
                maxPrice = o.optLong("maxPrice"), anyMaxPrice = o.optLong("anyMaxPrice"),
            )
        }

        fun newId() = "r" + System.currentTimeMillis()

        /** Ilk kurulumdaki ornek robot: kullanicinin istedigi konfigurasyon. */
        fun defaultRobot() = Robot(
            id = "r1", name = "Benim T10F'im", enabled = true,
            collection = "t10f-configurator", product = "T10F V2 RWD Uzun Menzil",
            want = linkedMapOf(
                "Kış Paketi" to mutableSetOf("VAR"),
                "Akıllı Destek Paketi" to mutableSetOf("VAR"),
                "Renk" to mutableSetOf("kula"),
                "Koltuklar" to mutableSetOf("seats-faux-leather-black"),
                "Panoramik Cam Tavan" to mutableSetOf("VAR"),
                "Meridian Premium Ses Sistemi" to mutableSetOf("YOK"),
                "Siyah Renkte Tavan ve Yan Aynalar" to mutableSetOf("YOK"),
            ),
            maxPrice = 2_500_000, anyMaxPrice = 2_600_000,
        )
    }
}

/** Uygulama ayarlari ve robot listesi. */
class Settings(ctx: Context) {
    val prefs: SharedPreferences = ctx.getSharedPreferences("toggrobot2", Context.MODE_PRIVATE)

    var running: Boolean
        get() = prefs.getBoolean("running", false)
        set(v) = prefs.edit().putBoolean("running", v).apply()

    /** Kontrol araligi (dakika) */
    var intervalMin: Int
        get() = prefs.getInt("intervalMin", 1)
        set(v) = prefs.edit().putInt("intervalMin", v.coerceIn(1, 60)).apply()

    /** Kesin zamanlama: telefon derin uykudayken de tam zamaninda kontrol eder. */
    var exactMode: Boolean
        get() = prefs.getBoolean("exactMode", true)
        set(v) = prefs.edit().putBoolean("exactMode", v).apply()

    /** Ayni uygun konfigurasyon icin hatirlatma araligi (dakika, 0 = tekrar etme) */
    var repeatMin: Int
        get() = prefs.getInt("repeatMin", 15)
        set(v) = prefs.edit().putInt("repeatMin", v.coerceAtLeast(0)).apply()

    var robots: List<Robot>
        get() {
            val s = prefs.getString("robots", null) ?: return listOf(Robot.defaultRobot())
            val a = JSONArray(s)
            return (0 until a.length()).map { Robot.fromJson(a.getJSONObject(it)) }
        }
        set(v) = prefs.edit().putString("robots", JSONArray(v.map { it.toJson() }).toString()).apply()

    fun saveRobot(r: Robot) {
        val list = robots.toMutableList()
        val i = list.indexOfFirst { it.id == r.id }
        if (i >= 0) list[i] = r else list += r
        robots = list
    }

    fun deleteRobot(id: String) {
        robots = robots.filter { it.id != id }
    }
}

/** Son indirilen veriler (sadece degistiginde diske yazilir). */
object Cache {
    private fun file(ctx: Context, collection: String) = File(ctx.filesDir, "cache-$collection.json")

    /** true: veri oncekinden farkli */
    fun put(ctx: Context, collection: String, json: String): Boolean {
        val f = file(ctx, collection)
        if (f.exists() && f.length() == json.length.toLong() && f.readText() == json) return false
        f.writeText(json)
        return true
    }

    fun products(ctx: Context, collection: String): List<Product>? {
        val f = file(ctx, collection)
        if (!f.exists()) return null
        return runCatching { Api.parse(collection, f.readText()) }.getOrNull()
    }

    fun age(ctx: Context, collection: String): Long {
        val f = file(ctx, collection)
        return if (f.exists()) System.currentTimeMillis() - f.lastModified() else Long.MAX_VALUE
    }
}
