package com.hb.dahuaviewer
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

object Vlc {
  private var lib: LibVLC? = null
  fun get(c: Context): LibVLC = lib ?: LibVLC(c.applicationContext, arrayListOf("--rtsp-tcp", "--no-audio")).also { lib = it }
}

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class Tile(ctx: Context, showLabel: Boolean = true) : FrameLayout(ctx) {
  private val layout = VLCVideoLayout(ctx)
  private val thumb = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
  val label = TextView(ctx).apply { setTextColor(Color.WHITE); textSize = 12f; setPadding(ctx.dp(8), ctx.dp(14), ctx.dp(8), ctx.dp(6)); background = ctx.drw(R.drawable.grad_bottom); maxLines = 1 }
  val info = TextView(ctx).apply { setTextColor(Color.WHITE); textSize = 10f; setPadding(ctx.dp(6), ctx.dp(2), ctx.dp(6), ctx.dp(2)); background = ctx.round(0x66000000, 4); visibility = GONE }
  private val stBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
  private val spin = ProgressBar(ctx).apply { isIndeterminate = true }
  private val status = TextView(ctx).apply { setTextColor(0xFFDDDDDD.toInt()); textSize = 12f; gravity = Gravity.CENTER }
  private var mp: MediaPlayer? = null
  private var media: Media? = null
  private val h = Handler(Looper.getMainLooper())
  var channel = 0; private set
  var sub = 1; private set
  private var want = false
  var showInfo = false
  var customUrl: String? = null
  var autoReconnect = true
  var onEnd: (() -> Unit)? = null
  var recPath: String? = null; private set
  var zoomable = false
  private var sc = 1f
  val zoomed get() = sc > 1.01f
  private val sgd = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    override fun onScale(d: ScaleGestureDetector): Boolean { sc = (sc * d.scaleFactor).coerceIn(1f, 6f); applyZoom(); return true }
  })
  private fun applyZoom() {
    layout.scaleX = sc; layout.scaleY = sc
    val mx = (sc - 1) * width / 2; val my = (sc - 1) * height / 2
    layout.translationX = layout.translationX.coerceIn(-mx, mx); layout.translationY = layout.translationY.coerceIn(-my, my)
  }
  fun resetZoom() { sc = 1f; layout.translationX = 0f; layout.translationY = 0f; applyZoom() }
  var onTap: (() -> Unit)? = null; var onDouble: (() -> Unit)? = null; var onLong: (() -> Unit)? = null
  private val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
    override fun onDown(e: MotionEvent) = true
    override fun onSingleTapConfirmed(e: MotionEvent): Boolean { onTap?.invoke(); return true }
    override fun onDoubleTap(e: MotionEvent): Boolean { onDouble?.invoke(); return true }
    override fun onLongPress(e: MotionEvent) { if (!zoomed) onLong?.invoke() }
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
      if (zoomable && zoomed) { layout.translationX -= dx; layout.translationY -= dy; applyZoom(); return true }; return false }
  })
  init {
    setBackgroundColor(Color.BLACK)
    addView(thumb, LayoutParams(-1, -1)); addView(layout, LayoutParams(-1, -1))
    stBox.addView(spin, LinearLayout.LayoutParams(ctx.dp(28), ctx.dp(28))); stBox.addView(status)
    addView(stBox, LayoutParams(-2, -2, Gravity.CENTER))
    if (showLabel) addView(label, LayoutParams(-1, -2, Gravity.BOTTOM))
    addView(info, LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(ctx.dp(6), ctx.dp(6), ctx.dp(6), ctx.dp(6)) })
    setOnTouchListener { _, e -> if (zoomable) sgd.onTouchEvent(e); if (!sgd.isInProgress) gd.onTouchEvent(e); true }
    setStatus(null)
  }
  private fun setStatus(s: String?, busy: Boolean = true) {
    stBox.visibility = if (s == null) GONE else VISIBLE; status.text = s ?: ""; spin.visibility = if (busy) VISIBLE else GONE }
  fun bind(ch: Int) {
    channel = ch; label.text = "${Cfg.name(context, ch)}  ·  $ch"
    val b = Thumbs.load(context, ch); thumb.setImageBitmap(b); thumb.visibility = VISIBLE
  }
  fun play(ch: Int, subtype: Int) {
    stop(); bind(ch); sub = subtype; want = true; start()
  }
  private fun start() {
    if (!want) return
    setStatus("加载中…")
    val p = MediaPlayer(Vlc.get(context)); mp = p
    p.attachViews(layout, null, false, true)
    val m = Media(Vlc.get(context), android.net.Uri.parse(customUrl ?: Cfg.url(context, channel, sub)))
    m.setHWDecoderEnabled(true, false)
    m.addOption(":network-caching=${Cfg.caching(context)}"); m.addOption(":rtsp-tcp")
    recPath?.let { m.addOption(":sout=#duplicate{dst=display,dst=std{access=file,mux=mp4,dst='$it'}}"); m.addOption(":sout-keep") }
    p.media = m; media = m
    p.setEventListener { e ->
      when (e.type) {
        MediaPlayer.Event.Vout -> if (e.voutCount > 0) { setStatus(null); thumb.visibility = INVISIBLE; Thumbs.online[channel] = true; tick() }
        MediaPlayer.Event.EndReached -> if (!autoReconnect && mp === p) { setStatus("播放结束", false); onEnd?.invoke() } else if (want && mp === p) {
          setStatus("重连中…"); h.removeCallbacksAndMessages(null); h.postDelayed({ if (want && mp === p) { release(); start() } }, 3000) }
        MediaPlayer.Event.EncounteredError -> if (want && mp === p) {
          setStatus("重连中…"); thumb.visibility = VISIBLE; Thumbs.online[channel] = false
          h.removeCallbacksAndMessages(null); h.postDelayed({ if (want && mp === p) { release(); start() } }, 3000)
        }
      }
    }
    p.play()
  }
  private fun tick() {
    if (!showInfo) return
    val p = mp ?: return
    val t = p.currentVideoTrack
    val kbps = media?.stats?.let { (it.demuxBitrate * 8000).toInt() } ?: 0
    info.text = (if (t != null) "${t.width}×${t.height}  " else "") + (if (kbps > 0) "$kbps kbps" else "")
    info.visibility = if (info.text.isBlank()) GONE else VISIBLE
    h.postDelayed({ tick() }, 2000)
  }
  private fun release() {
    mp?.let { it.setEventListener(null); it.stop(); it.detachViews(); it.release() }
    media?.release(); media = null; mp = null
  }
  fun stop() { want = false; h.removeCallbacksAndMessages(null); release(); setStatus(null); info.visibility = GONE; thumb.visibility = VISIBLE }
  val playing get() = want
  fun restart() { if (want) { h.removeCallbacksAndMessages(null); release(); start() } }
  fun startRecord(path: String) { recPath = path; restart() }
  fun stopRecord(): String? { val r = recPath; recPath = null; restart(); return r }
  fun stopRecordSilently() { recPath = null }
  fun pause() = mp?.pause()
  fun resume() = mp?.play()
  val isPaused get() = mp?.isPlaying == false
  fun setRate(r: Float) { mp?.rate = r }
  val time get() = mp?.time ?: 0L
  fun snapshot(): Bitmap? = findTex(layout)?.bitmap
}
