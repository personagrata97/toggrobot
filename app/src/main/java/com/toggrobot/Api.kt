package com.toggrobot

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale

data class OptValue(val id: String, val value: String, val isDefault: Boolean, val price25: Long, val price65: Long)

data class Option(val id: String, val title: String, val group: String, val type: String, val values: List<OptValue>)

/** Sitede gercekten secilebilen tek bir konfigurasyon. choice: secenek basligi -> deger (orn. "Renk" -> "kula"). */
data class Variant(
    val id: String,
    val sctRate: Int,
    val turnkey: Long,
    val withTaxes: Long,
    val delivery: String,
    val choice: Map<String, String>,
)

data class Product(
    val id: String,
    val name: String,
    val collection: String,
    val basePrice25: Long,
    val options: List<Option>,
    val variants: List<Variant>,
)

/**
 * Togg konfiguratorunun kendi kullandigi veri servisi.
 * Tek istek (~50 KB, gzip ile cok daha az) ile tum versiyonlar, secenekler ve
 * secilebilen konfigurasyonlar (fiyat + OTV orani dahil) gelir.
 */
object Api {
    private const val BASE =
        "https://bff.dfs.togg.cloud/smart-device-products/TUR/getSmartDeviceProducts/v1?collectionHandle="
    const val SITE = "https://configurator.togg.com.tr/"

    /** collectionHandle -> gorunen ad */
    val COLLECTIONS = linkedMapOf("t10f-configurator" to "T10F", "t10x-configurator" to "T10X")

    class HttpError(val code: Int) : Exception("Sunucu yaniti: HTTP $code")

    /** Ham JSON'u indirir. Android, gzip sikistirmayi otomatik ister ve acar. */
    fun fetch(collection: String): String {
        val c = URL(BASE + URLEncoder.encode(collection, "UTF-8")).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.useCaches = false
        c.setRequestProperty("Accept", "application/json, text/plain, */*")
        c.setRequestProperty("Authorization", "Bearer unauthorized") // sitenin kendisi de bunu gonderiyor
        c.setRequestProperty("Origin", "https://configurator.togg.com.tr")
        c.setRequestProperty("Referer", SITE)
        try {
            val code = c.responseCode
            if (code != 200) throw HttpError(code)
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun parse(collection: String, json: String): List<Product> {
        val arr = JSONObject(json).optJSONArray("products") ?: return emptyList()
        val out = ArrayList<Product>()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val options = ArrayList<Option>()
            val optById = HashMap<String, Option>()
            val valById = HashMap<String, OptValue>()
            val oa = p.optJSONArray("options")
            if (oa != null) for (j in 0 until oa.length()) {
                val o = oa.getJSONObject(j)
                val values = ArrayList<OptValue>()
                val va = o.optJSONArray("values")
                if (va != null) for (k in 0 until va.length()) {
                    val v = va.getJSONObject(k)
                    val pr = v.optJSONObject("price")
                    val ov = OptValue(
                        id = v.optString("id"),
                        value = v.optString("value"),
                        isDefault = v.optBoolean("default"),
                        price25 = Math.round(pr?.optJSONObject("sct_25")?.optDouble("with_all_taxes", 0.0) ?: 0.0),
                        price65 = Math.round(pr?.optJSONObject("sct_65")?.optDouble("with_all_taxes", 0.0) ?: 0.0),
                    )
                    values += ov
                    valById[ov.id] = ov
                }
                val opt = Option(o.optString("id"), o.optString("title"), o.optString("group"), o.optString("type"), values)
                options += opt
                optById[opt.id] = opt
            }
            val variants = ArrayList<Variant>()
            val vs = p.optJSONArray("variants")
            if (vs != null) for (j in 0 until vs.length()) {
                val v = vs.getJSONObject(j)
                val choice = LinkedHashMap<String, String>()
                val vo = v.optJSONArray("options")
                if (vo != null) for (k in 0 until vo.length()) {
                    val x = vo.getJSONObject(k)
                    val opt = optById[x.optString("id")] ?: continue
                    val value = valById[x.optString("value_id")] ?: continue
                    choice[opt.title] = value.value
                }
                // Konfigurasyonda yazmayan secenekler icin varsayilan degeri kullan
                for (opt in options) {
                    if (opt.title !in choice) opt.values.firstOrNull { it.isDefault }?.let { choice[opt.title] = it.value }
                }
                val price = v.optJSONObject("price")
                variants += Variant(
                    id = v.optString("id"),
                    sctRate = v.optInt("sct_rate"),
                    turnkey = Math.round(v.optDouble("turnkey_price", 0.0)),
                    withTaxes = Math.round(price?.optDouble("with_all_taxes", 0.0) ?: 0.0),
                    delivery = v.optString("estimated_delivery_date_text"),
                    choice = choice,
                )
            }
            out += Product(
                id = p.optString("id"),
                name = p.optString("name"),
                collection = collection,
                basePrice25 = Math.round(p.optJSONObject("price")?.optJSONObject("sct_25")?.optDouble("with_all_taxes", 0.0) ?: 0.0),
                options = options,
                variants = variants.sortedBy { it.turnkey },
            )
        }
        return out
    }
}

object Fmt {
    private val tr = Locale("tr", "TR")

    fun price(v: Long): String = NumberFormat.getIntegerInstance(tr).format(v) + " ₺"

    /** API degerlerini okunur hale getirir: "seats-faux-leather-black" -> "Siyah vegan deri" */
    fun value(v: String): String {
        val l = v.lowercase(Locale.ROOT)
        when (l) {
            "var" -> return "Var"
            "yok" -> return "Yok"
        }
        if (l.startsWith("seats-")) {
            val color = when {
                "dark-gray" in l -> "Koyu gri"
                "gray" in l || "grey" in l -> "Gri"
                "black" in l -> "Siyah"
                "creme" in l || "cream" in l -> "Krem"
                "brown" in l -> "Kahverengi"
                "white" in l -> "Beyaz"
                else -> ""
            }
            val mat = when {
                "faux-leather" in l || "vegan" in l -> "vegan deri"
                "leather" in l -> "deri"
                "fabric" in l -> "kumaş"
                else -> l.removePrefix("seats-").replace('-', ' ')
            }
            return "$color $mat".trim().replaceFirstChar { it.titlecase(tr) }
        }
        Regex("^(\\d+)-inch-jant$").find(l)?.let { return "${it.groupValues[1]} inç jant" }
        return v.replace('-', ' ').replaceFirstChar { it.titlecase(tr) }
    }

    /** Konfigurasyonun kisa ozeti: "Kula • Siyah vegan deri • Kış Paketi • Panoramik Cam Tavan" */
    fun variant(v: Variant): String {
        val parts = ArrayList<String>()
        v.choice["Renk"]?.let { parts += value(it) }
        v.choice["Koltuklar"]?.let { parts += value(it) }
        for ((title, value) in v.choice) {
            if (title == "Renk" || title == "Koltuklar" || title == "Jant" || title == "Jantlar") continue
            when (value.uppercase(Locale.ROOT)) {
                "VAR" -> parts += title
                "YOK" -> {}
                else -> parts += "$title: ${value(value)}"
            }
        }
        return parts.joinToString(" • ")
    }

    fun variantLine(v: Variant): String =
        "${price(v.turnkey)} (ÖTV %${v.sctRate}${if (v.delivery.isNotEmpty()) ", teslim ${v.delivery}" else ""})\n${variant(v)}"
}
