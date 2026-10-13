package com.toggrobot

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Kodla arayuz kurmak icin kucuk yardimcilar (ek kutuphane yok -> kucuk APK). */
open class UiActivity : Activity() {
    fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    fun vbox(pad: Int = 0) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
    }

    fun LinearLayout.header(t: String) = TextView(this@UiActivity).apply {
        text = t; textSize = 18f; setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(18), 0, dp(6))
    }.also { addView(it) }

    fun LinearLayout.text(t: String, size: Float = 14f, color: Int = Color.DKGRAY) = TextView(this@UiActivity).apply {
        text = t; textSize = size; setTextColor(color); setPadding(0, dp(2), 0, dp(2))
    }.also { addView(it) }

    fun LinearLayout.row(vararg views: View) = LinearLayout(this@UiActivity).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { addView(it, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) }
    }.also { addView(it) }

    fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setOnClickListener { onClick() }
    }

    fun LinearLayout.card(): LinearLayout {
        val c = vbox(12)
        c.background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(Color.parseColor("#F2F5F8"))
            setStroke(dp(1), Color.parseColor("#D5DDE5"))
        }
        addView(c, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        return c
    }

    fun LinearLayout.numberField(label: String, value: Long): EditText {
        text(label, 13f)
        return EditText(this@UiActivity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (value > 0) value.toString() else "")
            hint = "boş = kapalı"
        }.also { addView(it) }
    }

    fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
