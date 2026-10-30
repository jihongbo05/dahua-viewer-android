package com.hb.dahuaviewer
import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class PreviewActivity : AppCompatActivity() {
  private lateinit var tile: Tile
  private lateinit var videoBox: Ratio
  private lateinit var below: View
  private lateinit var topBar: View
  private lateinit var title: TextView
  private lateinit var hdLabel: TextView
  private var ch = 1; private var sub = 0
  private lateinit var strip: RecyclerView
  private lateinit var recLabel: TextView
  private var recStart = 0L
  private val hh = android.os.Handler(android.os.Looper.getMainLooper())
  private fun recording() = tile.recPath != null
  private fun warnData() { if (sub == 0 && Net.cellular(this)) Toast.makeText(this, "当前为移动数据，高清主码流流量很大，建议切换为流畅", Toast.LENGTH_LONG).show() }
  private fun startRec() {
    val f = Saver.tempVideo(this, "通道${ch}_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date()) + ".mp4")
    tile.startRecord(f.path); recStart = System.currentTimeMillis(); tickRec()
    snack("开始录像，再点一次停止")
  }
  private fun tickRec() {
    if (!recording()) { recLabel.text = "录像"; recLabel.setTextColor(TEXT1); if (::ovRec.isInitialized) ovRec.text = "录像"; return }
    val s = (System.currentTimeMillis() - recStart) / 1000
    recLabel.text = "%02d:%02d".format(s / 60, s % 60); recLabel.setTextColor(ERR); ovRec.text = "● " + recLabel.text
    hh.postDelayed({ tickRec() }, 1000)
  }
  private fun stopRec(restart: Boolean = true) {
    val path = if (restart) tile.stopRecord() else tile.recPath.also { tile.stop(); tile.stopRecordSilently() }
    tickRec()
    if (path != null) hh.postDelayed({ Thread { val ok = Saver.video(this, File(path))
      runOnUiThread { Toast.makeText(this, if (ok) "录像已保存到 相册/Movies/大华监控" else "录像保存失败（文件为空）", Toast.LENGTH_LONG).show() } }.start() }, 800)
  }

  override fun onCreate(b: Bundle?) {
    initTheme(this); super.onCreate(b)
    ch = b?.getInt("ch") ?: intent.getIntExtra("ch", 1); sub = b?.getInt("sub") ?: Cfg.lastSub(this)
    if (Cfg.flag(this, "keep", true)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
    val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.BLACK); setPadding(dp(4), dp(4), dp(16), dp(4)) }
    bar.addView(ImageButton(this).apply { setImageResource(R.drawable.ic_back); setColorFilter(Color.WHITE); setBackgroundColor(0); setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(48), dp(48)))
    title = tv("", 17f, Color.WHITE, true); bar.addView(title); topBar = bar
    root.addView(bar)
    videoBox = Ratio(this).apply { setBackgroundColor(Color.BLACK) }
    tile = Tile(this, showLabel = false).apply { showInfo = true; zoomable = true }
    videoBox.addView(tile, FrameLayout.LayoutParams(-1, -1))
    body = LinearLayout(this); side = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(SURFACE) }
    mainCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    body.addView(side, LinearLayout.LayoutParams(dp(280), -1)); body.addView(mainCol, LinearLayout.LayoutParams(0, -1, 1f))
    root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
    mainCol.addView(videoBox, LinearLayout.LayoutParams(-1, -2))
    val bl = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val ctl = LinearLayout(this).apply { setPadding(0, dp(20), 0, dp(12)); gravity = Gravity.CENTER; setBackgroundColor(SURFACE) }
    fun rb(res: Int, label: String, f: () -> Unit): TextView {
      val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setOnClickListener { f() } }
      val circle = FrameLayout(this).apply { background = round(soft(ACCENT, 0x22), 28) }
      circle.addView(icon(res, ACCENT), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
      col.addView(circle, LinearLayout.LayoutParams(dp(56), dp(56)))
      val t = tv(label, 12f, TEXT1).apply { setPadding(0, dp(6), 0, 0) }; col.addView(t)
      ctl.addView(col, LinearLayout.LayoutParams(0, -2, 1f)); return t
    }
    hdLabel = rb(R.drawable.ic_hd, "") { toggleHd() }
    rb(R.drawable.ic_shot, "截图") { shot() }
    recLabel = rb(R.drawable.ic_rec, "录像") { if (recording()) stopRec() else startRec() }
    rb(R.drawable.ic_history, "回放") { startActivity(android.content.Intent(this, PlaybackActivity::class.java).putExtra("ch", ch)) }
    rb(R.drawable.ic_fullscreen, "全屏") { enterFull() }
    // 横屏浮层控制条
    ov = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x99000000.toInt(), 0)); setPadding(dp(8), dp(8), dp(16), dp(24)); visibility = View.GONE }
    ov.addView(ImageButton(this).apply { setImageResource(R.drawable.ic_back); setColorFilter(Color.WHITE); setBackgroundColor(0); setOnClickListener { exitFull() } }, LinearLayout.LayoutParams(dp(48), dp(48)))
    ovTitle = tv("", 15f, Color.WHITE, true); ov.addView(ovTitle, LinearLayout.LayoutParams(0, -2, 1f))
    fun ob(t: String, f: () -> Unit) = TextView(this).apply { text = t; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; background = round(0x33FFFFFF, 16)
      setPadding(dp(14), 0, dp(14), 0); setOnClickListener { f(); showOv() } }.also { ov.addView(it, LinearLayout.LayoutParams(-2, dp(36)).apply { marginStart = dp(8) }) }
    ovHd = ob("") { toggleHd() }; ob("截图") { shot() }; ovRec = ob("录像") { if (recording()) stopRec() else startRec() }; ob("退出全屏") { exitFull() }
    videoBox.addView(ov, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
    bl.addView(ctl)
    stripTitle = tv("切换摄像头", 15f, TEXT1, true).apply { setPadding(dp(16), dp(16), dp(16), dp(8)) }; bl.addView(stripTitle)
    strip = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@PreviewActivity, LinearLayoutManager.HORIZONTAL, false); setPadding(dp(8), 0, dp(8), 0); clipToPadding = false }
    strip.adapter = StripAdapter()
    bl.addView(strip, LinearLayout.LayoutParams(-1, -2))
    below = bl
    mainCol.addView(bl, LinearLayout.LayoutParams(-1, 0, 1f))
    setContentView(root)
    // 统一手势：单击 显示/隐藏控制条，双击 放大/还原，双指缩放，左右滑 切换相邻通道
    tile.onTap = { if (full()) { if (ov.visibility == View.VISIBLE) ov.visibility = View.GONE else showOv() } else tile.info.visibility = if (tile.info.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
    tile.onDouble = { tile.toggleZoom() }
    tile.onSwipe = { d -> val o = order(); val i = o.indexOf(ch).coerceAtLeast(0); val n = o[(i + d + o.size) % o.size]; switchTo(n); snack("${Cfg.name(this, n)} · 通道$n") }
    applyOrientation(); updHd(); updTitle()
  }
  override fun onSaveInstanceState(o: Bundle) { super.onSaveInstanceState(o); o.putInt("ch", ch); o.putInt("sub", sub) }
  private fun isLand() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
  private lateinit var body: LinearLayout; private lateinit var side: LinearLayout; private lateinit var mainCol: LinearLayout; private lateinit var stripTitle: TextView
  private var forceFull = false; private var paneWide: Boolean? = null
  /** 全屏：小屏横屏，或大屏下手动全屏 */
  private fun full() = forceFull || (isLand() && !wide())
  private fun enterFull() { if (wide()) { forceFull = true; applyOrientation() } else requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
  private fun exitFull() { if (forceFull) { forceFull = false; applyOrientation() } else requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
  /** 大屏双栏：左侧通道列表 + 右侧画面；小屏：底部横向条 */
  private fun applyPanes() {
    val w = wide(); if (paneWide == w) return; paneWide = w
    (stripTitle.parent as? ViewGroup)?.removeView(stripTitle); (strip.parent as? ViewGroup)?.removeView(strip)
    if (w) { side.addView(stripTitle); side.addView(strip, LinearLayout.LayoutParams(-1, 0, 1f)); strip.layoutManager = LinearLayoutManager(this) }
    else { (below as LinearLayout).addView(stripTitle); (below as LinearLayout).addView(strip, LinearLayout.LayoutParams(-1, -2)); strip.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false) }
    strip.adapter = StripAdapter()
  }
  private lateinit var ov: LinearLayout; private lateinit var ovTitle: TextView; private lateinit var ovHd: TextView; private lateinit var ovRec: TextView
  private fun showOv() { ov.visibility = View.VISIBLE; ov.alpha = 0f; ov.animate().alpha(1f).setDuration(150).start(); hh.removeCallbacks(hideOv); hh.postDelayed(hideOv, 4000) }
  private val hideOv = Runnable { ov.visibility = View.GONE }
  private fun order() = Cfg.favs(this).sorted() + (1..Cfg.COUNT).filter { !Cfg.isFav(this, it) }
  private fun toggleHd() { if (recording()) stopRec(); sub = 1 - sub; Cfg.setLastSub(this, sub); updHd(); warnData(); tile.play(ch, sub); snack(if (sub == 0) "已切到高清（记住选择）" else "已切到流畅（记住选择）") }
  private fun updHd() { hdLabel.text = if (sub == 0) "高清" else "流畅"; if (::ovHd.isInitialized) ovHd.text = hdLabel.text }
  private fun updTitle() { title.text = "${Cfg.name(this, ch)} · 通道$ch"; ovTitle.text = title.text }
  override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); applyOrientation() }
  @Suppress("DEPRECATION")
  private fun applyOrientation() {
    applyPanes()
    val land = full()
    side.visibility = if (wide() && !land) View.VISIBLE else View.GONE
    videoBox.enabledRatio = !land
    videoBox.maxH = if (wide()) (resources.displayMetrics.heightPixels * 0.62f).toInt() else Int.MAX_VALUE
    videoBox.layoutParams = LinearLayout.LayoutParams(-1, if (land) -1 else -2)
    below.visibility = if (land) View.GONE else View.VISIBLE
    if (land) showOv() else ov.visibility = View.GONE
    topBar.visibility = if (land) View.GONE else View.VISIBLE
    window.decorView.systemUiVisibility = if (land) View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else 0
  }
  override fun onBackPressed() {
    if (full()) { exitFull(); return }
    @Suppress("DEPRECATION") super.onBackPressed()
  }
  override fun onResume() { super.onResume(); warnData(); tile.play(ch, sub) }
  override fun onPause() { super.onPause(); if (recording()) stopRec(false); tile.stop() }
  private fun switchTo(n: Int) { if (recording()) stopRec(); ch = n; tile.resetZoom(); updTitle(); tile.play(ch, sub); strip.adapter?.notifyDataSetChanged() }

  inner class StripAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val order get() = Cfg.favs(this@PreviewActivity).sorted() + (1..Cfg.COUNT).filter { !Cfg.isFav(this@PreviewActivity, it) }
    override fun getItemCount() = Cfg.COUNT
    override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder {
      val c = card().apply { layoutParams = RecyclerView.LayoutParams(if (paneWide == true) -1 else dp(140), -2).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) } }
      val col = LinearLayout(this@PreviewActivity).apply { orientation = LinearLayout.VERTICAL }
      val r = Ratio(this@PreviewActivity).apply { setBackgroundColor(FILL) }
      r.addView(icon(R.drawable.ic_videocam, 0xFFB0B6BE.toInt(), 28), FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
      r.addView(ImageView(this@PreviewActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP; tag = "img" }, FrameLayout.LayoutParams(-1, -1))
      col.addView(r); col.addView(tv("", 12f, TEXT1).apply { maxLines = 1; tag = "name"; setPadding(dp(8), dp(6), dp(8), dp(8)) })
      c.addView(col); return object : RecyclerView.ViewHolder(c) {}
    }
    override fun onBindViewHolder(vh: RecyclerView.ViewHolder, p: Int) {
      val n = order[p]; val v = vh.itemView as com.google.android.material.card.MaterialCardView
      v.findViewWithTag<ImageView>("img").setImageBitmap(Thumbs.load(this@PreviewActivity, n))
      v.findViewWithTag<TextView>("name").apply { text = "$n ${Cfg.name(this@PreviewActivity, n)}"; setTextColor(if (n == ch) ACCENT else TEXT1) }
      v.strokeColor = ACCENT; v.strokeWidth = if (n == ch) dp(2) else 0
      v.setOnClickListener { switchTo(n) }
    }
  }

  private fun shot() {
    val bmp: Bitmap = tile.snapshot() ?: run { snack("还没出画面，稍等再截"); return }
    val name = "通道${ch}_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date()) + ".jpg"
    try {
      Saver.image(this, bmp, name)
      snack("截图已保存到相册")
    } catch (e: Exception) { Toast.makeText(this, "截图失败：${e.message}", Toast.LENGTH_LONG).show() }
  }
}
