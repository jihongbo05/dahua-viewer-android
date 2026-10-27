package com.hb.dahuaviewer
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.io.FileOutputStream

fun findTex(v: View): TextureView? { if (v is TextureView) return v
  if (v is ViewGroup) for (i in 0 until v.childCount) findTex(v.getChildAt(i))?.let { return it }; return null }

/** 缩略图：从子码流抓一帧，缓存到磁盘。host 是一个被页面盖住的容器（TextureView 需挂在窗口上才出图）。 */
object Thumbs {
  val online = HashMap<Int, Boolean>()
  val listeners = mutableListOf<(Int) -> Unit>()
  private val queue = ArrayDeque<Int>()
  private var running = 0
  private val h = Handler(Looper.getMainLooper())
  var host: FrameLayout? = null
  fun dir(c: Context) = File(c.cacheDir, "thumbs").apply { mkdirs() }
  fun file(c: Context, ch: Int) = File(dir(c), "ch$ch.jpg")
  fun load(c: Context, ch: Int): Bitmap? = file(c, ch).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
  fun age(c: Context, ch: Int) = file(c, ch).let { if (it.exists()) System.currentTimeMillis() - it.lastModified() else Long.MAX_VALUE }
  fun clear(c: Context) { dir(c).listFiles()?.forEach { it.delete() }; online.clear() }
  fun size(c: Context) = dir(c).listFiles()?.sumOf { it.length() } ?: 0L
  fun request(c: Context, chs: List<Int>, force: Boolean = false) {
    for (ch in chs) if (ch !in queue && (force || age(c, ch) > 10 * 60_000)) queue.addLast(ch)
    pump(c)
  }
  fun cancel() = queue.clear()
  private fun pump(c: Context) { while (running < 2 && queue.isNotEmpty() && host != null) grab(c, queue.removeFirst()) }
  private fun grab(c: Context, ch: Int) {
    val hostV = host ?: return
    running++
    val lay = VLCVideoLayout(c); hostV.addView(lay, FrameLayout.LayoutParams(c.dp(240), c.dp(135)))
    val mp = MediaPlayer(Vlc.get(c)); mp.attachViews(lay, null, false, true)
    val m = Media(Vlc.get(c), android.net.Uri.parse(Cfg.url(c, ch, 1)))
    m.addOption(":network-caching=500"); m.addOption(":rtsp-tcp"); m.setHWDecoderEnabled(true, false)
    mp.media = m; m.release()
    var done = false
    lateinit var timeout: Runnable
    fun finish(ok: Boolean) {
      if (done) return; done = true; h.removeCallbacks(timeout)
      mp.setEventListener(null); mp.stop(); mp.detachViews(); mp.release(); hostV.removeView(lay)
      running--; online[ch] = ok; listeners.toList().forEach { it(ch) }; pump(c)
    }
    timeout = Runnable { finish(false) }
    h.postDelayed(timeout, 12000)
    mp.setEventListener { e -> when (e.type) {
      MediaPlayer.Event.Vout -> if (e.voutCount > 0) h.postDelayed({
        if (!done) { val b = findTex(lay)?.bitmap
          if (b != null) try { FileOutputStream(file(c, ch)).use { b.compress(Bitmap.CompressFormat.JPEG, 80, it) } } catch (_: Exception) {}
          finish(b != null) } }, 1500)
      MediaPlayer.Event.EncounteredError -> finish(false)
    } }
    mp.play()
  }
}
