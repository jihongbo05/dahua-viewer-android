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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
  private lateinit var ov: LinearLayout; private lateinit var ovTitle: TextView
  private var forceFull = false
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
    initTheme(this); super.onCreate(b)
    ch = intent.getIntExtra("ch", 1)
    if (Cfg.flag(this, "keep", true)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    val start = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -1) }
    start.timeInMillis = intent.getLongExtra("t", start.timeInMillis)
    if (b != null) start.timeInMillis = b.getLong("t", start.timeInMillis)
    day.time = start.time; zero(day); openAt = start.time
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
    val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.BLACK); setPadding(dp(4), dp(4), dp(16), dp(4)) }
    bar.addView(ImageButton(this).apply { setImageResource(R.drawable.ic_back); setColorFilter(Color.WHITE); setBackgroundColor(0); setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(48), dp(48)))
    bar.addView(tv("录像回放 · ${Cfg.name(this, ch)}", 17f, Color.WHITE, true)); topBar = bar; root.addView(bar)
    videoBox = Ratio(this).apply { setBackgroundColor(Color.BLACK) }
    tile = Tile(this, showLabel = false).apply { showInfo = true; zoomable = true; autoReconnect = false; onEnd = { setPlayUi(false) } }
    videoBox.addView(tile, FrameLayout.LayoutParams(-1, -1))
    ov = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x99000000.toInt(), 0)); setPadding(dp(8), dp(8), dp(16), dp(24)); visibility = View.GONE }
    ov.addView(ImageButton(this).apply { setImageResource(R.drawable.ic_back); setColorFilter(Color.WHITE); setBackgroundColor(0); setOnClickListener { exitFull() } }, LinearLayout.LayoutParams(dp(48), dp(48)))
    ovTitle = tv("回放 · ${Cfg.name(this, ch)}", 15f, Color.WHITE, true); ov.addView(ovTitle, LinearLayout.LayoutParams(0, -2, 1f))
    fun ob(t: String, f: () -> Unit) = TextView(this).apply { text = t; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; background = round(0x33FFFFFF, 16)
      setPadding(dp(14), 0, dp(14), 0); setOnClickListener { f(); showOv() } }.also { ov.addView(it, LinearLayout.LayoutParams(-2, dp(36)).apply { marginStart = dp(8) }) }
    ob("暂停") { togglePlay() }; ob("截图") { shot() }; ob("退出全屏") { exitFull() }
    videoBox.addView(ov, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
    root.addView(videoBox, LinearLayout.LayoutParams(-1, -2))
    tile.onDouble = { tile.toggleZoom() }
    tile.onTap = {
      if (inFull()) { if (ov.visibility == View.VISIBLE) ov.visibility = View.GONE else showOv() }
      else if (!wide()) requestedOrientation = if (isLand()) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    val bl = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, dp(8), dp(8)) }
    val c1 = card(); val l1 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }; c1.addView(l1)
    clock = tv("", 20f, TEXT1, true).apply { gravity = Gravity.CENTER }; l1.addView(clock)
    val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
    dateBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
    timeBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
    row.addView(dateBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }); row.addView(timeBtn, LinearLayout.LayoutParams(0, -2, 1f))
    l1.addView(row)
    // 日期条：最近 7 天，一键切换
    val days = LinearLayout(this)
    val dayBtns = mutableListOf<TextView>()
    fun paintDays() { dayBtns.forEach { b -> val on = (b.tag as Long) == day.timeInMillis
      b.setTextColor(if (on) Color.WHITE else TEXT1); b.background = round(if (on) ACCENT else FILL, 12) } }
    for (i in 6 downTo 0) { val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -i) }; zero(c)
      val lbl = when (i) { 0 -> "今天"; 1 -> "昨天"; else -> SimpleDateFormat("M/d", Locale.CHINA).format(c.time) }
      val wk = arrayOf("日", "一", "二", "三", "四", "五", "六")[c.get(Calendar.DAY_OF_WEEK) - 1]
      val b = TextView(this).apply { text = "$lbl\n周$wk"; textSize = 12f; gravity = Gravity.CENTER; tag = c.timeInMillis
        setOnClickListener { val m = slider.value.toLong(); day.timeInMillis = tag as Long; open(Date(day.timeInMillis + m * 60_000)); paintDays() } }
      dayBtns += b; days.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), dp(8), dp(2), dp(4)) }) }
    l1.addView(days); dayPaint = { paintDays() }
    slider = Slider(this).apply { valueFrom = 0f; valueTo = 1439f; stepSize = 1f
      setLabelFormatter { v -> "%02d:%02d".format(v.toInt() / 60, v.toInt() % 60) }
      addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
        override fun onStartTrackingTouch(s: Slider) { dragging = true }
        override fun onStopTrackingTouch(s: Slider) { dragging = false; open(Date(day.timeInMillis + s.value.toLong() * 60_000)) } }) }
    l1.addView(slider)
    l1.addView(tv("拖动时间轴或点日期/时间跳转（每次跳转会重新连接录像机）", 11f, TEXT2).apply { gravity = Gravity.CENTER })
    val quick = com.google.android.material.chip.ChipGroup(this).apply { setPadding(0, dp(6), 0, 0) }
    listOf<Pair<String, () -> Long>>("最近30分钟" to { System.currentTimeMillis() - 30 * 60_000L }, "最近1小时" to { System.currentTimeMillis() - 3600_000L },
      "今天凌晨起" to { Calendar.getInstance().apply { zero(this) }.timeInMillis }).forEach { (t, f) ->
      quick.addView(com.google.android.material.chip.Chip(this).apply { text = t; setOnClickListener { open(Date(f())) } }) }
    l1.addView(quick)
    bl.addView(c1)
    val ctl = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(4)) }
    fun rb(res: Int, label: String, f: () -> Unit): Pair<ImageView, TextView> {
      val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setOnClickListener { f() } }
      val circle = FrameLayout(this).apply { background = round(soft(ACCENT, 0x22), 26) }
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
    rb(R.drawable.ic_fullscreen, "全屏") { enterFull() }
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
    timeBtn.setOnClickListener { showPreciseTime() }
    applyOrientation()
  }
  private fun showPreciseTime() {
    val c = Calendar.getInstance().apply { timeInMillis = pos() }
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
    fun num(hint: String, v: Int, max: Int) = com.google.android.material.textfield.TextInputLayout(this).apply {
      this.hint = hint; boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
      val r = dp(10).toFloat(); setBoxCornerRadii(r,r,r,r)
      addView(com.google.android.material.textfield.TextInputEditText(context).apply {
        setText("%02d".format(v)); inputType = android.text.InputType.TYPE_CLASS_NUMBER; setSingleLine(); tag = max })
    }
    val row = LinearLayout(this)
    val hh = num("时", c.get(Calendar.HOUR_OF_DAY), 23)
    val mm = num("分", c.get(Calendar.MINUTE), 59)
    val ss = num("秒", c.get(Calendar.SECOND), 59)
    row.addView(hh, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
    row.addView(mm, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
    row.addView(ss, LinearLayout.LayoutParams(0, -2, 1f))
    box.addView(tv("精确到秒（24小时制）", 13f, TEXT2).apply { setPadding(0, 0, 0, dp(8)) })
    box.addView(row)
    // also keep system picker shortcut
    box.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
      text = "用系统时间选择器（到分钟）"; setOnClickListener {
        TimePickerDialog(this@PlaybackActivity, { _, h, m -> open(Date(day.timeInMillis + (h * 60 + m) * 60_000L)) },
          c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
      }
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    fun read(til: com.google.android.material.textfield.TextInputLayout): Int {
      val et = til.editText!!; val max = et.tag as Int
      return et.text.toString().toIntOrNull()?.coerceIn(0, max) ?: 0
    }
    MaterialAlertDialogBuilder(this).setTitle("跳转到时间").setView(box)
      .setPositiveButton("跳转") { _, _ ->
        val t = day.timeInMillis + (read(hh) * 3600L + read(mm) * 60L + read(ss)) * 1000L
        open(Date(t))
      }.setNegativeButton("取消", null).show()
  }
  private fun inFull() = forceFull || isLand()
  private fun enterFull() { if (wide()) { forceFull = true; applyOrientation() } else requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
  private fun exitFull() { if (forceFull) { forceFull = false; applyOrientation() } else requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
  private fun showOv() { ov.visibility = View.VISIBLE; ov.alpha = 0f; ov.animate().alpha(1f).setDuration(150).start(); h.removeCallbacks(hideOv); h.postDelayed(hideOv, 4000) }
  private val hideOv = Runnable { ov.visibility = View.GONE }
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
    setPlayUi(true); updClock(); dayPaint()
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
    timeBtn.text = "%02d:%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))
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
    val bmp = tile.snapshot() ?: run { snack("还没出画面，稍等再截"); return }
    try { Saver.image(this, bmp, "回放_通道${ch}_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date(pos())) + ".jpg")
      snack("截图已保存到相册") } catch (e: Exception) { Toast.makeText(this, "截图失败：${e.message}", Toast.LENGTH_LONG).show() }
  }
  private var dayPaint: () -> Unit = {}
  private fun isLand() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !wide()
  override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); applyOrientation() }
  @Suppress("DEPRECATION")
  private fun applyOrientation() {
    val land = inFull(); videoBox.enabledRatio = !land
    videoBox.maxH = if (wide() && !land) (resources.displayMetrics.heightPixels * 0.55f).toInt() else Int.MAX_VALUE
    videoBox.layoutParams = LinearLayout.LayoutParams(-1, if (land) -1 else -2)
    below.visibility = if (land) View.GONE else View.VISIBLE; topBar.visibility = below.visibility
    if (land) showOv() else ov.visibility = View.GONE
    window.decorView.systemUiVisibility = if (land) View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else 0
  }
  @Deprecated("") override fun onBackPressed() { if (inFull()) { exitFull(); return }; @Suppress("DEPRECATION") super.onBackPressed() }
  override fun onSaveInstanceState(o: Bundle) { super.onSaveInstanceState(o); o.putLong("t", pos()) }
  override fun onResume() { super.onResume(); open(openAt) }
  override fun onPause() { super.onPause(); if (tile.playing) openAt = Date(pos()); if (tile.recPath != null) stopDl(true); tile.stop(); h.removeCallbacksAndMessages(null) }
}
