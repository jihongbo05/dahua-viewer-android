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
    Toast.makeText(this, "开始录像", Toast.LENGTH_SHORT).show()
  }
  private fun tickRec() {
    if (!recording()) { recLabel.text = "录像"; recLabel.setTextColor(TEXT1); return }
    val s = (System.currentTimeMillis() - recStart) / 1000
    recLabel.text = "%02d:%02d".format(s / 60, s % 60); recLabel.setTextColor(0xFFD93025.toInt())
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
    root.addView(videoBox, LinearLayout.LayoutParams(-1, -2))
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
    rb(R.drawable.ic_shot, "截图") { shot() }
    hdLabel = rb(R.drawable.ic_hd, "") { if (recording()) stopRec(); sub = 1 - sub; Cfg.setLastSub(this, sub); updHd(); warnData(); tile.play(ch, sub) }
    recLabel = rb(R.drawable.ic_rec, "录像") { if (recording()) stopRec() else startRec() }
    rb(R.drawable.ic_history, "回放") { startActivity(android.content.Intent(this, PlaybackActivity::class.java).putExtra("ch", ch)) }
    rb(R.drawable.ic_fullscreen, "全屏") { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
    bl.addView(ctl)
    bl.addView(tv("切换摄像头", 15f, TEXT1, true).apply { setPadding(dp(16), dp(16), dp(16), dp(8)) })
    strip = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@PreviewActivity, LinearLayoutManager.HORIZONTAL, false); setPadding(dp(8), 0, dp(8), 0); clipToPadding = false }
    strip.adapter = StripAdapter()
    bl.addView(strip, LinearLayout.LayoutParams(-1, -2))
    below = bl
    root.addView(bl, LinearLayout.LayoutParams(-1, 0, 1f))
    setContentView(root)
    tile.onTap = { if (isLand()) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    tile.onDouble = { if (tile.zoomed) tile.resetZoom() else requestedOrientation = if (isLand()) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
    applyOrientation(); updHd(); updTitle()
  }
  override fun onSaveInstanceState(o: Bundle) { super.onSaveInstanceState(o); o.putInt("ch", ch); o.putInt("sub", sub) }
  private fun isLand() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
  private fun updHd() { hdLabel.text = if (sub == 0) "高清" else "流畅" }
  private fun updTitle() { title.text = "${Cfg.name(this, ch)} · 通道$ch" }
  override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); applyOrientation() }
  @Suppress("DEPRECATION")
  private fun applyOrientation() {
    val land = isLand()
    videoBox.enabledRatio = !land
    videoBox.layoutParams = LinearLayout.LayoutParams(-1, if (land) -1 else -2)
    below.visibility = if (land) View.GONE else View.VISIBLE
    topBar.visibility = if (land) View.GONE else View.VISIBLE
    window.decorView.systemUiVisibility = if (land) View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else 0
  }
  override fun onBackPressed() {
    if (isLand()) { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT; return }
    @Suppress("DEPRECATION") super.onBackPressed()
  }
  override fun onResume() { super.onResume(); warnData(); tile.play(ch, sub) }
  override fun onPause() { super.onPause(); if (recording()) stopRec(false); tile.stop() }
  private fun switchTo(n: Int) { if (recording()) stopRec(); ch = n; tile.resetZoom(); updTitle(); tile.play(ch, sub); strip.adapter?.notifyDataSetChanged() }

  inner class StripAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val order get() = Cfg.favs(this@PreviewActivity).sorted() + (1..Cfg.COUNT).filter { !Cfg.isFav(this@PreviewActivity, it) }
    override fun getItemCount() = Cfg.COUNT
    override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder {
      val c = card().apply { layoutParams = RecyclerView.LayoutParams(dp(140), -2) }
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
    val bmp: Bitmap = tile.snapshot() ?: run { Toast.makeText(this, "暂无画面", Toast.LENGTH_SHORT).show(); return }
    val name = "通道${ch}_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date()) + ".jpg"
    try {
      Saver.image(this, bmp, name)
      Toast.makeText(this, "已保存到相册：图片/大华监控截图", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) { Toast.makeText(this, "截图失败：${e.message}", Toast.LENGTH_LONG).show() }
  }
}
