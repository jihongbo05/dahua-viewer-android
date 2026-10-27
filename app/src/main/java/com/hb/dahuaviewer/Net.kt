package com.hb.dahuaviewer
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

object Net {
  fun reach(h: String, p: Int, t: Int = 2000) = h.isNotBlank() && try { Socket().use { it.connect(InetSocketAddress(h, p), t) }; true } catch (_: Exception) { false }
  /** 自动模式：按 公网→内网→域名 顺序尝试，返回第一个可达的并设为当前；手动模式：只测当前地址。阻塞调用，放在后台线程。 */
  fun resolve(c: Context): Cfg.Prof? {
    if (Cfg.flag(c, "auto", true)) {
      for (pr in Cfg.profiles(c)) if (reach(pr.host, pr.port)) { Cfg.setActive(c, pr); return pr }
      return null
    }
    val pr = Cfg.profiles(c).firstOrNull { it.key == Cfg.active(c) } ?: Cfg.Prof("manual", "手动", Cfg.host(c), Cfg.port(c))
    return if (reach(Cfg.host(c), Cfg.port(c))) pr else null
  }
  fun cellular(c: Context): Boolean {
    val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
    val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
  }
  fun notify(c: Context, title: String, text: String) {
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    if (Build.VERSION.SDK_INT >= 26) c.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("alert", "掉线提醒", NotificationManager.IMPORTANCE_DEFAULT))
    val n = NotificationCompat.Builder(c, "alert").setSmallIcon(R.drawable.ic_videocam).setContentTitle(title).setContentText(text).setAutoCancel(true).build()
    NotificationManagerCompat.from(c).notify(1, n)
  }
  fun schedule(c: Context, on: Boolean) {
    val wm = WorkManager.getInstance(c)
    if (!on) { wm.cancelUniqueWork("watch"); return }
    val req = PeriodicWorkRequestBuilder<WatchWorker>(15, TimeUnit.MINUTES)
      .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
    wm.enqueueUniquePeriodicWork("watch", ExistingPeriodicWorkPolicy.KEEP, req)
  }
}

class WatchWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
  override fun doWork(): Result {
    val c = applicationContext
    if (!Cfg.configured(c)) return Result.success()
    val ok = Net.resolve(c) != null
    if (!ok && Cfg.flag(c, "lastOk", true)) Net.notify(c, "监控录像机连不上", "地址都不可达，可能是公网IP变了或网络断了，请检查")
    if (ok && !Cfg.flag(c, "lastOk", true)) Net.notify(c, "监控已恢复在线", "录像机重新可以连接")
    Cfg.setFlag(c, "lastOk", ok)
    return Result.success()
  }
}

object Saver {
  fun image(c: Context, bmp: Bitmap, name: String) {
    if (Build.VERSION.SDK_INT >= 29) {
      val cv = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/大华监控截图") }
      val uri = c.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)!!
      c.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    } else {
      @Suppress("DEPRECATION") val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "大华监控截图").apply { mkdirs() }
      FileOutputStream(File(d, name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }
  }
  fun tempVideo(c: Context, name: String) = File(c.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: c.filesDir, name)
  /** 把录好的临时 mp4 拷到 相册/Movies/大华监控，返回是否成功 */
  fun video(c: Context, f: File): Boolean {
    if (!f.exists() || f.length() < 1024) return false
    return try {
      if (Build.VERSION.SDK_INT >= 29) {
        val cv = ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, f.name); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
          put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/大华监控") }
        val uri = c.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)!!
        c.contentResolver.openOutputStream(uri)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
      } else {
        @Suppress("DEPRECATION") val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "大华监控").apply { mkdirs() }
        f.copyTo(File(d, f.name), true)
      }
      f.delete(); true
    } catch (_: Exception) { false }
  }
}
