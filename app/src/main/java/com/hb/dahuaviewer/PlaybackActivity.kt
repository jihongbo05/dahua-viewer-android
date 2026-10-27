package com.hb.dahuaviewer
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** 录像回放：rtsp://.../cam/playback?channel=N&starttime=...&endtime=...  拖动/跳转 = 按新时间重新打开 */
class PlaybackActivity : AppCompatActivity() {
  private lateinit var tile: Tile
  private lateinit var videoBox: Ratio
  private lateinit var below: View; private lateinit var topBar: View
  private lateinit var clock: TextView; private lateinit var dateBtn: MaterialButton; private lateinit var timeBtn: MaterialButton
  private lateinit var slider: Slider; private lateinit var playLbl: TextView; private lateinit var playIcon: ImageView; private lateinit var dlLbl: TextView
  private var ch = 1
  private val day = Calendar.getInstance()          // 选择的日期（00:00）
  private var openAt = Date()                        // 当前打开的起始时间
  private var rate = 1f
  private var dragging = false
  private var recStart = 0L
  private val h = Handler(Looper.getMainLooper())
  private val df = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
  private val full = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

  override fun onCreate(b: Bundle?) {
    super.onCreate(b)
    ch = intent.getIntExtra("ch", 1)
    if (Cfg.flag(this, "keep", true)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    val start = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -1) }
    if (b != null) start.timeInMillis = b.getLong("t", start.timeInMillis)
    day.time = start.time; zero(day); openAt = start.time
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(getColor(R.color.bg)) }
    val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.BLACK); setPadding(dp(4), dp(4), dp(16), dp(4)) }
    bar.addView(ImageButton(this).apply { setImageResource(R.drawable.ic_back); setColorFilter(Color.WHITE); setBackgroundColor(0); setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(48), dp(48)))
    bar.addView(tv("录像回放 · ${Cfg.name(this, ch)}", 17f, Color.WHITE, true)); topBar = bar; root.addView(bar)
    videoBox = Ratio(this).apply { setBackgroundColor(Color.BLACK) }
    tile = Tile(this, showLabel = false).apply { showInfo = true; zoomable = true; autoReconnect = false; onEnd = { setPlayUi(false) } }
    videoBox.addView(tile, FrameLayout.LayoutParams(-1, -1)); root.addView(videoBox, LinearLayout.LayoutParams(-1, -2))
    tile.onDouble = { if (tile.zoomed) tile.resetZoom() else requestedOrientation = if (isLand()) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }

    val bl = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, dp(8), dp(8)) }
    val c1 = card(); val l1 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }; c1.addView(l1)
    clock = tv("", 20f, TEXT1, true).apply { gravity = Gravity.CENTER }; l1.addView(clock)
    val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
    dateBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
    timeBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
    row.addView(dateBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }); row.addView(timeBtn, LinearLayout.LayoutParams(0, -2, 1f))
    l1.addView(row)
    slider = Slider(this).apply { valueFrom = 0f; valueTo = 1439f; stepSize = 1f
      setLabelFormatter { v -> "%02d:%02d".format(v.toInt() / 60, v.toInt() % 60) }
      addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
        override fun onStartTrackingTouch(s: Slider) { dragging = true }
        override fun onStopTrackingTouch(s: Slider) { dragging = false; open(Date(day.timeInMillis + s.value.toLong() * 60_000)) } }) }
    l1.addView(slider)
    l1.addView(tv("拖动时间轴或选时间跳转（每次跳转会重新连接录像机）", 11f, TEXT2).apply { gravity = Gravity.CENTER })
    bl.addView(c1)
    val ctl = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(4)) }
    fun rb(res: Int, label: String, f: () -> Unit): Pair<ImageView, TextView> {
      val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setOnClickListener { f() } }
      val circle = FrameLayout(this).apply { background = round(0xFFFFF1E8.toInt(), 26) }
      val ic = icon(res, ACCENT); circle.addView(ic, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
      col.addView(circle, LinearLayout.LayoutParams(dp(52), dp(52)))
      val t = tv(label, 12f, TEXT1).apply { setPadding(0, dp(6), 0, 0) }; col.addView(t)
      ctl.addView(col, LinearLayout.LayoutParams(0, -2, 1f)); return ic to t
    }
    rb(R.drawable.ic_rewind, "后退30秒") { open(Date(pos() - 30_000)) }
    rb(R.drawable.ic_play, "播放") { togglePlay() }.let { playIcon = it.first; playLbl = it.second }
    rb(R.drawable.ic_forward, "前进30秒") { open(Date(pos() + 30_000)) }
    rb(R.drawable.ic_shot, "截图") { shot() }
    dlLbl = rb(R.drawable.ic_download, "下载片段") { if (tile.recPath != null) stopDl() else startDl() }.second
    bl.addView(ctl)
    val sp = MaterialButtonToggleGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
    listOf(0.5f, 1f, 2f, 4f).forEachIndexed { i, r -> sp.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
      id = View.generateViewId(); text = if (r < 1) "0.5x" else "${r.toInt()}x"; tag = r; if (r == 1f) post { sp.check(id) } }, LinearLayout.LayoutParams(0, -2, 1f)) }
    sp.addOnButtonCheckedListener { g, id, on -> if (on) { rate = g.findViewById<View>(id).tag as Float; tile.setRate(rate) } }
    bl.addView(tv("倍速（录像机是否支持倍速待验证）", 12f, TEXT2).apply { setPadding(dp(8), dp(8), 0, dp(4)) })
    bl.addView(sp)
    below = ScrollView(this).apply { addView(bl) }
    root.addView(below, LinearLayout.LayoutParams(-1, 0, 1f))
    setContentView(root)
    dateBtn.setOnClickListener { DatePickerDialog(this, { _, y, m, d -> day.set(y, m, d); zero(day)
      open(Date(day.timeInMillis + slider.value.toLong() * 60_000)) }, day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH))
      .apply { datePicker.maxDate = System.currentTimeMillis() }.show() }
    timeBtn.setOnClickListener { val c = Calendar.getInstance().apply { timeInMillis = pos() }
      TimePickerDialog(this, { _, hh, mm -> open(Date(day.timeInMillis + (hh * 60 + mm) * 60_000L)) }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show() }
    applyOrientation()
  }
  private fun zero(c: Calendar) { c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0) }
  private fun pos() = openAt.time + (tile.time * 1L)
  private fun open(t: Date) {
    if (tile.recPath != null) stopDl()
    val now = System.currentTimeMillis()
    val st = Date(minOf(t.time, now - 10_000))
    openAt = st
    day.time = st; zero(day)
    val end = Date(minOf(st.time + 6 * 3600_000L, day.timeInMillis + 24 * 3600_000L - 1000, now))
    tile.customUrl = Cfg.playUrl(this, ch, st, end)
    tile.play(ch, 0); tile.setRate(rate)
    if (Net.cellular(this)) Toast.makeText(this, "当前为移动数据：回放是高清主码流，流量较大", Toast.LENGTH_SHORT).show()
    setPlayUi(true); updClock()
  }
  private fun setPlayUi(playing: Boolean) { playIcon.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play); playLbl.text = if (playing) "暂停" else "播放" }
  private fun togglePlay() {
    when { !tile.playing -> open(Date(pos()))
      tile.isPaused -> { tile.resume(); setPlayUi(true) }
      else -> { tile.pause(); setPlayUi(false) } }
  }
  private fun updClock() {
    h.removeCallbacksAndMessages(null)
    val p = pos(); clock.text = full.format(Date(p)); dateBtn.text = df.format(day.time)
    val c = Calendar.getInstance().apply { timeInMillis = p }
    timeBtn.text = "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    if (!dragging) slider.value = ((p - day.timeInMillis) / 60_000).coerceIn(0, 1439).toFloat()
    if (tile.recPath != null) { val s = (System.currentTimeMillis() - recStart) / 1000; dlLbl.text = "%02d:%02d".format(s / 60, s % 60); dlLbl.setTextColor(0xFFD93025.toInt()) }
    else { dlLbl.text = "下载片段"; dlLbl.setTextColor(TEXT1) }
    h.postDelayed({ updClock() }, 1000)
  }
  private fun startDl() {
    if (!tile.playing) { Toast.makeText(this, "请先播放", Toast.LENGTH_SHORT).show(); return }
    val resumeAt = Date(pos())
    val f = Saver.tempVideo(this, "回放_通道${ch}_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(resumeAt) + ".mp4")
    openAt = resumeAt; val end = Date(minOf(resumeAt.time + 6 * 3600_000L, System.currentTimeMillis()))
    tile.customUrl = Cfg.playUrl(this, ch, resumeAt, end)
    tile.startRecord(f.path); recStart = System.currentTimeMillis()
    Toast.makeText(this, "开始下载片段，再点一次停止", Toast.LENGTH_SHORT).show()
  }
  private fun stopDl(silent: Boolean = false) {
    val resumeAt = Date(pos())
    val path = tile.recPath ?: return
    tile.stopRecordSilently()
    if (!silent) { openAt = resumeAt; tile.customUrl = Cfg.playUrl(this, ch, resumeAt, Date(minOf(resumeAt.time + 6 * 3600_000L, System.currentTimeMillis()))); tile.restart() } else tile.stop()
    h.postDelayed({ Thread { val ok = Saver.video(this, File(path))
      runOnUiThread { Toast.makeText(this, if (ok) "片段已保存到 相册/Movies/大华监控" else "片段保存失败", Toast.LENGTH_LONG).show() } }.start() }, 800)
  }
  private fun shot() {
    val bmp = tile.snapshot() ?: run { Toast.makeText(this, "暂无画面", Toast.LENGTH_SHORT).show(); return }
    try { Saver.image(this, bmp, "回放_通道${ch}_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date(pos())) + ".jpg")
      Toast.makeText(this, "已保存到相册：图片/大华监控截图", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Toast.makeText(this, "截图失败：${e.message}", Toast.LENGTH_LONG).show() }
  }
  private fun isLand() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
  override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); applyOrientation() }
  @Suppress("DEPRECATION")
  private fun applyOrientation() {
    val land = isLand(); videoBox.enabledRatio = !land
    videoBox.layoutParams = LinearLayout.LayoutParams(-1, if (land) -1 else -2)
    below.visibility = if (land) View.GONE else View.VISIBLE; topBar.visibility = below.visibility
    window.decorView.systemUiVisibility = if (land) View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else 0
  }
  @Deprecated("") override fun onBackPressed() { if (isLand()) { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT; return }; @Suppress("DEPRECATION") super.onBackPressed() }
  override fun onSaveInstanceState(o: Bundle) { super.onSaveInstanceState(o); o.putLong("t", pos()) }
  override fun onResume() { super.onResume(); open(openAt) }
  override fun onPause() { super.onPause(); if (tile.playing) openAt = Date(pos()); if (tile.recPath != null) stopDl(true); tile.stop(); h.removeCallbacksAndMessages(null) }
}
