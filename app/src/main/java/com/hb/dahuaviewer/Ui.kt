package com.hb.dahuaviewer
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

const val ACCENT = 0xFFFF6A00.toInt()
const val TEXT1 = 0xFF1F2329.toInt()
const val TEXT2 = 0xFF8A9099.toInt()
fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
fun Context.tv(t: String, sp: Float = 14f, color: Int = TEXT1, bold: Boolean = false) = TextView(this).apply {
  text = t; textSize = sp; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD }
fun Context.icon(res: Int, tint: Int, size: Int = 24) = ImageView(this).apply {
  setImageResource(res); setColorFilter(tint); layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)) }
fun Context.card(): MaterialCardView = MaterialCardView(this).apply {
  radius = dp(16).toFloat(); cardElevation = dp(2).toFloat(); setCardBackgroundColor(Color.WHITE); strokeWidth = 0; useCompatPadding = true }
fun Context.dot(color: Int) = View(this).apply {
  background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }; layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)) }
fun Context.round(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }
fun Context.drw(res: Int) = ContextCompat.getDrawable(this, res)!!

/** 16:9 自适应容器 */
class Ratio(c: Context) : FrameLayout(c) {
  var enabledRatio = true
  override fun onMeasure(w: Int, h: Int) {
    if (!enabledRatio) return super.onMeasure(w, h)
    val width = MeasureSpec.getSize(w)
    super.onMeasure(w, MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY))
  }
}
