package com.hb.dahuaviewer
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
object Cfg {
  private fun p(c: Context) = c.getSharedPreferences("cfg", 0)
  /** 真机用 EncryptedSharedPreferences；Robolectric 等无 AndroidKeyStore 时降级普通 prefs */
  private fun sec(c: Context) = try {
    EncryptedSharedPreferences.create(c, "sec",
      MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
  } catch (_: Throwable) { c.getSharedPreferences("sec_fallback", 0) }
  const val COUNT = 64
  fun host(c: Context) = p(c).getString("host", "")!!
  fun port(c: Context) = p(c).getInt("port", 554)
  fun user(c: Context) = p(c).getString("user", "use")!!
  fun caching(c: Context) = p(c).getInt("cache", 300)
  fun pass(c: Context) = try { sec(c).getString("pw", "")!! } catch (e: Exception) { "" }
  fun configured(c: Context) = p(c).getBoolean("ok", false)
  fun save(c: Context, h: String, po: Int, u: String, pw: String, cache: Int) {
    p(c).edit().putString("host", h).putInt("port", po).putString("user", u).putInt("cache", cache).putBoolean("ok", true).apply()
    sec(c).edit().putString("pw", pw).apply()
  }
  fun name(c: Context, ch: Int): String = p(c).getString("n$ch", null)?.takeIf { it.isNotBlank() } ?: "通道$ch"
  fun favs(c: Context): Set<Int> = (p(c).getString("favs", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.toSet()
  fun isFav(c: Context, ch: Int) = ch in favs(c)
  fun toggleFav(c: Context, ch: Int) { val f = favs(c).toMutableSet(); if (!f.add(ch)) f.remove(ch); p(c).edit().putString("favs", f.sorted().joinToString(",")).putLong("meta_ts", System.currentTimeMillis()).apply() }
  fun setCaching(c: Context, v: Int) = p(c).edit().putInt("cache", v).apply()
  data class Prof(val key: String, val label: String, val host: String, val port: Int)
  fun profiles(c: Context) = listOf(
    Prof("pub", "公网", p(c).getString("pub_h", "")!!, p(c).getInt("pub_p", 554)),
    Prof("lan", "内网", p(c).getString("lan_h", "")!!, p(c).getInt("lan_p", 554)),
    Prof("ddns", "域名/DDNS", p(c).getString("ddns_h", "")!!, p(c).getInt("ddns_p", 554)))
  fun saveProf(c: Context, key: String, h: String, po: Int) = p(c).edit().putString("${key}_h", h).putInt("${key}_p", po).apply()
  fun active(c: Context) = p(c).getString("active", "")!!
  fun setActive(c: Context, pr: Prof) = p(c).edit().putString("host", pr.host).putInt("port", pr.port).putString("active", pr.key).apply()
  fun flag(c: Context, k: String, def: Boolean) = p(c).getBoolean(k, def)
  fun setFlag(c: Context, k: String, v: Boolean) = p(c).edit().putBoolean(k, v).apply()
  fun lastSub(c: Context) = p(c).getInt("lastSub", 0)
  fun setLastSub(c: Context, v: Int) = p(c).edit().putInt("lastSub", v).apply()
  fun groups(c: Context): List<String> = p(c).getString("groups", "")!!.split("\n").filter { it.isNotBlank() }
  fun groupChs(c: Context, g: String): List<Int> = p(c).getString("g_$g", "")!!.split(",").mapNotNull { it.toIntOrNull() }
  fun setGroupChs(c: Context, g: String, l: Collection<Int>) = p(c).edit().putString("g_$g", l.toSortedSet().joinToString(",")).putLong("meta_ts", System.currentTimeMillis()).apply()
  fun addGroup(c: Context, g: String) { if (g.isNotBlank() && g !in groups(c)) p(c).edit().putString("groups", (groups(c) + g).joinToString("\n")).putLong("meta_ts", System.currentTimeMillis()).apply() }
  fun delGroup(c: Context, g: String) = p(c).edit().putString("groups", (groups(c) - g).joinToString("\n")).remove("g_$g").apply()
  private val tf get() = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US)
  fun playUrl(c: Context, ch: Int, start: Date, end: Date) =
    "rtsp://${enc(user(c))}:${enc(pass(c))}@${host(c)}:${port(c)}/cam/playback?channel=$ch&starttime=${tf.format(start)}&endtime=${tf.format(end)}"
  fun rename(c: Context, ch: Int, n: String) { p(c).edit().putString("n$ch", n).putLong("meta_ts", System.currentTimeMillis()).apply() }
  fun setPass(c: Context, pw: String) = sec(c).edit().putString("pw", pw).apply()
  fun setUser(c: Context, u: String) = p(c).edit().putString("user", u).apply()
  fun markConfigured(c: Context) = p(c).edit().putBoolean("ok", true).apply()
  fun setFavs(c: Context, f: Set<Int>) = p(c).edit().putString("favs", f.sorted().joinToString(",")).putLong("meta_ts", System.currentTimeMillis()).apply()
  fun metaTs(c: Context) = p(c).getLong("meta_ts", 0)
  fun setMetaTs(c: Context, t: Long) = p(c).edit().putLong("meta_ts", t).apply()
  fun syncToken(c: Context) = try { sec(c).getString("sync_tok", null) } catch (_: Exception) { null }
  fun setSyncToken(c: Context, t: String) = sec(c).edit().putString("sync_tok", t).apply()
  private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
  fun url(c: Context, ch: Int, sub: Int, h: String = host(c), po: Int = port(c), u: String = user(c), pw: String = pass(c)) =
    "rtsp://${enc(u)}:${enc(pw)}@$h:$po/cam/realmonitor?channel=$ch&subtype=$sub"
}
