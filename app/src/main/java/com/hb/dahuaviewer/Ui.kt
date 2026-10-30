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
var TEXT1 = 0xFF1C1C1E.toInt(); var TEXT2 = 0xFF8E8E93.toInt()
var SURFACE = Color.WHITE; var BG = 0xFFF2F2F7.toInt(); var LINE = 0xFFE5E5EA.toInt(); var FILL = 0xFFF2F2F7.toInt()
const val OK = 0xFF34C759.toInt(); const val ERR = 0xFFFF3B30.toInt(); const val WARN = 0xFFFF9F0A.toInt()
var NIGHT = false
/** 按系统深色模式刷新配色（苹果风格系统色） */
fun initTheme(c: Context) {
  NIGHT = (c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
  if (NIGHT) { TEXT1 = 0xFFF2F2F7.toInt(); TEXT2 = 0xFF98989F.toInt(); SURFACE = 0xFF1C1C1E.toInt(); BG = 0xFF000000.toInt(); LINE = 0xFF38383A.toInt(); FILL = 0xFF2C2C2E.toInt() }
  else { TEXT1 = 0xFF1C1C1E.toInt(); TEXT2 = 0xFF8E8E93.toInt(); SURFACE = Color.WHITE; BG = 0xFFF2F2F7.toInt(); LINE = 0xFFE5E5EA.toInt(); FILL = 0xFFF2F2F7.toInt() }
}
fun soft(color: Int, alpha: Int = 0x24) = (color and 0x00FFFFFF) or (alpha shl 24)
/** 状态胶囊：圆点 + 文字 */
fun Context.pill(t: String, color: Int) = TextView(this).apply {
  text = "●  $t"; textSize = 12f; setTextColor(color); typeface = Typeface.DEFAULT_BOLD
  background = round(soft(color), 12); setPadding(dp(10), dp(4), dp(10), dp(4)) }
fun TextView.setPill(t: String, color: Int) { text = "●  $t"; setTextColor(color); background = context.round(soft(color), 12) }
/** 大屏判断（折叠屏内屏/平板/宽多窗口）：最短边 ≥ 600dp 或当前宽 ≥ 840dp */
fun Context.wide() = resources.configuration.let { it.smallestScreenWidthDp >= 600 || it.screenWidthDp >= 840 }
fun Context.widthDp() = resources.configuration.screenWidthDp
fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
fun Context.tv(t: String, sp: Float = 14f, color: Int = TEXT1, bold: Boolean = false) = TextView(this).apply {
  text = t; textSize = sp; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD }
fun Context.icon(res: Int, tint: Int, size: Int = 24) = ImageView(this).apply {
  setImageResource(res); setColorFilter(tint); layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)) }
fun Context.card(): MaterialCardView = MaterialCardView(this).apply {
  radius = dp(14).toFloat(); cardElevation = 0f; setCardBackgroundColor(SURFACE); strokeWidth = if (NIGHT) 0 else dp(1) / 2; strokeColor = LINE; useCompatPadding = false }
fun Context.dot(color: Int) = View(this).apply {
  background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }; layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)) }
fun Context.round(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }
fun Context.drw(res: Int) = ContextCompat.getDrawable(this, res)!!

/** 16:9 自适应容器 */
class Ratio(c: Context) : FrameLayout(c) {
  var enabledRatio = true
  /** 高度上限（大屏/折叠屏避免视频过高；画面由播放器等比缩放，不拉伸） */
  var maxH = Int.MAX_VALUE
  override fun onMeasure(w: Int, h: Int) {
    if (!enabledRatio) return super.onMeasure(w, h)
    val width = MeasureSpec.getSize(w)
    super.onMeasure(w, MeasureSpec.makeMeasureSpec(minOf(width * 9 / 16, maxH), MeasureSpec.EXACTLY))
  }
}

/** 统一操作反馈：Snackbar（底部，短暂） */
fun View.snack(msg: String, long: Boolean = false) = com.google.android.material.snackbar.Snackbar.make(this, msg, if (long) com.google.android.material.snackbar.Snackbar.LENGTH_LONG else com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
fun android.app.Activity.snack(msg: String, long: Boolean = false) = findViewById<View>(android.R.id.content).snack(msg, long)
