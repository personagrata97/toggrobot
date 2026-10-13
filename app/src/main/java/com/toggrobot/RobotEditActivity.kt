package com.toggrobot

import android.app.AlertDialog
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import com.google.android.material.chip.ChipGroup

/** Robot ekleme / duzenleme. Secenekler sitenin gercek verisinden listelenir. */
class RobotEditActivity : BaseActivity() {

    private lateinit var s: Settings
    private lateinit var robot: Robot
    private lateinit var versionGroup: ChipGroup
    private lateinit var optionsBox: LinearLayout
    private val collections = Api.COLLECTIONS.keys.toList()
    private var products: List<Product> = emptyList()
    private var isNew = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = Settings(this)
        val id = intent.getStringExtra("id")
        val existing = s.robots.firstOrNull { it.id == id }
        isNew = existing == null
        robot = existing?.let { Robot.fromJson(it.toJson()) }
            ?: Robot(Robot.newId(), "Robot ${s.robots.size + 1}", true, "t10f-configurator", "", linkedMapOf(), 0, 0)

        val actions: Array<Triple<Int, String, () -> Unit>> =
            if (isNew) emptyArray() else arrayOf(Triple(R.drawable.ic_delete, "Sil") { confirmDelete() })
        val content = screen(if (isNew) "Yeni robot" else robot.name, back = true, *actions)

        // ---- Genel ----
        content.section("Genel")
        val c1 = content.card()
        val name = c1.field("Robot adı", robot.name, helper = "Ör. 'Benim T10F'im' ya da 'Ahmet'in aracı'")
        c1.tv("Model", 13f, Palette.MUTED, top = 14)
        c1.addView(
            segmented(collections.map { Api.COLLECTIONS[it] ?: it }, collections.indexOf(robot.collection).coerceAtLeast(0)) { i ->
                val col = collections[i]
                if (col != robot.collection) {
                    robot.collection = col; robot.product = ""; robot.want.clear()
                    loadProducts()
                }
            },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
        )
        c1.tv("Versiyon", 13f, Palette.MUTED, top = 14)
        versionGroup = chipGroup().apply { isSingleSelection = true; isSelectionRequired = true }
        c1.addView(versionGroup)

        // ---- Secenekler ----
        content.section("İstediğin özellikler")
        content.tv(
            "Seçmediğin her şey 'fark etmez' sayılır. Aynı başlıkta birden fazla seçersen biri olsun yeter (ör. Kula veya Seyhan).",
            13f, Palette.MUTED
        )
        optionsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(optionsBox)

        // ---- Fiyat ----
        content.section("Fiyat")
        val c3 = content.card()
        val maxPrice = c3.field(
            "En fazla anahtar teslim fiyat", if (robot.maxPrice > 0) robot.maxPrice.toString() else "",
            numeric = true, suffix = "₺", helper = "Bu fiyatın altındaysa alarm çalar. Boş = fiyat fark etmez."
        )
        val anyPrice = c3.field(
            "Ek alarm (isteğe bağlı)", if (robot.anyMaxPrice > 0) robot.anyMaxPrice.toString() else "",
            numeric = true, suffix = "₺",
            helper = "Özellikler tutmasa bile bu versiyon bu fiyatın altına inerse haber ver."
        )

        // ---- Telegram ----
        content.section("Telegram")
        val c4 = content.card()
        val tg = c4.field(
            "Bu robotun mesajları kime gitsin?", robot.tgChats,
            helper = "Boş = Ayarlar'daki Telegram sohbetleri. Arkadaşın için onun sohbet numarası."
        )

        content.fullWidth(primaryButton("Kaydet") {
            robot.name = name.text.toString().trim().ifEmpty { "Robot" }
            robot.maxPrice = maxPrice.text.toString().toLongOrNull() ?: 0
            robot.anyMaxPrice = anyPrice.text.toString().toLongOrNull() ?: 0
            robot.tgChats = tg.text.toString().trim()
            if (robot.product.isEmpty()) {
                toast("Önce versiyon seç")
                return@primaryButton
            }
            robot.want.entries.removeAll { it.value.isEmpty() }
            s.saveRobot(robot)
            toast("Kaydedildi")
            finish()
        }, top = 24)

        versionGroup.setOnCheckedStateChangeListener { _, ids ->
            val p = ids.firstOrNull()?.let { products.getOrNull(versionGroup.indexOfChild(versionGroup.findViewById(it))) }
                ?: return@setOnCheckedStateChangeListener
            if (Robot.norm(p.name) != Robot.norm(robot.product)) {
                robot.product = p.name
                robot.want.clear()
            }
            renderOptions(p)
        }
        loadProducts()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this).setMessage("'${robot.name}' silinsin mi?")
            .setPositiveButton("Sil") { _, _ -> s.deleteRobot(robot.id); finish() }
            .setNegativeButton("Vazgeç", null).show()
    }

    private fun loadProducts() {
        val col = robot.collection
        val cached = Cache.products(this, col)
        if (cached != null) showProducts(cached) else {
            versionGroup.removeAllViews()
            optionsBox.removeAllViews()
            optionsBox.card().tv("Seçenekler siteden yükleniyor…", 14f, Palette.MUTED)
        }
        Thread {
            val fresh = runCatching { Api.fetch(col).also { Cache.put(this, col, it) } }.mapCatching { Api.parse(col, it) }
            runOnUiThread {
                if (isFinishing || col != robot.collection) return@runOnUiThread
                fresh.onSuccess { if (it.map { p -> p.name } != products.map { p -> p.name } || cached == null) showProducts(it) }
                    .onFailure { if (products.isEmpty()) toast("Siteye ulaşılamadı: ${it.message}") }
            }
        }.start()
    }

    private fun showProducts(list: List<Product>) {
        products = list
        versionGroup.removeAllViews()
        var sel = list.indexOfFirst { Robot.norm(it.name) == Robot.norm(robot.product) }
        if (sel < 0 && list.isNotEmpty()) sel = 0
        list.forEachIndexed { i, p ->
            versionGroup.addView(filterChip(p.name, false) {}.apply { id = android.view.View.generateViewId() })
            if (i == sel) (versionGroup.getChildAt(i) as com.google.android.material.chip.Chip).isChecked = true
        }
        if (sel < 0) optionsBox.removeAllViews()
    }

    private fun isYesNo(o: Option) = o.values.all { it.value.equals("VAR", true) || it.value.equals("YOK", true) }

    private fun renderOptions(p: Product) {
        optionsBox.removeAllViews()
        for ((group, opts) in p.options.groupBy { it.group }) {
            val c = optionsBox.card()
            c.tv(group, 16f, bold = true)
            opts.forEachIndexed { i, o ->
                if (i > 0) c.divider()
                val chosen = robot.want.getOrPut(o.title) { linkedSetOf() }
                if (isYesNo(o)) {
                    val price = o.values.firstOrNull { it.value.equals("VAR", true) }?.price25 ?: 0
                    if (o.title != group || opts.size > 1) {
                        c.tv(o.title, 14f, bold = true, top = if (i == 0) 8 else 0)
                    }
                    if (price > 0) c.tv("+${Fmt.price(price)}", 12f, Palette.MUTED)
                    val hasNo = o.values.any { it.value.equals("YOK", true) }
                    val labels = if (hasNo) listOf("Fark etmez", "Olsun", "Olmasın") else listOf("Fark etmez", "Olsun")
                    val current = when {
                        chosen.any { it.equals("VAR", true) } && chosen.size == 1 -> 1
                        chosen.any { it.equals("YOK", true) } && chosen.size == 1 && hasNo -> 2
                        else -> 0
                    }
                    c.addView(segmented(labels, current) { idx ->
                        chosen.clear()
                        when (idx) { 1 -> chosen += "VAR"; 2 -> chosen += "YOK" }
                    }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })
                } else {
                    if (o.title != group) c.tv(o.title, 14f, bold = true, top = if (i == 0) 8 else 0)
                    val chips = chipGroup()
                    for (v in o.values) {
                        val extra = if (v.price25 > 0) " +${Fmt.price(v.price25)}" else ""
                        chips.addView(filterChip(Fmt.value(v.value) + extra, chosen.any { Robot.norm(it) == Robot.norm(v.value) }) { on ->
                            chosen.removeAll { Robot.norm(it) == Robot.norm(v.value) }
                            if (on) chosen += v.value
                        })
                    }
                    c.addView(chips, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })
                }
            }
        }
        optionsBox.tv(
            "Bu versiyonda şu an ${p.variants.size} konfigürasyon seçilebiliyor.",
            12f, Palette.MUTED, top = 8
        )
    }
}
