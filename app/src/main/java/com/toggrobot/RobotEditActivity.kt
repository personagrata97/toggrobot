package com.toggrobot

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

/** Robot ekleme / duzenleme. Secenekler sitenin gercek verisinden listelenir. */
class RobotEditActivity : UiActivity() {

    private lateinit var s: Settings
    private lateinit var robot: Robot
    private lateinit var optionsBox: LinearLayout
    private lateinit var productSpinner: Spinner
    private val collections = Api.COLLECTIONS.keys.toList()
    private var products: List<Product> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = Settings(this)
        val id = intent.getStringExtra("id")
        robot = s.robots.firstOrNull { it.id == id }?.let { Robot.fromJson(it.toJson()) }
            ?: Robot(Robot.newId(), "Robot ${s.robots.size + 1}", true, "t10f-configurator", "", linkedMapOf(), 0, 0)
        title = if (id == null) "Yeni robot" else "Robotu düzenle"

        val root = vbox(14)
        root.text("Robot adı", 13f)
        val name = EditText(this).apply { setText(robot.name); setSingleLine() }
        root.addView(name)

        root.text("Model", 13f)
        val modelSpinner = Spinner(this)
        modelSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, collections.map { Api.COLLECTIONS[it] })
        modelSpinner.setSelection(collections.indexOf(robot.collection).coerceAtLeast(0))
        root.addView(modelSpinner)

        root.text("Versiyon", 13f)
        productSpinner = Spinner(this)
        root.addView(productSpinner)

        root.header("Seçenekler")
        root.text(
            "İşaretlediklerin şart olur. Bir seçenekte hiçbir şey işaretlemezsen 'fark etmez' sayılır. " +
                "Aynı seçenekte birden fazla işaretlersen biri olsun yeter (ör. Renk: Kula veya Seyhan). " +
                "Gelmesini istemediğin donanım için 'Yok'u işaretle (ör. Meridian: Yok).", 12f
        )
        optionsBox = vbox()
        root.addView(optionsBox)

        root.header("Fiyat")
        val maxPrice = root.numberField("Anahtar teslim fiyat şunun ALTINDAYSA alarm (₺)", robot.maxPrice)
        val anyPrice = root.numberField(
            "Ek alarm: seçenekler tutmasa da bu versiyonun herhangi bir konfigürasyonu şunun altındaysa haber ver (₺)",
            robot.anyMaxPrice
        )

        root.row(
            button("💾 Kaydet") {
                robot.name = name.text.toString().trim().ifEmpty { "Robot" }
                robot.maxPrice = maxPrice.text.toString().toLongOrNull() ?: 0
                robot.anyMaxPrice = anyPrice.text.toString().toLongOrNull() ?: 0
                if (robot.product.isEmpty()) {
                    toast("Önce versiyon seç (veri yüklenmediyse internet bağlantısını kontrol et)")
                    return@button
                }
                robot.want.entries.removeAll { it.value.isEmpty() }
                s.saveRobot(robot)
                toast("Kaydedildi")
                finish()
            },
            button("Vazgeç") { finish() },
        )
        setContentView(ScrollView(this).apply { addView(root) })

        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val col = collections[pos]
                if (col != robot.collection) {
                    robot.collection = col
                    robot.product = ""
                    robot.want.clear()
                }
                loadProducts(col)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        productSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val p = products.getOrNull(pos) ?: return
                if (!Robot.norm(p.name).equals(Robot.norm(robot.product))) {
                    robot.product = p.name
                    robot.want.clear()
                }
                renderOptions(p)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadProducts(col: String) {
        Cache.products(this, col)?.let { showProducts(it) }
        if (Cache.products(this, col) == null) {
            optionsBox.removeAllViews()
            optionsBox.text("Siteden seçenekler yükleniyor...")
        }
        Thread {
            val fresh = runCatching { Api.fetch(col).also { Cache.put(this, col, it) } }
                .mapCatching { Api.parse(col, it) }
            runOnUiThread {
                if (isFinishing || col != robot.collection) return@runOnUiThread
                fresh.onSuccess { if (it.map { p -> p.name } != products.map { p -> p.name }) showProducts(it) }
                    .onFailure { if (products.isEmpty()) toast("Veri alınamadı: ${it.message}") }
            }
        }.start()
    }

    private fun showProducts(list: List<Product>) {
        products = list
        productSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, list.map { it.name })
        val i = list.indexOfFirst { Robot.norm(it.name) == Robot.norm(robot.product) }
        if (i >= 0) productSpinner.setSelection(i) else if (list.isNotEmpty()) productSpinner.setSelection(0)
    }

    private fun renderOptions(p: Product) {
        optionsBox.removeAllViews()
        for ((group, opts) in p.options.groupBy { it.group }) {
            optionsBox.addView(TextView(this).apply {
                text = group; textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#0E2A47"))
                setPadding(0, dp(10), 0, 0)
            })
            for (o in opts) {
                val c = optionsBox.card()
                if (o.title != group) c.addView(TextView(this).apply { text = o.title; setTypeface(null, Typeface.BOLD) })
                val chosen = robot.want.getOrPut(o.title) { linkedSetOf() }
                for (v in o.values) {
                    val extra = if (v.price25 > 0) "  (+${Fmt.price(v.price25)})" else ""
                    c.addView(CheckBox(this).apply {
                        text = Fmt.value(v.value) + extra
                        isChecked = chosen.any { Robot.norm(it) == Robot.norm(v.value) }
                        setOnCheckedChangeListener { _, on ->
                            chosen.removeAll { Robot.norm(it) == Robot.norm(v.value) }
                            if (on) chosen += v.value
                        }
                    })
                }
                if (o.values.size == 1) c.text("Bu versiyonda tek seçenek var.", 11f, Color.GRAY)
            }
        }
        val avail = p.variants.size
        optionsBox.text("Şu an bu versiyonda $avail seçilebilir konfigürasyon var.", 12f, Color.GRAY)
    }
}
