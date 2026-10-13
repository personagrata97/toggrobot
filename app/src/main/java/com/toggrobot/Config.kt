package com.toggrobot

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Kullanici ayarlari (SharedPreferences). Anahtar kelimelerde: virgul = VE, "/" = VEYA. */
class Config(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("toggrobot", Context.MODE_PRIVATE)

    var url by StrPref("url", "https://configurator.togg.com.tr/")
    var intervalSec by IntPref("intervalSec", 60)
    var basicThreshold by IntPref("basicThreshold", 2_600_000)
    var fullThreshold by IntPref("fullThreshold", 2_500_000)
    var fullMode by BoolPref("fullMode", true)
    var desktopMode by BoolPref("desktopMode", false)
    var blockImages by BoolPref("blockImages", true)
    var overlayMode by BoolPref("overlayMode", false)
    var repeatMin by IntPref("repeatMin", 10)
    var stepDelayMs by IntPref("stepDelayMs", 1500)

    var kwModel by StrPref("kwModel", "t10f")
    var kwVariant by StrPref("kwVariant", "v2, rwd, uzun menzil/long range")
    var kwKis by StrPref("kwKis", "kis paket")
    var kwAkilli by StrPref("kwAkilli", "akilli, destek")
    var kwRenk by StrPref("kwRenk", "kula")
    var kwKoltuk by StrPref("kwKoltuk", "siyah, vegan")
    var kwTavan by StrPref("kwTavan", "panoramik/cam tavan")
    var nextWords by StrPref("nextWords", "devam/ileri/sonraki/next/continue")
    var tabWords by StrPref("tabWords", "paket,dis renk,renk,ic mekan,koltuk,donanim,opsiyon")

    var running by BoolPref("running", false)

    fun botConfig(runId: String, debug: Boolean): JSONObject {
        val targets = JSONArray()
        fun t(id: String, label: String, kind: String, kw: String, toggle: Boolean = false, optional: Boolean = false) {
            targets.put(
                JSONObject()
                    .put("id", id).put("label", label).put("kind", kind)
                    .put("keywords", kw).put("toggle", toggle).put("optional", optional)
            )
        }
        t("model", "Model (T10F)", "base", kwModel, optional = true)
        t("variant", "V2 RWD Uzun Menzil", "variant", kwVariant)
        t("kis", "Kis Paketi", "extra", kwKis, toggle = true)
        t("akilli", "Akilli Destek Paketi", "extra", kwAkilli, toggle = true)
        t("renk", "Dis renk (Kula)", "extra", kwRenk)
        t("koltuk", "Koltuk (Siyah vegan deri)", "extra", kwKoltuk)
        t("tavan", "Panoramik cam tavan", "extra", kwTavan, toggle = true)
        return JSONObject()
            .put("runId", runId)
            .put("fullMode", fullMode)
            .put("debug", debug)
            .put("timeoutMs", 75_000)
            .put("stepDelayMs", stepDelayMs)
            .put("maxSteps", 40)
            .put("nextWords", nextWords)
            .put("tabWords", tabWords)
            .put("targets", targets)
    }

    private inner class StrPref(val key: String, val def: String) {
        operator fun getValue(thisRef: Any?, p: Any?): String = prefs.getString(key, def) ?: def
        operator fun setValue(thisRef: Any?, p: Any?, v: String) = prefs.edit().putString(key, v).apply()
    }

    private inner class IntPref(val key: String, val def: Int) {
        operator fun getValue(thisRef: Any?, p: Any?): Int = prefs.getInt(key, def)
        operator fun setValue(thisRef: Any?, p: Any?, v: Int) = prefs.edit().putInt(key, v).apply()
    }

    private inner class BoolPref(val key: String, val def: Boolean) {
        operator fun getValue(thisRef: Any?, p: Any?): Boolean = prefs.getBoolean(key, def)
        operator fun setValue(thisRef: Any?, p: Any?, v: Boolean) = prefs.edit().putBoolean(key, v).apply()
    }
}
