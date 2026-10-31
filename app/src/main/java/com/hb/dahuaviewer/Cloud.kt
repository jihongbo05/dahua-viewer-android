package com.hb.dahuaviewer
import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

/**
 * 云端配置：地址/账号可预填；密码以 AES-GCM 密文（cloud-config.enc）随 Release 下发，不进安装包。
 * 通道名/分组/收藏经 channels.json 拉取合并；若加密配置含 sync_token 则可自动推送。
 * 密钥拆分混淆，非高安全【待验证】。
 */
object Cloud {
  @JvmField @Volatile var enabled = true
  const val CHANNELS_RAW = "https://raw.githubusercontent.com/jihongbo05/dahua-viewer-android/main/channels.json"
  const val CONFIG_ASSET = "cloud-config.enc"
  // 推荐连接（可进包；密码不进包）
  const val DEF_PUB_HOST = "222.129.161.254"; const val DEF_PUB_PORT = 28554
  const val DEF_LAN_HOST = "192.168.9.45"; const val DEF_LAN_PORT = 554
  const val DEF_USER = "use"

  /** 密钥：两段 XOR + 盐，与 gen-cloud-config.py 一致 */
  fun keyBytes(): ByteArray {
    val a = byteArrayOf(0x4A, 0x68, 0x44, 0x76, 0x41, 0x70, 0x50, 0x77, 0x4B, 0x39, 0x6D, 0x52, 0x73, 0x58, 0x71, 0x2B)
    val b = byteArrayOf(0x7C, 0x31, 0xE9.toByte(), 0xA4.toByte(), 0x55, 0x0B, 0xD2.toByte(), 0x68, 0x3F, 0x9A.toByte(), 0x14, 0xC7.toByte(), 0xE0.toByte(), 0x5D, 0x82.toByte(), 0x6F)
    return ByteArray(16) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }
  }

  fun encrypt(plain: ByteArray): ByteArray {
    val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val c = Cipher.getInstance("AES/GCM/NoPadding")
    c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes(), "AES"), GCMParameterSpec(128, iv))
    val out = c.doFinal(plain)
    return iv + out
  }
  fun decrypt(blob: ByteArray): ByteArray {
    require(blob.size > 12) { "bad blob" }
    val iv = blob.copyOfRange(0, 12); val ct = blob.copyOfRange(12, blob.size)
    val c = Cipher.getInstance("AES/GCM/NoPadding")
    c.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes(), "AES"), GCMParameterSpec(128, iv))
    return c.doFinal(ct)
  }

  private fun httpGet(url: String, token: String? = null): ByteArray? = try {
    val u = URL(url); val conn = (u.openConnection() as HttpURLConnection).apply {
      connectTimeout = 8000; readTimeout = 15000; instanceFollowRedirects = true
      setRequestProperty("User-Agent", "DahuaViewer/${BuildConfig.VERSION_NAME}")
      if (token != null) setRequestProperty("Authorization", "Bearer $token")
    }
    if (conn.responseCode !in 200..299) null else conn.inputStream.use { it.readBytes() }
  } catch (_: Exception) { null }

  private fun mirrorUrls(raw: String): List<String> = listOf(raw) +
    listOf("https://ghfast.top/", "https://gh-proxy.com/").map { "$it$raw" }

  data class CloudCfg(val pubHost: String, val pubPort: Int, val lanHost: String, val lanPort: Int,
    val user: String, val pass: String, val syncToken: String?, val updatedAt: Long)

  fun parseConfig(json: String): CloudCfg {
    val o = JSONObject(json)
    return CloudCfg(o.optString("pub_host", DEF_PUB_HOST), o.optInt("pub_port", DEF_PUB_PORT),
      o.optString("lan_host", DEF_LAN_HOST), o.optInt("lan_port", DEF_LAN_PORT),
      o.optString("user", DEF_USER), o.optString("pass", ""),
      o.optString("sync_token", "").ifBlank { null }, o.optLong("updatedAt", 0))
  }

  /** 首次/本机无密码时从最新 Release 拉 cloud-config.enc */
  fun fetchAndApply(c: Context, force: Boolean = false, done: ((String) -> Unit)? = null) {
    thread {
      val msg = try {
        if (!force && Cfg.pass(c).isNotEmpty() && Cfg.configured(c)) { "本机已有配置，跳过云端密码"; return@thread done?.invoke("本机已有配置") ?: Unit }
        // 1) latest release asset
        val api = "https://api.github.com/repos/${Updater.REPO}/releases/latest"
        var blob: ByteArray? = null
        for (u in mirrorUrls(api)) {
          val body = httpGet(u)?.toString(Charsets.UTF_8) ?: continue
          val rel = try { JSONObject(body) } catch (_: Exception) { continue }
          val assets = rel.optJSONArray("assets") ?: continue
          var url: String? = null
          for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name") == CONFIG_ASSET) { url = a.getString("browser_download_url"); break }
          }
          if (url != null) {
            for (du in mirrorUrls(url)) { blob = httpGet(du); if (blob != null) break }
          }
          if (blob != null) break
        }
        if (blob == null) { "未找到云端配置包，请手动填密码"; return@thread done?.invoke("未找到云端配置") ?: Unit }
        val cfg = parseConfig(String(decrypt(blob), Charsets.UTF_8))
        applyDefaults(c, cfg)
        if (cfg.pass.isNotEmpty()) Cfg.setPass(c, cfg.pass)
        if (cfg.syncToken != null) Cfg.setSyncToken(c, cfg.syncToken)
        Cfg.markConfigured(c)
        "已从云端填入地址与账号" + if (cfg.pass.isNotEmpty()) "（含密码）" else ""
      } catch (e: Exception) { "云端配置失败：${e.message}" }
      android.os.Handler(android.os.Looper.getMainLooper()).post { done?.invoke(msg) }
    }
  }

  fun applyDefaults(c: Context, cfg: CloudCfg? = null) {
    val pubH = cfg?.pubHost ?: DEF_PUB_HOST; val pubP = cfg?.pubPort ?: DEF_PUB_PORT
    val lanH = cfg?.lanHost ?: DEF_LAN_HOST; val lanP = cfg?.lanPort ?: DEF_LAN_PORT
    val u = cfg?.user ?: DEF_USER
    if (Cfg.profiles(c).first { it.key == "pub" }.host.isBlank()) Cfg.saveProf(c, "pub", pubH, pubP)
    if (Cfg.profiles(c).first { it.key == "lan" }.host.isBlank()) Cfg.saveProf(c, "lan", lanH, lanP)
    if (Cfg.user(c).isBlank() || Cfg.user(c) == "use") Cfg.setUser(c, u)
    // 若尚未选定活动地址，默认公网
    if (Cfg.host(c).isBlank()) {
      Cfg.setActive(c, Cfg.Prof("pub", "公网", pubH, pubP))
      // 只写明文 prefs，避免首次启动就碰密钥库
      c.getSharedPreferences("cfg", 0).edit().putString("host", pubH).putInt("port", pubP).putString("user", u).putBoolean("ok", true).apply()
    }
  }

  // ---- 通道名/分组/收藏 ----
  fun exportChannels(c: Context): String {
    val names = JSONObject()
    for (i in 1..Cfg.COUNT) { val n = Cfg.name(c, i); if (n != "通道$i") names.put("$i", n) }
    val groups = JSONObject()
    for (g in Cfg.groups(c)) groups.put(g, JSONArray(Cfg.groupChs(c, g)))
    return JSONObject().put("updatedAt", System.currentTimeMillis())
      .put("names", names).put("groups", groups)
      .put("favs", JSONArray(Cfg.favs(c).sorted())).toString(2)
  }

  fun importChannels(c: Context, json: String, preferRemote: Boolean = false): Boolean {
    val o = JSONObject(json)
    val remoteTs = o.optLong("updatedAt", 0)
    val localTs = Cfg.metaTs(c)
    if (!preferRemote && localTs >= remoteTs && localTs > 0) return false
    val names = o.optJSONObject("names")
    if (names != null) {
      val keys = names.keys()
      while (keys.hasNext()) { val k = keys.next(); k.toIntOrNull()?.let { Cfg.rename(c, it, names.getString(k)) } }
    }
    val groups = o.optJSONObject("groups")
    if (groups != null) {
      val existing = Cfg.groups(c).toSet()
      val keys = groups.keys()
      while (keys.hasNext()) {
        val g = keys.next(); Cfg.addGroup(c, g)
        val arr = groups.getJSONArray(g); val list = (0 until arr.length()).mapNotNull { arr.optInt(it).takeIf { n -> n > 0 } }
        Cfg.setGroupChs(c, g, list)
      }
      // 不删除云端没有的本地分组，避免误伤
      existing.forEach { /* keep */ }
    }
    val favs = o.optJSONArray("favs")
    if (favs != null) {
      val set = (0 until favs.length()).mapNotNull { favs.optInt(it).takeIf { n -> n > 0 } }.toSet()
      Cfg.setFavs(c, set)
    }
    Cfg.setMetaTs(c, maxOf(remoteTs, System.currentTimeMillis()))
    return true
  }

  fun pullChannels(c: Context, done: ((String) -> Unit)? = null) {
    thread {
      val msg = try {
        var body: String? = null
        for (u in mirrorUrls(CHANNELS_RAW)) { body = httpGet(u)?.toString(Charsets.UTF_8); if (body != null) break }
        if (body == null) "拉取失败：网络或文件不存在"
        else if (importChannels(c, body)) "已合并云端通道命名/分组/收藏" else "本地配置更新，无需覆盖"
      } catch (e: Exception) { "拉取失败：${e.message}" }
      android.os.Handler(android.os.Looper.getMainLooper()).post { done?.invoke(msg) }
    }
  }

  /** 有 sync_token 时用 Contents API 更新 channels.json；否则返回需手动导出 */
  fun pushChannels(c: Context, done: ((String) -> Unit)? = null) {
    thread {
      val msg = try {
        val token = Cfg.syncToken(c)
        if (token.isNullOrBlank()) {
          android.os.Handler(android.os.Looper.getMainLooper()).post { done?.invoke("NO_TOKEN") }; return@thread
        }
        val content = exportChannels(c)
        Cfg.setMetaTs(c, System.currentTimeMillis())
        val path = "channels.json"
        val api = "https://api.github.com/repos/${Updater.REPO}/contents/$path"
        val existing = httpGet(api, token)?.toString(Charsets.UTF_8)
        val sha = existing?.let { try { JSONObject(it).optString("sha") } catch (_: Exception) { "" } } ?: ""
        val body = JSONObject().put("message", "sync channels ${System.currentTimeMillis()}")
          .put("content", Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP))
          .put("branch", "main")
        if (sha.isNotBlank()) body.put("sha", sha)
        val conn = (URL(api).openConnection() as HttpURLConnection).apply {
          requestMethod = "PUT"; doOutput = true; connectTimeout = 10000; readTimeout = 20000
          setRequestProperty("Authorization", "Bearer $token")
          setRequestProperty("Accept", "application/vnd.github+json")
          setRequestProperty("Content-Type", "application/json")
          setRequestProperty("User-Agent", "DahuaViewer")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        if (code in 200..299) "已同步到云端，其他设备打开 App 即可拉取" else "推送失败 HTTP $code"
      } catch (e: Exception) { "推送失败：${e.message}" }
      android.os.Handler(android.os.Looper.getMainLooper()).post { done?.invoke(msg) }
    }
  }
}
