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
    val NAVY = Color.parseColor("#0B1F3A")
    val NAVY2 = Color.parseColor("#14406B")
    val ACCENT = Color.parseColor("#00A3B4")
    val ACCENT_SOFT = Color.parseColor("#E0F6F8")
    val BG = Color.parseColor("#F5F6F8")
    val TEXT = Color.parseColor("#0B1220")
    val MUTED = Color.parseColor("#6B7280")
    val LINE = Color.parseColor("#ECEEF2")
    val GREEN = Color.parseColor("#067647")
    val GREEN_BG = Color.parseColor("#DCFAE6")
    val AMBER = Color.parseColor("#B54708")
    val AMBER_BG = Color.parseColor("#FEF0C7")
    val RED = Color.parseColor("#B42318")
    val RED_BG = Color.parseColor("#FEE4E2")
    val GRAY_BG = Color.parseColor("#F1F3F6")

    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val BOLD: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
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
        window.statusBarColor = Palette.BG
        window.navigationBarColor = Palette.BG
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        frameRoot = FrameLayout(this).apply { setBackgroundColor(Palette.BG) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        frameRoot.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(if (back) 8 else 22), dp(14), dp(12), dp(6))
        }
        if (back) bar.addView(iconButton(R.drawable.ic_back, "Geri") { finish() })
        bar.addView(TextView(this).apply {
            text = title
            textSize = if (back) 22f else 28f
            setTextColor(Palette.TEXT); typeface = Palette.BOLD
            letterSpacing = -0.01f
            setPadding(dp(if (back) 6 else 0), 0, 0, 0)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        actions.forEach { (icon, desc, fn) -> bar.addView(iconButton(icon, desc, fn)) }
        column.addView(bar)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(110))
        }
        column.addView(ScrollView(this).apply { isFillViewport = true; clipToPadding = false; addView(content) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(frameRoot)
        return content
    }

    private lateinit var frameRoot: FrameLayout

    /** Sag altta yuzen buton */
    fun addFab(text: String, onClick: () -> Unit) {
        val fab = com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton(this).apply {
            this.text = text
            isAllCaps = false
            setIconResource(R.drawable.ic_add)
            iconTint = ColorStateList.valueOf(Color.WHITE)
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Palette.NAVY)
            setOnClickListener { onClick() }
        }
        frameRoot.addView(fab, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, dp(20), dp(28))
        })
    }

    private fun iconButton(icon: Int, desc: String, fn: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        contentDescription = desc
        imageTintList = ColorStateList.valueOf(Palette.TEXT)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
        setPadding(dp(11), dp(11), dp(11), dp(11))
        layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(8) }
        setOnClickListener { fn() }
    }

    fun ViewGroup.tv(t: CharSequence, size: Float = 14f, color: Int = Palette.TEXT, bold: Boolean = false, top: Int = 0) =
        TextView(this@BaseActivity).apply {
            text = t; textSize = size; setTextColor(color)
            if (bold) typeface = Palette.BOLD
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(top), 0, 0)
        }.also { addView(it) }

    /** Bolum basligi; istege bagli sag tarafta yazi-buton */
    fun ViewGroup.section(t: String, action: String? = null, onAction: (() -> Unit)? = null) {
        val row = LinearLayout(this@BaseActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(26), 0, dp(2))
        }
        // Buyuk harfe cevirmiyoruz: telefonun diline gore "i/İ" bozulmasin
        row.addView(TextView(this@BaseActivity).apply {
            text = t; textSize = 19f
            setTextColor(Palette.TEXT); typeface = Palette.BOLD
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (action != null && onAction != null) row.addView(textButton(action, onAction))
        addView(row)
    }

    fun ViewGroup.card(pad: Int = 16, color: Int = Color.WHITE, @Suppress("UNUSED_PARAMETER") stroke: Boolean = true, onClick: (() -> Unit)? = null): LinearLayout {
        val c = MaterialCardView(this@BaseActivity).apply {
            radius = dpf(22)
            cardElevation = 0f
            setCardBackgroundColor(color)
            strokeWidth = 0
            if (onClick != null) { isClickable = true; isFocusable = true; setOnClickListener { onClick() } }
        }
        val inner = LinearLayout(this@BaseActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(pad + 2), dp(pad), dp(pad + 2), dp(pad))
        }
        c.addView(inner)
        addView(c, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })
        return inner
    }

    fun ViewGroup.hrow(gravity: Int = Gravity.CENTER_VERTICAL, top: Int = 0) = LinearLayout(this@BaseActivity).apply {
        orientation = LinearLayout.HORIZONTAL; this.gravity = gravity; setPadding(0, dp(top), 0, 0)
    }.also { addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }

    fun ViewGroup.divider() = View(this@BaseActivity).apply { setBackgroundColor(Palette.LINE) }
        .also { addView(it, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(12) }) }

    /** Kucuk renkli etiket */
    fun badge(t: String, fg: Int, bg: Int) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(fg); typeface = Palette.MEDIUM
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = GradientDrawable().apply { cornerRadius = dpf(20); setColor(bg) }
    }

    fun primaryButton(t: String, fn: () -> Unit) = MaterialButton(this).apply {
        text = t; isAllCaps = false; textSize = 15f; cornerRadius = dp(28)
        minHeight = dp(54); typeface = Palette.MEDIUM
        setOnClickListener { fn() }
    }

    fun outlinedButton(t: String, fn: () -> Unit) = MaterialButton(this, null, M.attr.materialButtonOutlinedStyle).apply {
        text = t; isAllCaps = false; cornerRadius = dp(28); minHeight = dp(50); typeface = Palette.MEDIUM
        setOnClickListener { fn() }
    }

    fun textButton(t: String, fn: () -> Unit) = MaterialButton(this, null, M.attr.borderlessButtonStyle).apply {
        text = t; isAllCaps = false; setTextColor(Palette.ACCENT); typeface = Palette.MEDIUM; textSize = 15f
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
        texts.tv(title, 16f).typeface = Palette.MEDIUM
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
        text = t; textSize = 12.5f
        isClickable = false; isCheckable = false
        typeface = Palette.MEDIUM
        chipBackgroundColor = ColorStateList.valueOf(bg)
        setTextColor(fg)
        chipStrokeWidth = 0f
        chipMinHeight = dpf(28)
        setEnsureMinTouchTargetSize(false)
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
