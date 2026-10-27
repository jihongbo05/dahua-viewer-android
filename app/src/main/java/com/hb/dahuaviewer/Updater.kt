package com.hb.dahuaviewer
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

/** 在线更新：GitHub Releases（latest），下载失败/太慢时依次尝试镜像前缀（第三方服务） */
object Updater {
  const val REPO = "jihongbo05/dahua-viewer-android"
  const val DEFAULT_MIRRORS = "https://ghfast.top/\nhttps://gh-proxy.com/"
  data class Rel(val tag: String, val body: String, val apkUrl: String, val apkName: String, val size: Long, val shaUrl: String?, val htmlUrl: String)

  private fun prefs(c: Context) = c.getSharedPreferences("upd", 0)
  fun auto(c: Context) = prefs(c).getBoolean("auto", true)
  fun setAuto(c: Context, v: Boolean) = prefs(c).edit().putBoolean("auto", v).apply()
  fun mirrors(c: Context) = prefs(c).getString("mirrors", DEFAULT_MIRRORS)!!
  fun setMirrors(c: Context, v: String) = prefs(c).edit().putString("mirrors", v).apply()
  private fun mirrorList(c: Context) = mirrors(c).split("\n", ",", " ").map { it.trim() }.filter { it.startsWith("http") }.map { if (it.endsWith("/")) it else "$it/" }

  /** "v1.3.0" vs "1.2" → 正数表示 a 更新 */
  fun cmp(a: String, b: String): Int {
    fun parts(s: String) = s.trim().removePrefix("v").removePrefix("V").split(".", "-").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val x = parts(a); val y = parts(b)
    for (i in 0 until maxOf(x.size, y.size)) { val d = (x.getOrElse(i) { 0 }) - (y.getOrElse(i) { 0 }); if (d != 0) return d }
    return 0
  }
  fun parse(json: String): Rel? {
    val o = JSONObject(json); val assets = o.optJSONArray("assets") ?: return null
    var apk: JSONObject? = null; var sha: String? = null
    for (i in 0 until assets.length()) { val a = assets.getJSONObject(i); val n = a.getString("name")
      if (n.endsWith(".apk") && apk == null) apk = a; if (n.endsWith(".sha256")) sha = a.getString("browser_download_url") }
    apk ?: return null
    return Rel(o.getString("tag_name"), o.optString("body"), apk.getString("browser_download_url"), apk.getString("name"), apk.getLong("size"), sha, o.optString("html_url"))
  }

  fun autoCheck(a: Activity) {
    if (!auto(a)) return
    val last = prefs(a).getLong("last", 0)
    if (System.currentTimeMillis() - last < 12 * 3600_000L) return
    check(a, manual = false)
  }

  fun check(a: Activity, manual: Boolean) {
    if (manual) Toast.makeText(a, "正在检查更新…", Toast.LENGTH_SHORT).show()
    thread {
      val res: Result<Rel?> = try {
        val c = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
          connectTimeout = 8000; readTimeout = 10000; setRequestProperty("Accept", "application/vnd.github+json"); setRequestProperty("User-Agent", "DahuaViewer") }
        when (c.responseCode) {
          200 -> Result.success(parse(c.inputStream.bufferedReader().readText()))
          404 -> Result.failure(IOException("还没有发布任何版本"))
          403, 429 -> Result.failure(IOException("GitHub 接口访问次数超限，请过一小时再试"))
          else -> Result.failure(IOException("服务器返回 ${c.responseCode}"))
        }
      } catch (e: Exception) { Result.failure(IOException("网络不通或无法访问 GitHub（${e.javaClass.simpleName}）")) }
      a.runOnUiThread {
        if (a.isFinishing) return@runOnUiThread
        prefs(a).edit().putLong("last", System.currentTimeMillis()).apply()
        val r = res.getOrNull()
        when {
          res.isFailure -> if (manual) MaterialAlertDialogBuilder(a).setTitle("检查更新失败").setMessage(res.exceptionOrNull()?.message).setPositiveButton("好", null).show()
          r == null -> if (manual) Toast.makeText(a, "最新版本里没有安装包", Toast.LENGTH_LONG).show()
          cmp(r.tag, BuildConfig.VERSION_NAME) <= 0 -> if (manual) Toast.makeText(a, "已是最新版本（v${BuildConfig.VERSION_NAME}）", Toast.LENGTH_SHORT).show()
          !manual && prefs(a).getString("ignore", "") == r.tag -> {}
          else -> offer(a, r)
        }
      }
    }
  }

  private fun offer(a: Activity, r: Rel) {
    MaterialAlertDialogBuilder(a).setTitle("发现新版本 ${r.tag}")
      .setMessage("当前 v${BuildConfig.VERSION_NAME}，安装包 %.1f MB\n\n更新日志：\n%s".format(r.size / 1048576.0, r.body.ifBlank { "（无）" }))
      .setPositiveButton("立即更新") { _, _ -> download(a, r) }
      .setNegativeButton("稍后", null)
      .setNeutralButton("忽略此版本") { _, _ -> prefs(a).edit().putString("ignore", r.tag).apply() }.show()
  }

  private fun download(a: Activity, r: Rel) {
    val bar = LinearProgressIndicator(a).apply { max = 100; isIndeterminate = true }
    val msg = TextView(a).apply { text = "连接中…"; setPadding(0, a.dp(8), 0, 0) }
    val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(24), a.dp(16), a.dp(24), 0); addView(bar); addView(msg) }
    val cancelFlag = java.util.concurrent.atomic.AtomicBoolean(false)
    val dlg: AlertDialog = MaterialAlertDialogBuilder(a).setTitle("下载 ${r.tag}").setView(box).setCancelable(false)
      .setNegativeButton("取消") { _, _ -> cancelFlag.set(true) }.show()
    val dir = File(a.cacheDir, "apk").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    val out = File(dir, "update.apk")
    thread {
      val sources = listOf("GitHub" to r.apkUrl) + mirrorList(a).map { "镜像 " + Uri.parse(it).host to it + r.apkUrl }
      var ok = false; var err = ""
      for ((name, url) in sources) {
        if (cancelFlag.get()) break
        a.runOnUiThread { msg.text = "从 $name 下载…" }
        try { if (fetch(url, out, r.size, { cancelFlag.get() }) { done, spd -> a.runOnUiThread {
            bar.isIndeterminate = false; bar.setProgressCompat((done * 100 / r.size).toInt(), true)
            msg.text = "$name：%.1f / %.1f MB  %d KB/s".format(done / 1048576.0, r.size / 1048576.0, spd) } }) { ok = true; break }
          err = "文件大小不对"
        } catch (e: Exception) { err = e.message ?: e.javaClass.simpleName }
      }
      var shaMsg = "（未提供 SHA-256，已校验文件大小）"
      if (ok && !cancelFlag.get()) {
        val expected = expectedSha(a, r)
        if (expected != null) {
          val got = sha256(out)
          if (!got.equals(expected, true)) { ok = false; err = "SHA-256 校验不一致，已删除下载文件"; out.delete() } else shaMsg = "（SHA-256 校验通过）"
        }
      }
      a.runOnUiThread {
        dlg.dismiss()
        if (cancelFlag.get()) return@runOnUiThread
        if (!ok) { MaterialAlertDialogBuilder(a).setTitle("下载失败").setMessage("$err\n\n可在「我的 → 更新」修改镜像地址后重试，或打开发布页手动下载。")
          .setPositiveButton("打开发布页") { _, _ -> a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.htmlUrl))) }.setNegativeButton("关闭", null).show(); return@runOnUiThread }
        MaterialAlertDialogBuilder(a).setTitle("下载完成").setMessage("新版本已下载 $shaMsg，点击安装。").setCancelable(false)
          .setPositiveButton("安装") { _, _ -> install(a, out) }.setNegativeButton("稍后", null).show()
      }
    }
  }

  /** 下载到 out，返回大小是否与 size 一致。前 10 秒平均速度 <30KB/s 视为太慢，抛出以换下一个源 */
  private fun fetch(url: String, out: File, size: Long, cancel: () -> Boolean, prog: (Long, Long) -> Unit): Boolean {
    val c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 15000; instanceFollowRedirects = true; setRequestProperty("User-Agent", "DahuaViewer") }
    if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
    val t0 = System.currentTimeMillis(); var done = 0L; var lastUi = 0L
    c.inputStream.use { inp -> out.outputStream().use { o ->
      val buf = ByteArray(64 * 1024)
      while (true) {
        if (cancel()) throw IOException("已取消")
        val n = inp.read(buf); if (n < 0) break
        o.write(buf, 0, n); done += n
        val el = System.currentTimeMillis() - t0
        if (el > 10_000 && done * 1000 / el < 30 * 1024 && done < size / 2) throw IOException("速度太慢")
        if (System.currentTimeMillis() - lastUi > 300) { lastUi = System.currentTimeMillis(); prog(done, if (el > 0) done * 1000 / el / 1024 else 0) }
      }
    } }
    return out.length() == size
  }

  private fun expectedSha(c: Context, r: Rel): String? {
    Regex("(?i)sha-?256[^0-9a-f]*([0-9a-f]{64})").find(r.body)?.let { return it.groupValues[1] }
    val u = r.shaUrl ?: return null
    for (url in listOf(u) + mirrorList(c).map { it + u }) try {
      val cn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 10000 }
      if (cn.responseCode == 200) Regex("[0-9a-fA-F]{64}").find(cn.inputStream.bufferedReader().readText())?.let { return it.value }
    } catch (_: Exception) {}
    return null
  }
  private fun sha256(f: File): String { val md = MessageDigest.getInstance("SHA-256")
    f.inputStream().use { val b = ByteArray(65536); while (true) { val n = it.read(b); if (n < 0) break; md.update(b, 0, n) } }
    return md.digest().joinToString("") { "%02x".format(it) } }

  fun install(a: Activity, f: File) {
    if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls()) {
      MaterialAlertDialogBuilder(a).setTitle("需要允许安装").setMessage("请在接下来的页面打开「允许来自此来源的应用」，返回后再点一次「安装」。")
        .setPositiveButton("去设置") { _, _ -> a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}"))) }
        .setNeutralButton("我已允许，安装") { _, _ -> install(a, f) }.setNegativeButton("取消", null).show()
      return
    }
    val uri = FileProvider.getUriForFile(a, "${a.packageName}.files", f)
    a.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
  }
}
