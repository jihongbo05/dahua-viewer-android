package com.hb.dahuaviewer
import android.graphics.Color
import android.os.Build
import android.text.InputType
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class MinePage(a: MainActivity) : Page(a) {
  private fun field(parent: LinearLayout, hint: String, v: String, type: Int, pwd: Boolean = false, weight: Float = 0f, row: LinearLayout? = null): TextInputEditText {
    val til = TextInputLayout(a).apply { this.hint = hint; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
      val r = a.dp(12).toFloat(); setBoxCornerRadii(r, r, r, r); if (pwd) endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE }
    val et = TextInputEditText(til.context).apply { setText(v); inputType = type; setSingleLine() }
    til.addView(et)
    if (row != null) row.addView(til, LinearLayout.LayoutParams(0, -2, weight).apply { marginEnd = a.dp(6) })
    else parent.addView(til, LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.dp(8) })
    return et
  }
  private fun section(root: LinearLayout, title: String): LinearLayout {
    val c = a.card(); val l = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(16)) }
    l.addView(a.tv(title, 16f, TEXT1, true)); c.addView(l)
    root.addView(c, LinearLayout.LayoutParams(-1, -2).apply { setMargins(a.dp(8), a.dp(4), a.dp(8), a.dp(4)) }); return l
  }
  private fun switch(parent: LinearLayout, title: String, desc: String, on: Boolean, f: (MaterialSwitch, Boolean) -> Unit) {
    val row = LinearLayout(a).apply { gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, a.dp(10), 0, 0) }
    val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    col.addView(a.tv(title, 14f)); col.addView(a.tv(desc, 12f, TEXT2))
    row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
    val sw = MaterialSwitch(a).apply { isChecked = on }; sw.setOnCheckedChangeListener { v, c -> f(v as MaterialSwitch, c) }
    row.addView(sw); parent.addView(row)
  }
  private var tp: MediaPlayer? = null
  private val cacheTv = a.tv("", 13f, TEXT2)
  private val activeTv = a.tv("", 13f, TEXT2)
  private var modeViews: List<View> = emptyList()
  override val view: View
  init {
    val root = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(8), a.dp(12), a.dp(8), a.dp(24)) }
    root.addView(a.tv("我的", 22f, TEXT1, true).apply { setPadding(a.dp(8), 0, 0, a.dp(8)) })
    val s1 = section(root, "连接地址")
    var auto = Cfg.flag(a, "auto", true)
    switch(s1, "地址自适应", "按 公网 → 内网 → 域名 顺序快速检测（2秒），自动用第一个能连上的", auto) { _, c -> auto = c; Cfg.setFlag(a, "auto", c); updMode() }
    s1.addView(activeTv.apply { setPadding(0, a.dp(8), 0, 0) })
    val profs = Cfg.profiles(a)
    val hostE = HashMap<String, TextInputEditText>(); val portE = HashMap<String, TextInputEditText>()
    for (pr in profs) {
      val row = LinearLayout(a).apply { setPadding(0, a.dp(8), 0, 0) }
      hostE[pr.key] = field(s1, pr.label + (if (pr.key == "ddns") "（可空）" else "") + " 地址", pr.host, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, weight = 2.2f, row = row)
      portE[pr.key] = field(s1, "端口", pr.port.toString(), InputType.TYPE_CLASS_NUMBER, weight = 1f, row = row)
      s1.addView(row)
    }
    s1.addView(a.tv("手动模式下使用：", 12f, TEXT2).apply { setPadding(0, a.dp(10), 0, 0); tag = "manual" })
    val manual = MaterialButtonToggleGroup(a).apply { isSingleSelection = true; tag = "manual" }
    profs.forEach { pr -> manual.addView(MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { id = View.generateViewId(); text = pr.label; tag = pr.key; textSize = 12f }, LinearLayout.LayoutParams(0, -2, 1f)) }
    for (i in 0 until manual.childCount) if (manual.getChildAt(i).tag == Cfg.active(a).ifEmpty { "pub" }) manual.check(manual.getChildAt(i).id)
    s1.addView(manual)
    modeViews = listOf(s1.findViewWithTag<View>("manual"), manual)
    val s1b = section(root, "账号")
    val user = field(s1b, "用户名", Cfg.user(a), InputType.TYPE_CLASS_TEXT)
    val pw = field(s1b, "密码", Cfg.pass(a), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, true)
    val st = a.tv("", 13f, TEXT2).apply { setPadding(0, a.dp(8), 0, 0) }
    val video = VLCVideoLayout(a)
    val videoBox = Ratio(a).apply { setBackgroundColor(Color.BLACK); addView(video, FrameLayout.LayoutParams(-1, -1)); visibility = View.GONE }
    val btnRow = LinearLayout(a)
    val test = MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = "测试连接" }
    val save = MaterialButton(a).apply { text = "保存" }
    btnRow.addView(test, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = a.dp(8) }); btnRow.addView(save, LinearLayout.LayoutParams(0, -2, 1f))
    s1b.addView(btnRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.dp(12) }); s1b.addView(st)
    s1b.addView(videoBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.dp(8) })
    fun persist(): Boolean {
      for (pr in profs) Cfg.saveProf(a, pr.key, hostE[pr.key]!!.text.toString().trim(), portE[pr.key]!!.text.toString().toIntOrNull() ?: 554)
      val key = (0 until manual.childCount).map { manual.getChildAt(it) }.firstOrNull { it.id == manual.checkedButtonId }?.tag as? String ?: "pub"
      val sel = Cfg.profiles(a).first { it.key == key }
      Cfg.save(a, sel.host, sel.port, user.text.toString().trim(), pw.text.toString(), Cfg.caching(a)); Cfg.setActive(a, sel)
      return true
    }
    test.setOnClickListener {
      persist(); stopTest(); st.setTextColor(TEXT2); st.text = "正在检测地址…"; videoBox.visibility = View.VISIBLE
      a.checkOnline { pr ->
        updActive(pr)
        if (pr == null) { st.setTextColor(0xFFD93025.toInt()); st.text = "所有地址都连不上：公网IP可能已变化（路由器重拨），或网络断开"; return@checkOnline }
        st.text = "地址可达（${pr.label}），正在拉取通道1画面…"
        val p = MediaPlayer(Vlc.get(a)); tp = p; p.attachViews(video, null, false, true)
        val m = Media(Vlc.get(a), android.net.Uri.parse(Cfg.url(a, 1, 1)))
        m.addOption(":rtsp-tcp"); m.addOption(":network-caching=300"); p.media = m; m.release()
        p.setEventListener { e -> when (e.type) {
          MediaPlayer.Event.Vout -> if (e.voutCount > 0) { st.setTextColor(0xFF1E9E4A.toInt()); st.text = "连接成功（${pr.label}），已出画面" }
          MediaPlayer.Event.EncounteredError -> { st.setTextColor(0xFFD93025.toInt()); st.text = "地址通但取流失败：请检查用户名/密码" } } }
        p.play()
      }
    }
    save.setOnClickListener { persist(); Thumbs.clear(a); stopTest(); videoBox.visibility = View.GONE; Toast.makeText(a, "已保存", Toast.LENGTH_SHORT).show(); a.goHome() }

    val s2 = section(root, "播放")
    val cacheLbl = a.tv("网络缓冲：${Cfg.caching(a)} ms（卡顿可调大，800~1500）", 13f, TEXT2).apply { setPadding(0, a.dp(8), 0, 0) }
    s2.addView(cacheLbl)
    s2.addView(Slider(a).apply { valueFrom = 100f; valueTo = 2000f; stepSize = 100f; value = (Cfg.caching(a).coerceIn(100, 2000) / 100 * 100).toFloat()
      addOnChangeListener { _, v, _ -> Cfg.setCaching(a, v.toInt()); cacheLbl.text = "网络缓冲：${v.toInt()} ms（卡顿可调大，800~1500）" } })
    switch(s2, "保持屏幕常亮", "实时预览和回放时不自动锁屏", Cfg.flag(a, "keep", true)) { _, c -> Cfg.setFlag(a, "keep", c) }
    s2.addView(a.tv("实时预览会记住上次选的清晰度（高清/流畅）；移动数据下选高清会提醒流量。", 12f, TEXT2).apply { setPadding(0, a.dp(8), 0, 0) })

    val s3 = section(root, "提醒与安全")
    switch(s3, "掉线提醒", "约每15分钟检测一次录像机地址，连不上/恢复时发通知（省电，仅端口检测）", Cfg.flag(a, "notify", false)) { _, c ->
      Cfg.setFlag(a, "notify", c); Net.schedule(a, c)
      if (c && Build.VERSION.SDK_INT >= 33) a.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9) }
    switch(s3, "应用锁", "打开App时需要指纹/面容或锁屏密码", Cfg.flag(a, "lock", false)) { sw, c ->
      val au = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
      if (c && androidx.biometric.BiometricManager.from(a).canAuthenticate(au) != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
        sw.isChecked = false; Toast.makeText(a, "本机没有设置指纹或锁屏密码，无法开启", Toast.LENGTH_LONG).show() }
      else { Cfg.setFlag(a, "lock", c); MainActivity.unlocked = true } }

    val s4 = section(root, "存储")
    s4.addView(cacheTv.apply { setPadding(0, a.dp(8), 0, a.dp(4)) })
    s4.addView(MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = "清除缓存"
      setOnClickListener { Thumbs.clear(a); updCache(); Toast.makeText(a, "缓存已清除", Toast.LENGTH_SHORT).show() } })
    s4.addView(a.tv("截图：相册/图片/大华监控截图；录像和回放片段：相册/Movies/大华监控", 12f, TEXT2).apply { setPadding(0, a.dp(6), 0, 0) })
    val s5 = section(root, "更新")
    s5.addView(a.tv("当前版本 v${BuildConfig.VERSION_NAME}", 14f, TEXT1).apply { setPadding(0, a.dp(8), 0, 0) })
    switch(s5, "自动检查更新", "打开App时检查（最多每12小时一次）", Updater.auto(a)) { _, c -> Updater.setAuto(a, c) }
    val mir = field(s5, "下载镜像前缀（每行一个，GitHub 下载失败或太慢时依次尝试）", Updater.mirrors(a), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
    mir.isSingleLine = false; mir.maxLines = 4
    s5.addView(a.tv("镜像为第三方免费服务，可能失效；安装包会校验大小和 SHA-256。", 12f, TEXT2).apply { setPadding(0, a.dp(4), 0, 0) })
    s5.addView(MaterialButton(a).apply { text = "检查更新"; setOnClickListener { Updater.setMirrors(a, mir.text.toString()); Updater.check(a, true) } },
      LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.dp(8) })
    val s6 = section(root, "关于")
    s6.addView(a.tv("大华监控  v${BuildConfig.VERSION_NAME}", 14f, TEXT1).apply { setPadding(0, a.dp(8), 0, a.dp(4)) })
    s6.addView(a.tv("开源的大华录像机手机查看工具，RTSP（TCP）直连录像机。支持实时预览、录像回放、轮巡、分组、电子放大、本地录像、掉线提醒、在线更新。密码用系统密钥库加密只保存在本机。\n项目地址：github.com/${Updater.REPO}", 12f, TEXT2))
    view = ScrollView(a).apply { addView(root) }
    updMode()
  }
  private fun updMode() { val auto = Cfg.flag(a, "auto", true); modeViews.forEach { it.visibility = if (auto) View.GONE else View.VISIBLE }; updActive(null, true) }
  private fun updActive(pr: Cfg.Prof?, fromCfg: Boolean = false) {
    val cur = Cfg.profiles(a).firstOrNull { it.key == Cfg.active(a) }
    activeTv.text = if (!fromCfg && pr == null) "当前：全部不可达（公网IP可能变了）" else "当前使用：${(pr ?: cur)?.label ?: "公网"}  ${Cfg.host(a)}:${Cfg.port(a)}"
  }
  private fun updCache() { cacheTv.text = "缩略图缓存：%.1f MB".format(Thumbs.size(a) / 1048576.0) }
  private fun stopTest() { tp?.let { it.setEventListener(null); it.stop(); it.detachViews(); it.release() }; tp = null }
  override fun onShow() { updCache(); updActive(null, true) }
  override fun onHide() { stopTest() }
}
