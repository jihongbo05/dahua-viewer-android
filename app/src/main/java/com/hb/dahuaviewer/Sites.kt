package com.hb.dahuaviewer
import android.content.Context

/** 多站点：站点1 沿用旧版 "cfg"/"sec"（零迁移，旧数据/云同步不受影响）；其他站点用 "cfg_sN"/"sec_sN" */
object Sites {
  @Volatile var cur = 1; private set
  @Volatile var count = 64; private set
  private fun g(c: Context) = c.getSharedPreferences("sites", 0)
  fun ids(c: Context) = g(c).getString("ids", "1")!!.split(",").mapNotNull { it.toIntOrNull() }
  fun name(c: Context, id: Int) = g(c).getString("name_$id", null) ?: if (id == 1) "BBAC 库房" else "站点$id"
  fun count(c: Context, id: Int) = g(c).getInt("count_$id", if (id == 1) 64 else 32)
  fun prefsName(id: Int) = if (id == 1) "cfg" else "cfg_s$id"
  fun secName(id: Int) = if (id == 1) "sec" else "sec_s$id"
  fun init(c: Context) {
    val p = g(c)
    if (!p.getBoolean("migrated_170", false)) {
      // 预置「库房2」（DH-NVR4232-HDS2/H，32 路）；密码留空由用户在 App 内输入
      if (2 !in ids(c)) {
        p.edit().putString("ids", (ids(c) + 2).joinToString(",")).putString("name_2", "库房2").putInt("count_2", 32).apply()
        c.getSharedPreferences(prefsName(2), 0).edit()
          .putString("pub_h", "222.129.164.48").putInt("pub_p", 38554)
          .putString("lan_h", "192.168.1.6").putInt("lan_p", 554)
          .putString("host", "222.129.164.48").putInt("port", 38554).putString("active", "pub")
          .putString("user", "use").putBoolean("ok", true).apply()
      }
      p.edit().putBoolean("migrated_170", true).apply()
    }
    cur = p.getInt("cur", 1).takeIf { it in ids(c) } ?: 1
    count = count(c, cur)
  }
  fun switch(c: Context, id: Int) { g(c).edit().putInt("cur", id).apply(); init(c); Thumbs.online.clear() }
  fun add(c: Context, n: String, cnt: Int): Int {
    val id = (ids(c).maxOrNull() ?: 1) + 1
    g(c).edit().putString("ids", (ids(c) + id).joinToString(",")).putString("name_$id", n).putInt("count_$id", cnt).apply()
    c.getSharedPreferences(prefsName(id), 0).edit().putString("user", "use").apply(); return id
  }
  fun edit(c: Context, id: Int, n: String, cnt: Int) { g(c).edit().putString("name_$id", n).putInt("count_$id", cnt.coerceIn(1, 256)).apply(); if (id == cur) count = count(c, id) }
  fun remove(c: Context, id: Int) {
    if (id == 1) return
    g(c).edit().putString("ids", (ids(c) - id).joinToString(",")).remove("name_$id").remove("count_$id").apply()
    c.getSharedPreferences(prefsName(id), 0).edit().clear().apply(); c.getSharedPreferences(secName(id), 0).edit().clear().apply()
    if (cur == id) switch(c, 1)
  }
}
