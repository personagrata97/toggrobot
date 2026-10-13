package com.toggrobot

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import java.util.Locale

/** Sitede su an secilebilen tum konfigurasyonlar (sitenin kendi verisinden). */
class SiteActivity : BaseActivity() {
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = screen("Sitede şu an", back = true, Triple(R.drawable.ic_refresh, "Yenile") { refresh() })
        render()
    }

    private fun refresh() {
        toast("Yenileniyor…")
        Thread {
            for (col in Api.COLLECTIONS.keys) runCatching { Cache.put(this, col, Api.fetch(col)) }
            runOnUiThread { render() }
        }.start()
    }

    @SuppressLint("SetTextI18n")
    private fun render() {
        content.removeAllViews()
        content.tv(
            "Konfigüratörde şu an gerçekten seçilebilen kombinasyonlar. Fiyatlar anahtar teslimdir (tescil ve noter dahil).",
            13f, Palette.MUTED, top = 8
        )
        var any = false
        for ((col, label) in Api.COLLECTIONS) {
            val products = Cache.products(this, col) ?: continue
            any = true
            content.section(label)
            for (p in products) {
                val c = content.card()
                c.tv(p.name, 17f, bold = true)
                c.tv(
                    "Liste fiyatı ${Fmt.price(p.basePrice25)}'den başlıyor (ÖTV %25 ile) · ${p.variants.size} konfigürasyon",
                    12f, Palette.MUTED
                )
                if (p.variants.isEmpty()) c.tv("Şu an seçilebilir konfigürasyon yok.", 14f, Palette.MUTED, top = 10)
                p.variants.forEach { v ->
                    c.divider()
                    val row = c.hrow()
                    row.addView(LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        tv(Fmt.price(v.turnkey), 18f, bold = true)
                        if (v.delivery.isNotEmpty()) tv("Teslim: ${v.delivery}", 12f, Palette.MUTED)
                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    val low = v.sctRate <= 25
                    row.addView(badge("ÖTV %${v.sctRate}", if (low) Palette.GREEN else Palette.AMBER, if (low) Palette.GREEN_BG else Palette.AMBER_BG))

                    val chips = chipGroup()
                    v.choice["Renk"]?.let { chips.addView(infoChip(Fmt.value(it), Palette.NAVY, android.graphics.Color.parseColor("#DCE7F5"))) }
                    v.choice["Koltuklar"]?.let { chips.addView(infoChip(Fmt.value(it))) }
                    for ((title, value) in v.choice) {
                        if (title in setOf("Renk", "Koltuklar", "Jant", "Jantlar")) continue
                        when (value.uppercase(Locale.ROOT)) {
                            "VAR" -> chips.addView(infoChip("✓ $title"))
                            "YOK" -> {}
                            else -> chips.addView(infoChip("$title: ${Fmt.value(value)}"))
                        }
                    }
                    c.addView(chips, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })
                }
            }
        }
        if (!any) content.card().tv("Henüz veri yok. Sağ üstteki ↻ ile yenile.", 14f, Palette.MUTED)
        content.fullWidth(outlinedButton("Konfigüratörü tarayıcıda aç") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Api.SITE)))
        }, top = 20)
        content.gravity = Gravity.TOP
    }
}
