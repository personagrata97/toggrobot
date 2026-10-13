package com.toggrobot

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.R as M

/** Uygulamanin renk paleti */
object Palette {
    val NAVY = Color.parseColor("#0E1A2B")
    val NAVY2 = Color.parseColor("#1D3557")
    val ACCENT = Color.parseColor("#00A3B4")
    val BG = Color.parseColor("#F3F5F8")
    val TEXT = Color.parseColor("#101828")
    val MUTED = Color.parseColor("#667085")
    val LINE = Color.parseColor("#E4E7EC")
    val GREEN = Color.parseColor("#067647")
    val GREEN_BG = Color.parseColor("#DCFAE6")
    val AMBER = Color.parseColor("#B54708")
    val AMBER_BG = Color.parseColor("#FEF0C7")
    val RED = Color.parseColor("#B42318")
    val RED_BG = Color.parseColor("#FEE4E2")
    val GRAY_BG = Color.parseColor("#F2F4F7")
}

/** Kodla Material arayuz kurmak icin ortak parcalar. */
open class BaseActivity : AppCompatActivity() {
    fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    fun dpf(v: Int) = v * resources.displayMetrics.density

    /**
     * Ust cubuk + kaydirilabilir icerik. Icerik kutusunu dondurur.
     * [actions]: sag ustteki simge butonlari (simge, aciklama, tiklama)
     */
    fun screen(title: String, back: Boolean, vararg actions: Triple<Int, String, () -> Unit>): LinearLayout {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Palette.BG) }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Palette.NAVY)
            setPadding(dp(if (back) 4 else 20), dp(10), dp(8), dp(10))
            minimumHeight = dp(60)
        }
        if (back) bar.addView(iconButton(R.drawable.ic_back, "Geri") { finish() })
        bar.addView(TextView(this).apply {
            text = title; textSize = 20f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(if (back) 4 else 0), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        actions.forEach { (icon, desc, fn) -> bar.addView(iconButton(icon, desc, fn)) }
        root.addView(bar)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }
        root.addView(ScrollView(this).apply { isFillViewport = true; addView(content) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
        return content
    }

    private fun iconButton(icon: Int, desc: String, fn: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        contentDescription = desc
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        background = null
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { fn() }
    }

    fun ViewGroup.tv(t: CharSequence, size: Float = 14f, color: Int = Palette.TEXT, bold: Boolean = false, top: Int = 0) =
        TextView(this@BaseActivity).apply {
            text = t; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(top), 0, 0)
        }.also { addView(it) }

    /** Bolum basligi; istege bagli sag tarafta yazi-buton */
    fun ViewGroup.section(t: String, action: String? = null, onAction: (() -> Unit)? = null) {
        val row = LinearLayout(this@BaseActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(20), 0, dp(4))
        }
        row.addView(TextView(this@BaseActivity).apply {
            text = t.uppercase(); textSize = 12f; letterSpacing = 0.08f
            setTextColor(Palette.MUTED); setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (action != null && onAction != null) row.addView(textButton(action, onAction))
        addView(row)
    }

    fun ViewGroup.card(pad: Int = 16, color: Int = Color.WHITE, stroke: Boolean = true, onClick: (() -> Unit)? = null): LinearLayout {
        val c = MaterialCardView(this@BaseActivity).apply {
            radius = dpf(16)
            cardElevation = 0f
            setCardBackgroundColor(color)
            strokeWidth = if (stroke) dp(1) else 0
            strokeColor = Palette.LINE
            if (onClick != null) { isClickable = true; isFocusable = true; setOnClickListener { onClick() } }
        }
        val inner = LinearLayout(this@BaseActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
        }
        c.addView(inner)
        addView(c, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        return inner
    }

    fun ViewGroup.hrow(gravity: Int = Gravity.CENTER_VERTICAL, top: Int = 0) = LinearLayout(this@BaseActivity).apply {
        orientation = LinearLayout.HORIZONTAL; this.gravity = gravity; setPadding(0, dp(top), 0, 0)
    }.also { addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }

    fun ViewGroup.divider() = View(this@BaseActivity).apply { setBackgroundColor(Palette.LINE) }
        .also { addView(it, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(12) }) }

    /** Kucuk renkli etiket */
    fun badge(t: String, fg: Int, bg: Int) = TextView(this).apply {
        text = t; textSize = 12f; setTextColor(fg); setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = GradientDrawable().apply { cornerRadius = dpf(20); setColor(bg) }
    }

    fun primaryButton(t: String, fn: () -> Unit) = MaterialButton(this).apply {
        text = t; isAllCaps = false; textSize = 15f; cornerRadius = dp(14)
        minHeight = dp(52)
        setOnClickListener { fn() }
    }

    fun outlinedButton(t: String, fn: () -> Unit) = MaterialButton(this, null, M.attr.materialButtonOutlinedStyle).apply {
        text = t; isAllCaps = false; cornerRadius = dp(14); minHeight = dp(48)
        setOnClickListener { fn() }
    }

    fun textButton(t: String, fn: () -> Unit) = MaterialButton(this, null, M.attr.borderlessButtonStyle).apply {
        text = t; isAllCaps = false
        setOnClickListener { fn() }
    }

    fun ViewGroup.fullWidth(v: View, top: Int = 10) =
        addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) })

    /** Iki butonu yan yana koyar */
    fun ViewGroup.buttonRow(a: View, b: View, top: Int = 10) {
        val row = hrow(top = top)
        row.addView(a, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(5) })
        row.addView(b, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(5) })
    }

    fun ViewGroup.field(label: String, value: String, numeric: Boolean = false, suffix: String? = null, helper: String? = null): TextInputEditText {
        val til = TextInputLayout(this@BaseActivity, null, M.attr.textInputOutlinedStyle).apply {
            hint = label
            setBoxCornerRadii(dpf(12), dpf(12), dpf(12), dpf(12))
            if (suffix != null) suffixText = suffix
            if (helper != null) helperText = helper
        }
        val et = TextInputEditText(til.context).apply {
            setText(value)
            if (numeric) inputType = InputType.TYPE_CLASS_NUMBER else setSingleLine()
        }
        til.addView(et)
        addView(til, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        return et
    }

    /** Baslik + aciklama + sagda acma/kapama */
    fun ViewGroup.switchRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit): MaterialSwitch {
        val row = hrow(top = 6)
        val texts = LinearLayout(this@BaseActivity).apply { orientation = LinearLayout.VERTICAL }
        texts.tv(title, 15f, bold = true)
        if (desc != null) texts.tv(desc, 13f, Palette.MUTED)
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val sw = MaterialSwitch(this@BaseActivity).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        row.addView(sw)
        return sw
    }

    fun chipGroup(): ChipGroup = ChipGroup(this).apply { chipSpacingHorizontal = dp(8); chipSpacingVertical = dp(4) }

    /** Secilebilir (filtre) cip */
    fun filterChip(t: String, checked: Boolean, onChange: (Boolean) -> Unit) = Chip(this).apply {
        setChipDrawable(ChipDrawable.createFromAttributes(this@BaseActivity, null, 0, M.style.Widget_Material3_Chip_Filter))
        text = t
        isCheckable = true
        isChecked = checked
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    /** Sadece gosterim icin kucuk cip */
    fun infoChip(t: String, fg: Int = Palette.TEXT, bg: Int = Palette.GRAY_BG) = Chip(this).apply {
        setChipDrawable(ChipDrawable.createFromAttributes(this@BaseActivity, null, 0, M.style.Widget_Material3_Chip_Assist))
        text = t; textSize = 12f
        isClickable = false; isCheckable = false
        chipBackgroundColor = ColorStateList.valueOf(bg)
        setTextColor(fg)
        chipStrokeWidth = 0f
        chipMinHeight = dpf(28)
        ensureMinTouchTargetSize = false
    }

    /** Tek secimli bolumlu dugme grubu. Secilen indeksi bildirir. */
    fun segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit): MaterialButtonToggleGroup {
        val g = MaterialButtonToggleGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        val ids = labels.map { label ->
            val b = MaterialButton(this, null, M.attr.materialButtonOutlinedStyle).apply {
                id = View.generateViewId(); text = label; isAllCaps = false; textSize = 13f
                setPadding(dp(4), 0, dp(4), 0)
            }
            g.addView(b, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            b.id
        }
        g.check(ids[selected.coerceIn(0, ids.size - 1)])
        g.addOnButtonCheckedListener { _, id, isChecked -> if (isChecked) onSelect(ids.indexOf(id)) }
        return g
    }

    fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(h)) }

    @Suppress("unused")
    fun frame() = FrameLayout(this)
}
