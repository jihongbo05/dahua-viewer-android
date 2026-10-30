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
  private val outlined = com.google.android.material.R.attr.materialButtonOutlinedStyle
  private fun tonalBtn() = MaterialButton(a).apply { backgroundTintList = android.content.res.ColorStateList.valueOf(soft(ACCENT, 0x24)); setTextColor(ACCENT); elevation = 0f; stateListAnimator = null }
  /** 紧凑输入框：浅灰填充、圆角 10、高 46dp */
  private fun box(hint: String, v: String, type: Int) = EditText(a).apply {
    this.hint = hint; setText(v); inputType = type; setSingleLine(); textSize = 15f; setTextColor(TEXT1); setHintTextColor(TEXT2)
    background = a.round(FILL, 10); setPadding(a.dp(12), 0, a.dp(12), 0); minHeight = a.dp(46) }
  private fun field(parent: LinearLayout, hint: String, v: String, type: Int, pwd: Boolean = false): TextInputEditText {
    val til = TextInputLayout(a).apply { this.hint = hint; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE; boxStrokeColor = ACCENT
      val r = a.dp(10).toFloat(); setBoxCornerRadii(r, r, r, r); if (pwd) endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE }
    val et = TextInputEditText(til.context).apply { setText(v); inputType = type; setSingleLine() }
    til.addView(et); parent.addView(til, LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.dp(10) }); return et
  }
  /** iOS 风格分组：卡片外灰色小标题 + 卡片 + 卡片下说明 */
  private fun section(root: LinearLayout, title: String, footer: String? = null): LinearLayout {
    root.addView(a.tv(title, 13f, TEXT2, true).apply { setPadding(a.dp(28), a.dp(22), a.dp(16), a.dp(8)); letterSpacing = 0.04f })
    val c = a.card(); val l = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(16), a.dp(6), a.dp(16), a.dp(14)) }
    c.addView(l); root.addView(c, LinearLayout.LayoutParams(-1, -2).apply { setMargins(a.dp(16), 0, a.dp(16), 0) })
    footer?.let { root.addView(a.tv(it, 12f, TEXT2).apply { setPadding(a.dp(28), a.dp(6), a.dp(24), 0); setLineSpacing(0f, 1.15f) }) }
    return l
  }
  private fun divider(parent: LinearLayout) = parent.addView(View(a).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(-1, maxOf(1, a.dp(1) / 2)).apply { topMargin = a.dp(8) })
  private fun switch(parent: LinearLayout, title: String, desc: String, on: Boolean, f: (MaterialSwitch, Boolean) -> Unit) {
    if (parent.childCount > 0) divider(parent)
    val row = LinearLayout(a).apply { gravity = android.view.Gravity.CENTER_VERTICAL; minimumHeight = a.dp(52); setPadding(0, a.dp(8), 0, 0) }
    val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    col.addView(a.tv(title, 15f, TEXT1)); col.addView(a.tv(desc, 12f, TEXT2).apply { setPadding(0, a.dp(2), a.dp(8), 0) })
    row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
    val sw = MaterialSwitch(a).apply { isChecked = on }; sw.setOnCheckedChangeListener { v, c -> f(v as MaterialSwitch, c) }
    row.addView(sw); row.setOnClickListener { sw.toggle() }; parent.addView(row)
  }
  /** 可折叠行：点标题展开/收起 */
  private fun fold(parent: LinearLayout, title: String, body: LinearLayout) {
    if (parent.childCount > 0) divider(parent)
    val arrow = a.tv("›", 20f, TEXT2)
    val row = LinearLayout(a).apply { gravity = android.view.Gravity.CENTER_VERTICAL; minimumHeight = a.dp(48); setPadding(0, a.dp(6), 0, 0) }
    row.addView(a.tv(title, 15f, TEXT1), LinearLayout.LayoutParams(0, -2, 1f)); row.addView(arrow)
    body.visibility = View.GONE
    row.setOnClickListener { val open = body.visibility != View.VISIBLE; body.visibility = if (open) View.VISIBLE else View.GONE; arrow.animate().rotation(if (open) 90f else 0f).setDuration(150).start() }
    parent.addView(row); parent.addView(body)
  }
  private fun col() = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
  private var tp: MediaPlayer? = null
  private val cacheTv = a.tv("", 15f, TEXT1)
  private val activeTv = a.tv("", 12f, TEXT2)
  private val statusPill = a.pill("未检测", TEXT2)
  private var modeViews: List<View> = emptyList()
  private val listCard = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
  private val holder = FrameLayout(a)
  private lateinit var mainScroll: View
  private var subView: View? = null
  private val subs = LinkedHashMap<String, LinearLayout>()
  /** 一级列表里的入口行，点进去是二级页 */
  private fun entry(title: String, desc: String, icon: Int, color: Int): LinearLayout {
    val sub = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, a.dp(32)) }; subs[title] = sub
    if (listCard.childCount > 0) listCard.addView(View(a).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(-1, maxOf(1, a.dp(1) / 2)).apply { marginStart = a.dp(60) })
    val r = LinearLayout(a).apply { gravity = android.view.Gravity.CENTER_VERTICAL; minimumHeight = a.dp(60); setPadding(a.dp(14), a.dp(8), a.dp(14), a.dp(8))
      background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(LINE), null, null); setOnClickListener { open(title) } }
    r.addView(FrameLayout(a).apply { background = a.round(color, 8); addView(a.icon(icon, Color.WHITE, 18), FrameLayout.LayoutParams(a.dp(18), a.dp(18), android.view.Gravity.CENTER)) }, LinearLayout.LayoutParams(a.dp(32), a.dp(32)))
    val c = col().apply { setPadding(a.dp(14), 0, a.dp(8), 0) }
    c.addView(a.tv(title, 16f, TEXT1)); c.addView(a.tv(desc, 12f, TEXT2).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
    r.addView(c, LinearLayout.LayoutParams(0, -2, 1f)); r.addView(a.tv("›", 22f, TEXT2))
    listCard.addView(r); return sub
  }
  private fun open(title: String) {
    val sub = subs[title] ?: return; lastTitle = title; subView?.let { holder.removeView(it); subView = null }; stopTest()
    val page = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG); isClickable = true }
    val bar = LinearLayout(a).apply { gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(a.dp(4), a.dp(6), a.dp(16), a.dp(2)) }
    bar.addView(MaterialButton(a, null, com.google.android.material.R.attr.borderlessButtonStyle).apply { text = "‹ 我的"; textSize = 16f; setOnClickListener { back() } }, LinearLayout.LayoutParams(-2, a.dp(48)))
    page.addView(bar)
    page.addView(a.tv(title, 24f, TEXT1, true).apply { setPadding(a.dp(20), 0, 0, 0) })
    (sub.parent as? android.view.ViewGroup)?.removeView(sub)
    page.addView(ScrollView(a).apply { addView(sub); isVerticalScrollBarEnabled = false }, LinearLayout.LayoutParams(-1, 0, 1f))
    val w = a.wide()
    holder.addView(page, FrameLayout.LayoutParams(-1, -1).apply { if (w) marginStart = a.dp(MASTER) }); subView = page
    if (w) { bar.visibility = View.GONE; page.setPadding(0, a.dp(12), 0, 0) }
    page.translationX = a.resources.displayMetrics.widthPixels * 0.25f; page.alpha = 0f
    page.animate().translationX(0f).alpha(1f).setDuration(200).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    mainScroll.visibility = if (w) View.VISIBLE else View.INVISIBLE
  }
  /** 返回上一级；没有二级页时返回 false */
  fun back(): Boolean { if (a.wide()) return false; val v = subView ?: return false; subView = null; mainScroll.visibility = View.VISIBLE
    v.animate().translationX(a.resources.displayMetrics.widthPixels * 0.25f).alpha(0f).setDuration(160).withEndAction { holder.removeView(v) }.start(); stopTest(); return true }
  fun openConn() = open("连接与账号")
  private val MASTER = 360
  private var wasWide = false
  private var lastTitle = "连接与账号"
  /** 大屏主从双栏：左列表固定 360dp，右侧显示二级页 */
  private fun applyMaster() {
    val w = a.wide()
    mainScroll.layoutParams = FrameLayout.LayoutParams(if (w) a.dp(MASTER) else -1, -1)
    if (w && subView == null) open(lastTitle)
    else if (w) subView?.let { v -> (v.layoutParams as FrameLayout.LayoutParams).marginStart = a.dp(MASTER); v.requestLayout(); mainScroll.visibility = View.VISIBLE }
    else if (wasWide) { subView?.let { holder.removeView(it) }; subView = null; mainScroll.visibility = View.VISIBLE }
    wasWide = w
  }
  override val view: View
  init {
    val root = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(0, a.dp(12), 0, a.dp(32)) }
    root.addView(a.tv("我的", 28f, TEXT1, true).apply { setPadding(a.dp(20), 0, 0, 0) })

    // 顶部状态卡
    val head = a.card(); val hl = LinearLayout(a).apply { gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(a.dp(16), a.dp(16), a.dp(16), a.dp(16)) }
    hl.addView(FrameLayout(a).apply { background = a.round(soft(ACCENT, 0x26), 14); addView(a.icon(R.drawable.ic_videocam, ACCENT, 26), FrameLayout.LayoutParams(a.dp(26), a.dp(26), android.view.Gravity.CENTER)) }, LinearLayout.LayoutParams(a.dp(48), a.dp(48)))
    val hc = col().apply { setPadding(a.dp(12), 0, a.dp(8), 0) }
    hc.addView(a.tv("大华录像机", 17f, TEXT1, true)); hc.addView(activeTv.apply { setPadding(0, a.dp(2), 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
    hl.addView(hc, LinearLayout.LayoutParams(0, -2, 1f)); hl.addView(statusPill)
    head.addView(hl); root.addView(head, LinearLayout.LayoutParams(-1, -2).apply { setMargins(a.dp(16), a.dp(14), a.dp(16), 0) })
    head.setOnClickListener { open("连接与账号") }
    val lc = a.card(); lc.addView(listCard); root.addView(lc, LinearLayout.LayoutParams(-1, -2).apply { setMargins(a.dp(16), a.dp(20), a.dp(16), 0) })
    root.addView(a.tv("常用：首页长按摄像头可收藏、改名、分组、看回放", 12f, TEXT2).apply { setPadding(a.dp(28), a.dp(10), a.dp(24), 0) })
    val pConn = entry("连接与账号", "地址、端口、用户名密码、测试连接", R.drawable.ic_videocam, ACCENT)
    val pPlay = entry("播放设置", "网络缓冲、屏幕常亮", R.drawable.ic_play, 0xFF007AFF.toInt())
    val pSafe = entry("提醒与安全", "掉线提醒、应用锁", R.drawable.ic_lens, 0xFF34C759.toInt())
    val pStore = entry("存储", "缩略图缓存、截图录像位置", R.drawable.ic_download, 0xFF8E8E93.toInt())
    val pUpd = entry("更新与关于", "当前 v${BuildConfig.VERSION_NAME} · 检查更新", R.drawable.ic_loop, 0xFFAF52DE.toInt())

    // 连接
    val s1 = section(pConn, "连接", "地址填录像机 IP 或域名，端口一般是 554。公网 IP 路由器重拨后可能会变。")
    var auto = Cfg.flag(a, "auto", true)
    switch(s1, "地址自适应", "按 公网 → 内网 → 域名 顺序检测，自动用第一个能连上的", auto) { _, c -> auto = c; Cfg.setFlag(a, "auto", c); updMode() }
    divider(s1)
    val profs = Cfg.profiles(a)
    val hostE = HashMap<String, EditText>(); val portE = HashMap<String, EditText>()
    val hints = mapOf("pub" to "公网 IP", "lan" to "内网 IP，如 192.168.x.x", "ddns" to "域名（可空）")
    for (pr in profs) {
      val row = LinearLayout(a).apply { gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, a.dp(10), 0, 0) }
      row.addView(a.tv(pr.label, 14f, TEXT1, true), LinearLayout.LayoutParams(a.dp(44), -2))
      val he = box(hints[pr.key] ?: "地址", pr.host, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
      val pe = box("554", if (pr.host.isBlank()) "" else pr.port.toString(), InputType.TYPE_CLASS_NUMBER).apply { gravity = android.view.Gravity.CENTER }
      row.addView(he, LinearLayout.LayoutParams(0, a.dp(46), 1f))
      row.addView(a.tv(":", 16f, TEXT2).apply { setPadding(a.dp(6), 0, a.dp(6), 0) })
      row.addView(pe, LinearLayout.LayoutParams(a.dp(76), a.dp(46)))
      hostE[pr.key] = he; portE[pr.key] = pe; s1.addView(row)
    }
    val manLbl = a.tv("手动模式下使用", 12f, TEXT2).apply { setPadding(0, a.dp(14), 0, a.dp(4)) }
    val manual = MaterialButtonToggleGroup(a).apply { isSingleSelection = true; isSelectionRequired = true }
    profs.forEach { pr -> manual.addView(MaterialButton(a, null, outlined).apply { id = View.generateViewId(); text = pr.label; tag = pr.key; textSize = 13f }, LinearLayout.LayoutParams(0, a.dp(48), 1f)) }
    for (i in 0 until manual.childCount) if (manual.getChildAt(i).tag == Cfg.active(a).ifEmpty { "pub" }) manual.check(manual.getChildAt(i).id)
    s1.addView(manLbl); s1.addView(manual)
    modeViews = listOf(manLbl, manual)

    // 账号
    val s1b = section(pConn, "账号", "密码用系统密钥库加密，只保存在本机。")
    val user = field(s1b, "用户名", Cfg.user(a), InputType.TYPE_CLASS_TEXT)
    val pw = field(s1b, "密码", Cfg.pass(a), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, true)
    // 测试结果状态条
    val stTitle = a.tv("", 14f, TEXT1, true); val stDesc = a.tv("", 12f, TEXT2).apply { setPadding(0, a.dp(2), 0, 0) }
    val stBar = col().apply { setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10)); visibility = View.GONE; addView(stTitle); addView(stDesc) }
    fun state(color: Int, title: String, desc: String) { stBar.visibility = View.VISIBLE; stBar.background = a.round(soft(color, 0x1F), 10)
      stTitle.setTextColor(color); stTitle.text = title; stDesc.text = desc; stDesc.visibility = if (desc.isEmpty()) View.GONE else View.VISIBLE }
    val video = VLCVideoLayout(a)
    val videoBox = Ratio(a).apply { setBackgroundColor(Color.BLACK); addView(video, FrameLayout.LayoutParams(-1, -1)); visibility = View.GONE
      background = a.round(Color.BLACK, 10); clipToOutline = true }
    val save = MaterialButton(a).apply { text = "保存"; textSize = 15f; cornerRadius = a.dp(12) }
    val test = tonalBtn().apply { text = "测试连接"; textSize = 15f; cornerRadius = a.dp(12) }
    val btnRow = LinearLayout(a)
    btnRow.addView(test, LinearLayout.LayoutParams(0, a.dp(50), 1f).apply { marginEnd = a.dp(10) }); btnRow.addView(save, LinearLayout.LayoutParams(0, a.dp(50), 1f))
    s1b.addView(btnRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.dp(14) })
    s1b.addView(stBar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.dp(10) })
    s1b.addView(videoBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.dp(10) })
    fun persist(): Boolean {
      for (pr in profs) Cfg.saveProf(a, pr.key, hostE[pr.key]!!.text.toString().trim(), portE[pr.key]!!.text.toString().toIntOrNull() ?: 554)
      val key = (0 until manual.childCount).map { manual.getChildAt(it) }.firstOrNull { it.id == manual.checkedButtonId }?.tag as? String ?: "pub"
      val sel = Cfg.profiles(a).first { it.key == key }
      Cfg.save(a, sel.host, sel.port, user.text.toString().trim(), pw.text.toString(), Cfg.caching(a)); Cfg.setActive(a, sel)
      return true
    }
    test.setOnClickListener {
      if (profs.all { hostE[it.key]!!.text.isNullOrBlank() }) { state(WARN, "还没填地址", "至少填一个公网或内网地址再测试"); return@setOnClickListener }
      persist(); stopTest(); state(TEXT2, "正在检测地址…", "约需 2～6 秒"); videoBox.visibility = View.GONE; test.isEnabled = false
      statusPill.setPill("检测中", TEXT2)
      a.checkOnline { pr ->
        test.isEnabled = true; updActive(pr)
        if (pr == null) { state(ERR, "连不上录像机", "请检查：手机是否联网；公网 IP 是否变了（路由器重拨）；内网地址是否在同一网络"); return@checkOnline }
        state(ACCENT, "地址可达（${pr.label}），正在拉取画面…", "通道 1 · 流畅"); videoBox.visibility = View.VISIBLE
        val p = MediaPlayer(Vlc.get(a)); tp = p; p.attachViews(video, null, false, true)
        val m = Media(Vlc.get(a), android.net.Uri.parse(Cfg.url(a, 1, 1)))
        m.addOption(":rtsp-tcp"); m.addOption(":network-caching=300"); p.media = m; m.release()
        p.setEventListener { e -> when (e.type) {
          MediaPlayer.Event.Vout -> if (e.voutCount > 0) state(OK, "连接成功", "${pr.label} · 已出画面，点「保存」即可使用")
          MediaPlayer.Event.EncounteredError -> { videoBox.visibility = View.GONE; state(ERR, "地址能通，但取不到画面", "多半是用户名或密码不对，也可能该账号没有预览权限") } } }
        p.play()
      }
    }
    save.setOnClickListener { persist(); Thumbs.clear(a); stopTest(); videoBox.visibility = View.GONE; stBar.visibility = View.GONE; back(); a.snack("已保存"); a.goHome() }

    // 播放
    val s2 = section(pPlay, "播放", "实时预览会记住上次的清晰度；移动数据下选高清会提醒流量。")
    val cacheLbl = a.tv("", 15f, TEXT1).apply { setPadding(0, a.dp(10), 0, 0) }
    fun cl(v: Int) { cacheLbl.text = "网络缓冲  ${v} ms" + if (v < 600) "  · 低延迟" else if (v <= 1500) "  · 均衡" else "  · 更流畅" }
    cl(Cfg.caching(a)); s2.addView(cacheLbl)
    s2.addView(Slider(a).apply { valueFrom = 100f; valueTo = 2000f; stepSize = 100f; value = (Cfg.caching(a).coerceIn(100, 2000) / 100 * 100).toFloat()
      addOnChangeListener { _, v, _ -> Cfg.setCaching(a, v.toInt()); cl(v.toInt()) } })
    s2.addView(a.tv("画面卡顿就往右调（800～1500 比较稳）", 12f, TEXT2))
    switch(s2, "保持屏幕常亮", "实时预览和回放时不自动锁屏", Cfg.flag(a, "keep", true)) { _, c -> Cfg.setFlag(a, "keep", c) }

    // 提醒与安全
    val s3 = section(pSafe, "提醒与安全")
    switch(s3, "掉线提醒", "约每 15 分钟检测一次，连不上或恢复时通知你（仅端口检测，省电）", Cfg.flag(a, "notify", false)) { _, c ->
      Cfg.setFlag(a, "notify", c); Net.schedule(a, c)
      if (c && Build.VERSION.SDK_INT >= 33) a.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9) }
    switch(s3, "应用锁", "打开 App 时需要指纹、面容或锁屏密码", Cfg.flag(a, "lock", false)) { sw, c ->
      val au = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
      if (c && androidx.biometric.BiometricManager.from(a).canAuthenticate(au) != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
        sw.isChecked = false; Toast.makeText(a, "手机还没设置指纹或锁屏密码，先到系统设置里开启", Toast.LENGTH_LONG).show() }
      else { Cfg.setFlag(a, "lock", c); MainActivity.unlocked = true } }

    // 存储
    val s4 = section(pStore, "存储", "截图在 相册/图片/大华监控截图；录像在 相册/Movies/大华监控。")
    val srow = LinearLayout(a).apply { gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, a.dp(8), 0, 0) }
    srow.addView(cacheTv, LinearLayout.LayoutParams(0, -2, 1f))
    srow.addView(tonalBtn().apply { text = "清除"; cornerRadius = a.dp(12)
      setOnClickListener { Thumbs.clear(a); updCache(); a.snack("缓存已清除") } }, LinearLayout.LayoutParams(-2, a.dp(44)))
    s4.addView(srow)

    // 更新
    val s5 = section(pUpd, "更新")
    val vrow = LinearLayout(a).apply { gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, a.dp(8), 0, 0) }
    vrow.addView(a.tv("当前版本", 15f, TEXT1), LinearLayout.LayoutParams(0, -2, 1f)); vrow.addView(a.tv("v${BuildConfig.VERSION_NAME}", 15f, TEXT2))
    s5.addView(vrow)
    switch(s5, "自动检查更新", "打开 App 时检查（最多每 12 小时一次）", Updater.auto(a)) { _, c -> Updater.setAuto(a, c) }
    val adv = col()
    val mir = box("每行一个镜像前缀", Updater.mirrors(a), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE).apply {
      isSingleLine = false; maxLines = 4; minLines = 2; gravity = android.view.Gravity.TOP; setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10)); textSize = 13f }
    adv.addView(a.tv("GitHub 下载慢或失败时依次尝试。镜像是第三方免费服务，可能失效；安装包会校验大小和 SHA-256。", 12f, TEXT2).apply { setPadding(0, a.dp(4), 0, a.dp(8)) })
    adv.addView(mir, LinearLayout.LayoutParams(-1, -2))
    fold(s5, "下载镜像（高级）", adv)
    s5.addView(MaterialButton(a).apply { text = "检查更新"; textSize = 15f; cornerRadius = a.dp(12); setOnClickListener { Updater.setMirrors(a, mir.text.toString()); Updater.check(a, true) } },
      LinearLayout.LayoutParams(-1, a.dp(50)).apply { topMargin = a.dp(12) })

    // 关于
    val s6 = section(pUpd, "关于")
    val ab = col(); ab.addView(a.tv("开源的大华录像机手机查看工具，RTSP（TCP）直连录像机。支持实时预览、录像回放、轮巡、分组、电子放大、本地录像、掉线提醒、在线更新。\n项目地址：github.com/${Updater.REPO}", 12f, TEXT2).apply { setPadding(0, a.dp(4), 0, a.dp(4)); setLineSpacing(0f, 1.2f) })
    fold(s6, "大华监控 v${BuildConfig.VERSION_NAME}", ab)
    mainScroll = ScrollView(a).apply { addView(root); isVerticalScrollBarEnabled = false }
    holder.addView(mainScroll, FrameLayout.LayoutParams(-1, -1)); view = holder
    updMode()
  }
  private fun updMode() { val auto = Cfg.flag(a, "auto", true); modeViews.forEach { it.visibility = if (auto) View.GONE else View.VISIBLE }; updActive(null, true) }
  private fun updActive(pr: Cfg.Prof?, fromCfg: Boolean = false) {
    val cur = Cfg.profiles(a).firstOrNull { it.key == Cfg.active(a) }
    if (!Cfg.configured(a) || Cfg.host(a).isBlank()) { activeTv.text = "还没设置地址，请在下方填写"; statusPill.setPill("未设置", WARN); return }
    activeTv.text = if (!fromCfg && pr == null) "所有地址都连不上" else "${(pr ?: cur)?.label ?: "公网"} · ${Cfg.host(a)}:${Cfg.port(a)}"
    if (!fromCfg) statusPill.setPill(if (pr == null) "离线" else "在线", if (pr == null) ERR else OK)
  }
  private fun updCache() { cacheTv.text = "缩略图缓存  %.1f MB".format(Thumbs.size(a) / 1048576.0) }
  private fun stopTest() { tp?.let { it.setEventListener(null); it.stop(); it.detachViews(); it.release() }; tp = null }
  override fun onShow() { applyMaster(); updCache(); updActive(null, true); if (Cfg.configured(a)) a.checkOnline { updActive(it) } }
  override fun onHide() { stopTest() }
}
