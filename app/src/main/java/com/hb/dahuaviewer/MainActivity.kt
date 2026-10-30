package com.hb.dahuaviewer
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
  private lateinit var content: FrameLayout
  private lateinit var nav: BottomNavigationView
  private val h = Handler(Looper.getMainLooper())
  private lateinit var home: HomePage; private lateinit var all: AllPage; private lateinit var mine: MinePage; private lateinit var pb: PlaybackPage
  private var cur: Page? = null
  private lateinit var rail: com.google.android.material.navigationrail.NavigationRailView
  private lateinit var root: LinearLayout
  private var railMode: Boolean? = null
  /** 折叠屏/大屏：左侧导航栏；手机/外屏：底部导航（折叠展开时不重建 Activity，只换导航） */
  private fun applyNav() {
    val w = wide(); if (railMode == w) return; railMode = w
    root.removeAllViews()
    if (w) { root.orientation = LinearLayout.HORIZONTAL; root.addView(rail, LinearLayout.LayoutParams(-2, -1)); root.addView(content, LinearLayout.LayoutParams(0, -1, 1f)) }
    else { root.orientation = LinearLayout.VERTICAL; root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(nav, LinearLayout.LayoutParams(-1, -2)) }
  }

  override fun onCreate(b: Bundle?) {
    setTheme(R.style.Theme_App); initTheme(this)
    super.onCreate(b)
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    content = FrameLayout(this)
    val thumbHost = FrameLayout(this); content.addView(thumbHost, FrameLayout.LayoutParams(-1, -1)); Thumbs.host = thumbHost
    nav = BottomNavigationView(this).apply {
      setBackgroundColor(SURFACE); elevation = 0f; itemActiveIndicatorColor = android.content.res.ColorStateList.valueOf(soft(ACCENT, 0x2E))
      menu.add(0, 1, 0, "首页").setIcon(R.drawable.ic_home)
      menu.add(0, 2, 1, "分屏").setIcon(R.drawable.ic_grid)
      menu.add(0, 4, 2, "回放").setIcon(R.drawable.ic_history)
      menu.add(0, 3, 3, "我的").setIcon(R.drawable.ic_person)
      labelVisibilityMode = com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED
    }
    rail = com.google.android.material.navigationrail.NavigationRailView(this).apply { setBackgroundColor(SURFACE); elevation = 0f
      itemActiveIndicatorColor = android.content.res.ColorStateList.valueOf(soft(ACCENT, 0x2E)); labelVisibilityMode = com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED
      menuGravity = Gravity.CENTER_VERTICAL
      menu.add(0, 1, 0, "首页").setIcon(R.drawable.ic_home); menu.add(0, 2, 1, "分屏").setIcon(R.drawable.ic_grid)
      menu.add(0, 4, 2, "回放").setIcon(R.drawable.ic_history); menu.add(0, 3, 3, "我的").setIcon(R.drawable.ic_person) }
    this.root = root; applyNav()
    setContentView(root)
    home = HomePage(this); all = AllPage(this); mine = MinePage(this); pb = PlaybackPage(this)
    for (pg in listOf(home, all, pb, mine)) { pg.view.setBackgroundColor(BG); pg.view.visibility = View.GONE; content.addView(pg.view, FrameLayout.LayoutParams(-1, -1)) }
    nav.setOnItemSelectedListener { select(it.itemId); if (rail.selectedItemId != it.itemId) rail.selectedItemId = it.itemId; true }
    rail.setOnItemSelectedListener { if (nav.selectedItemId != it.itemId) nav.selectedItemId = it.itemId; true }
    Updater.autoCheck(this)
    if (Cfg.configured(this)) nav.selectedItemId = 1 else { nav.selectedItemId = 3; mine.openConn(); snack("首次使用，请先填写连接信息", true) }
  }
  fun select(id: Int) {
    val n = when (id) { 1 -> home; 2 -> all; 4 -> pb; else -> mine }
    if (n === cur) return
    cur?.let { it.onHide(); it.view.visibility = View.GONE }
    cur = n; n.view.alpha = 0f; n.view.translationY = dp(8).toFloat(); n.view.visibility = View.VISIBLE; n.view.animate().alpha(1f).translationY(0f).setDuration(180).start(); n.onShow()
  }
  fun goHome() { nav.selectedItemId = 1 }
  /** 统一返回：二级页 → 上一级；非首页 Tab → 首页；首页 → 退出 */
  @Deprecated("") override fun onBackPressed() {
    if (lockView != null) { finish(); return }
    if (cur === mine && mine.back()) return
    if (cur !== home) { goHome(); return }
    @Suppress("DEPRECATION") super.onBackPressed()
  }
  fun openPlayback(ch: Int, startMs: Long? = null) { startActivity(Intent(this, PlaybackActivity::class.java).putExtra("ch", ch).apply { startMs?.let { putExtra("t", it) } }); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out) }
  /** 选通道对话框（收藏在前） */
  fun pickChannel(title: String, ok: (Int) -> Unit) {
    val order = Cfg.favs(this).sorted() + (1..Cfg.COUNT).filter { !Cfg.isFav(this, it) }
    MaterialAlertDialogBuilder(this).setTitle(title).setItems(order.map { (if (Cfg.isFav(this, it)) "★ " else "") + "$it  ${Cfg.name(this, it)}" }.toTypedArray()) { _, i -> ok(order[i]) }.setNegativeButton("取消", null).show()
  }
  override fun onResume() { super.onResume(); cur?.onShow()
    if (Cfg.flag(this, "notify", false) && android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9) }
  override fun onPause() { super.onPause(); cur?.onHide() }
  override fun onDestroy() { Thumbs.host = null; Thumbs.cancel(); super.onDestroy() }
  override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); applyNav(); cur?.let { it.onHide(); it.onShow() } }
  fun openPreview(ch: Int) { startActivity(Intent(this, PreviewActivity::class.java).putExtra("ch", ch)); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out) }
  fun rename(ch: Int, done: () -> Unit) {
    val til = TextInputLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0) }
    val et = TextInputEditText(til.context).apply { setText(Cfg.name(this@MainActivity, ch)); setSingleLine() }
    til.addView(et)
    MaterialAlertDialogBuilder(this).setTitle("重命名 通道$ch").setView(til)
      .setPositiveButton("保存") { _, _ -> Cfg.rename(this, ch, et.text.toString().trim()); done() }.setNegativeButton("取消", null).show()
  }
  fun camMenu(anchor: View, ch: Int, done: () -> Unit) {
    PopupMenu(this, anchor).apply {
      val fav = Cfg.isFav(this@MainActivity, ch)
      menu.add(0, 2, 0, if (fav) "取消收藏" else "收藏"); menu.add(0, 1, 1, "重命名"); menu.add(0, 3, 2, "加入分组…")
      menu.add(0, 5, 3, "录像回放"); menu.add(0, 4, 4, "预览码流：" + if (Cfg.lastSub(this@MainActivity) == 0) "高清（点切到流畅）" else "流畅（点切到高清）")
      setOnMenuItemClickListener { when (it.itemId) { 1 -> rename(ch, done); 3 -> assignGroups(ch, done); 5 -> openPlayback(ch)
        4 -> { Cfg.setLastSub(this@MainActivity, 1 - Cfg.lastSub(this@MainActivity)); snack("实时预览默认：" + if (Cfg.lastSub(this@MainActivity) == 0) "高清" else "流畅") }
        else -> { Cfg.toggleFav(this@MainActivity, ch); snack(if (fav) "已取消收藏" else "已收藏"); done() } }; true }
    }.show()
  }
  fun input(title: String, init: String = "", ok: (String) -> Unit) {
    val til = TextInputLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0) }
    val et = TextInputEditText(til.context).apply { setText(init); setSingleLine() }; til.addView(et)
    MaterialAlertDialogBuilder(this).setTitle(title).setView(til).setPositiveButton("确定") { _, _ -> et.text.toString().trim().takeIf { it.isNotEmpty() }?.let(ok) }.setNegativeButton("取消", null).show()
  }
  fun assignGroups(ch: Int, done: () -> Unit) {
    val gs = Cfg.groups(this)
    if (gs.isEmpty()) { input("新建分组（如 仓库、大门）") { g -> Cfg.addGroup(this, g); Cfg.setGroupChs(this, g, listOf(ch)); done() }; return }
    val checked = gs.map { ch in Cfg.groupChs(this, it) }.toBooleanArray()
    MaterialAlertDialogBuilder(this).setTitle("通道$ch 加入分组").setMultiChoiceItems(gs.toTypedArray(), checked) { _, i, v -> checked[i] = v }
      .setPositiveButton("保存") { _, _ -> gs.forEachIndexed { i, g -> val l = Cfg.groupChs(this, g).toMutableSet(); if (checked[i]) l += ch else l -= ch; Cfg.setGroupChs(this, g, l) }; done() }
      .setNeutralButton("新建分组") { _, _ -> input("新建分组") { g -> Cfg.addGroup(this, g); Cfg.setGroupChs(this, g, Cfg.groupChs(this, g) + ch); done() } }
      .setNegativeButton("取消", null).show()
  }
  fun manageGroups(done: () -> Unit) {
    val gs = Cfg.groups(this)
    MaterialAlertDialogBuilder(this).setTitle("分组管理（点分组可删除）")
      .setItems(gs.map { "$it（${Cfg.groupChs(this, it).size} 路）" }.toTypedArray()) { _, i ->
        MaterialAlertDialogBuilder(this).setTitle("删除分组「${gs[i]}」？").setMessage("只删除分组，不影响摄像头").setPositiveButton("删除") { _, _ -> Cfg.delGroup(this, gs[i]); done() }.setNegativeButton("取消", null).show() }
      .setPositiveButton("新建分组") { _, _ -> input("新建分组（长按摄像头可加入分组）") { Cfg.addGroup(this, it); done() } }
      .setNegativeButton("关闭", null).show()
  }
  fun checkOnline(cb: (Cfg.Prof?) -> Unit) { thread { val pr = Net.resolve(this); h.post { cb(pr) } } }
  /* ---- 应用锁 ---- */
  companion object { var unlocked = false; var bgAt = 0L }
  private var lockView: View? = null
  override fun onStart() {
    super.onStart()
    if (Cfg.flag(this, "lock", false) && (!unlocked || System.currentTimeMillis() - bgAt > 60_000)) { unlocked = false; showLock() }
  }
  override fun onStop() { super.onStop(); bgAt = System.currentTimeMillis() }
  private fun showLock() {
    if (lockView == null) {
      val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(BG); isClickable = true
        addView(icon(R.drawable.ic_videocam, ACCENT, 64)); addView(tv("大华监控已锁定", 18f, TEXT1, true).apply { setPadding(0, dp(12), 0, dp(16)) })
        addView(MaterialButton(this@MainActivity).apply { text = "解锁"; setOnClickListener { auth() } }) }
      (window.decorView as ViewGroup).addView(v, ViewGroup.LayoutParams(-1, -1)); lockView = v
    }
    auth()
  }
  private fun auth() {
    val au = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (androidx.biometric.BiometricManager.from(this).canAuthenticate(au) != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
      Toast.makeText(this, "本机未设置指纹/锁屏密码，应用锁暂不生效", Toast.LENGTH_LONG).show(); unlock(); return }
    androidx.biometric.BiometricPrompt(this, mainExecutor, object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(r: androidx.biometric.BiometricPrompt.AuthenticationResult) { unlock() }
    }).authenticate(androidx.biometric.BiometricPrompt.PromptInfo.Builder().setTitle("解锁大华监控").setAllowedAuthenticators(au).build())
  }
  private fun unlock() { unlocked = true; lockView?.let { (window.decorView as ViewGroup).removeView(it) }; lockView = null }
}

abstract class Page(val a: MainActivity) { abstract val view: View; open fun onShow() {}; open fun onHide() {} }

/* ---------------- 首页 ---------------- */
class HomePage(a: MainActivity) : Page(a) {
  private val chip = Chip(a).apply { text = "检测中"; isClickable = false; chipStrokeWidth = 0f; textSize = 12f }
  private val search = EditText(a).apply {
    hint = "搜索名称或通道号"; textSize = 14f; setSingleLine(); background = a.round(SURFACE, 12); setTextColor(TEXT1); setHintTextColor(TEXT2)
    setPadding(a.dp(14), 0, a.dp(14), 0); compoundDrawablePadding = a.dp(8)
    setCompoundDrawablesRelativeWithIntrinsicBounds(a.drw(R.drawable.ic_search).mutate().apply { setTint(TEXT2) }, null, null, null)
  }
  private val rv = RecyclerView(a)
  private val chips = com.google.android.material.chip.ChipGroup(a).apply { isSingleLine = true }
  private val swipe = SwipeRefreshLayout(a).apply { setColorSchemeColors(ACCENT); addView(rv) }
  private var items = listOf<Any>()
  private val h = Handler(Looper.getMainLooper())
  private val periodic = object : Runnable { override fun run() { refreshThumbs(false); h.postDelayed(this, 5 * 60_000L) } }
  private val listener: (Int) -> Unit = { ch -> items.indexOf(ch).takeIf { it >= 0 }?.let { rv.adapter?.notifyItemChanged(it) }
    items.indexOfLast { it == ch }.takeIf { it >= 0 }?.let { rv.adapter?.notifyItemChanged(it) } }
  override val view: View = LinearLayout(a).apply {
    orientation = LinearLayout.VERTICAL
    val top = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(4)) }
    top.addView(a.tv("大华监控", 28f, TEXT1, true), LinearLayout.LayoutParams(0, -2, 1f)); top.addView(chip)
    addView(top)
    addView(search, LinearLayout.LayoutParams(-1, a.dp(46)).apply { setMargins(a.dp(16), a.dp(8), a.dp(16), a.dp(4)) })
    addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(a.dp(12), 0, a.dp(12), 0) })
    addView(swipe, LinearLayout.LayoutParams(-1, 0, 1f))
  }
  private var filter = "全部"
  private val fixed = listOf("全部", "收藏", "在线", "离线")
  private fun buildChips() {
    chips.removeAllViews()
    for (g in fixed + Cfg.groups(a)) chips.addView(Chip(a, null, com.google.android.material.R.attr.chipStyle).apply {
      text = g; isCheckable = true; isChecked = g == filter; setOnClickListener { filter = g; buildChips(); rebuild() } })
    chips.addView(Chip(a).apply { text = "+ 分组"; setOnClickListener { a.manageGroups { if (filter !in fixed + Cfg.groups(a)) filter = "全部"; buildChips(); rebuild() } } })
  }
  init {
    val lm = GridLayoutManager(a, 2)
    lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() { override fun getSpanSize(p: Int) = if (items.getOrNull(p) is Int) 1 else lm.spanCount }
    rv.layoutManager = lm; rv.setPadding(a.dp(8), 0, a.dp(8), a.dp(8)); rv.clipToPadding = false
    rv.adapter = Adapter()
    swipe.setOnRefreshListener { refreshThumbs(true); check(); h.postDelayed({ swipe.isRefreshing = false }, 1200) }
    search.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, a1: Int, b: Int, c: Int) {}
      override fun onTextChanged(s: CharSequence?, a1: Int, b: Int, c: Int) {}
      override fun afterTextChanged(s: Editable?) { rebuild() }
    })
  }
  private fun check() { chip.text = "● 检测中"; chip.setTextColor(TEXT2); chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(FILL)
    a.checkOnline { pr -> val ok = pr != null; chip.text = if (ok) "● 在线 · ${pr!!.label}" else "● 离线"
      if (!ok) a.snack("连不上录像机：网络断开或公网地址变了，可到「我的 → 连接与账号」检查", true)
      chip.setTextColor(if (ok) OK else ERR)
      chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(soft(if (ok) OK else ERR)) } }
  fun rebuild() {
    val q = search.text.toString().trim()
    val base = when (filter) { "全部" -> (1..Cfg.COUNT).toList(); "收藏" -> Cfg.favs(a).sorted(); "在线" -> (1..Cfg.COUNT).filter { Thumbs.online[it] == true }; "离线" -> (1..Cfg.COUNT).filter { Thumbs.online[it] == false }; else -> Cfg.groupChs(a, filter) }
    val match = base.filter { q.isEmpty() || it.toString() == q || Cfg.name(a, it).contains(q, true) || "通道$it".contains(q) }
    val favs = match.filter { Cfg.isFav(a, it) }
    val l = mutableListOf<Any>()
    if (favs.isNotEmpty() && q.isEmpty() && filter == "全部") { l += "收藏"; l.addAll(favs) }
    l.add(if (q.isNotEmpty()) "搜索结果 (${match.size})" else if (filter == "全部") "全部摄像头 (${Cfg.COUNT})" else "$filter (${match.size})")
    if (match.isEmpty()) l.add(Empty(when { q.isNotEmpty() -> "没有找到“$q”相关的摄像头"; filter == "在线" || filter == "离线" -> "暂无${filter}的摄像头\n（下拉刷新可重新检测）"; filter == "收藏" -> "还没有收藏\n长按摄像头卡片 → 收藏"; else -> "该分组还没有摄像头\n长按摄像头卡片 → 加入分组" })) else l.addAll(match)
    items = l; rv.adapter?.notifyDataSetChanged()
  }
  private fun refreshThumbs(force: Boolean) {
    if (!Cfg.configured(a)) return
    val favs = Cfg.favs(a).toList(); Thumbs.request(a, favs + (1..Cfg.COUNT).filter { it !in favs }, force)
  }
  override fun onShow() { (rv.layoutManager as GridLayoutManager).spanCount = (a.widthDp() / 200).coerceIn(2, 6)
    if (filter !in fixed + Cfg.groups(a)) filter = "全部"; buildChips(); rebuild(); check(); Thumbs.listeners += listener; h.removeCallbacks(periodic); h.post(periodic) }
  override fun onHide() { Thumbs.listeners -= listener; h.removeCallbacks(periodic) }
  data class Empty(val msg: String)

  inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun getItemCount() = items.size
    override fun getItemViewType(p: Int) = when (items[p]) { is Int -> 1; is String -> 0; else -> 2 }
    override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder = when (t) {
      1 -> CamVH(); 0 -> object : RecyclerView.ViewHolder(a.tv("", 13f, TEXT2, true).apply { setPadding(a.dp(10), a.dp(18), a.dp(8), a.dp(6)); letterSpacing = 0.04f }) {}
      else -> object : RecyclerView.ViewHolder(LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, a.dp(60), 0, a.dp(60))
        addView(FrameLayout(a).apply { background = a.round(FILL, 40); addView(a.icon(R.drawable.ic_videocam, TEXT2, 40), FrameLayout.LayoutParams(a.dp(40), a.dp(40), Gravity.CENTER)) }, LinearLayout.LayoutParams(a.dp(80), a.dp(80)))
        addView(a.tv("", 14f, TEXT2).apply { setPadding(a.dp(32), a.dp(14), a.dp(32), 0); gravity = Gravity.CENTER }) }) {}
    }
    override fun onBindViewHolder(vh: RecyclerView.ViewHolder, p: Int) {
      when (val it = items[p]) {
        is Int -> (vh as CamVH).bind(it)
        is String -> (vh.itemView as TextView).text = it
        is Empty -> ((vh.itemView as LinearLayout).getChildAt(1) as TextView).text = it.msg
      }
    }
  }
  inner class CamVH : RecyclerView.ViewHolder(a.card()) {
    private val img = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val ph = a.icon(R.drawable.ic_videocam, 0xFFB0B6BE.toInt(), 36)
    private val star = a.icon(R.drawable.ic_star, 0xFFFFC107.toInt(), 18)
    private val name = a.tv("", 14f, TEXT1, true).apply { maxLines = 1 }
    private val sub = a.tv("", 12f, TEXT2)
    private val dotV = a.dot(TEXT2)
    init {
      val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
      val r = Ratio(a).apply { setBackgroundColor(FILL) }
      r.addView(ph, FrameLayout.LayoutParams(a.dp(36), a.dp(36), Gravity.CENTER)); r.addView(img, FrameLayout.LayoutParams(-1, -1))
      r.addView(star, FrameLayout.LayoutParams(a.dp(18), a.dp(18), Gravity.TOP or Gravity.END).apply { setMargins(0, a.dp(8), a.dp(8), 0) })
      col.addView(r)
      val row = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(a.dp(12), a.dp(10), a.dp(12), 0) }
      row.addView(dotV); row.addView(name, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = a.dp(6) })
      col.addView(row); col.addView(sub.apply { setPadding(a.dp(26), a.dp(2), a.dp(12), a.dp(12)) })
      (itemView as ViewGroup).addView(col)
      itemView.layoutParams = RecyclerView.LayoutParams(-1, -2).apply { setMargins(a.dp(6), a.dp(6), a.dp(6), a.dp(6)) }
    }
    fun bind(ch: Int) {
      name.text = Cfg.name(a, ch); sub.text = "通道 $ch"
      val bmp = Thumbs.load(a, ch); img.setImageBitmap(bmp); img.visibility = if (bmp == null) View.GONE else View.VISIBLE
      star.visibility = if (Cfg.isFav(a, ch)) View.VISIBLE else View.GONE
      val on = Thumbs.online[ch]
      (dotV.background as android.graphics.drawable.GradientDrawable).setColor(when (on) { true -> OK; false -> ERR; null -> LINE })
      sub.text = "通道 $ch · " + when (on) { true -> "在线"; false -> "离线"; null -> "未检测" }
      itemView.setOnClickListener { a.openPreview(ch) }
      itemView.setOnLongClickListener { it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); a.camMenu(itemView, ch) { rebuild() }; true }
    }
  }
}

/* ---------------- 全部画面 ---------------- */
@SuppressLint("ClickableViewAccessibility")
class AllPage(a: MainActivity) : Page(a) {
  private var split = 4; private var page = 0
  private val tiles = mutableListOf<Tile>()
  private val body = FrameLayout(a)
  private val dots = LinearLayout(a).apply { gravity = Gravity.CENTER }
  private var rv: RecyclerView? = null
  private val group = MaterialButtonToggleGroup(a).apply { isSingleSelection = true; isSelectionRequired = true }
  private var patrol: List<Int>? = null; private var patrolSec = 5; private var patrolName = ""
  private val src get() = patrol ?: (1..Cfg.COUNT).toList()
  private val pages get() = maxOf(1, (src.size + split - 1) / split)
  private val ph = Handler(Looper.getMainLooper())
  private val patrolBtn = MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { setIconResource(R.drawable.ic_loop); textSize = 13f }
  private fun arrow(t: String, d: Int) = MaterialButton(a, null, com.google.android.material.R.attr.borderlessButtonStyle).apply { text = t; textSize = 22f; setPadding(0, 0, 0, 0); minWidth = 0; setOnClickListener { go(d) } }
  private val prevBtn = arrow("‹", -1); private val nextBtn = arrow("›", 1)
  private val pageLbl = a.tv("", 14f, TEXT1, true).apply { gravity = Gravity.CENTER }
  /** 长按换通道：页内格子 → 指定通道 */
  private val slotOverride = HashMap<Int, Int>()
  override val view: View = LinearLayout(a).apply {
    orientation = LinearLayout.VERTICAL
    addView(a.tv("分屏", 28f, TEXT1, true).apply { setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(8)) })
    val ids = linkedMapOf(1 to "1", 4 to "4", 9 to "9", 16 to "16", 0 to "全部")
    for ((n, t) in ids) group.addView(MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { id = 100 + n; text = t; textSize = 13f },
      LinearLayout.LayoutParams(0, a.dp(44), 1f))
    addView(group, LinearLayout.LayoutParams(-1, -2).apply { setMargins(a.dp(16), 0, a.dp(16), a.dp(6)) })
    val bar = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
    bar.addView(prevBtn, LinearLayout.LayoutParams(a.dp(48), a.dp(44))); bar.addView(pageLbl, LinearLayout.LayoutParams(0, -2, 1f)); bar.addView(nextBtn, LinearLayout.LayoutParams(a.dp(48), a.dp(44)))
    bar.addView(patrolBtn, LinearLayout.LayoutParams(-2, a.dp(44)).apply { marginStart = a.dp(8) })
    addView(bar, LinearLayout.LayoutParams(-1, -2).apply { setMargins(a.dp(12), 0, a.dp(16), a.dp(6)) })
    addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
    addView(dots, LinearLayout.LayoutParams(-1, a.dp(28)))
    addView(a.tv("点画面全屏 · 长按换通道 · 左右滑翻页", 11f, TEXT2).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, a.dp(6)) }, LinearLayout.LayoutParams(-1, -2))
  }
  init {
    group.check(100 + split); updPatrolBtn(); patrolBtn.setOnClickListener { choosePatrol() }
    group.addOnButtonCheckedListener { _, id, checked -> if (checked && id - 100 != split) { userSplit = true; slotOverride.clear(); page = if (id == 100) 0 else page * maxOf(split, 1) / (id - 100).coerceAtLeast(1); split = id - 100; onHide(); onShow() } }
  }
  private fun updPatrolBtn() { patrolBtn.text = if (patrol == null) "轮巡" else "停止 · ${patrolName}" }
  private fun choosePatrol() {
    if (patrol != null) { patrol = null; updPatrolBtn(); onHide(); onShow(); return }
    val names = listOf("收藏") + Cfg.groups(a) + "全部 ${Cfg.COUNT} 路"
    MaterialAlertDialogBuilder(a).setTitle("轮巡哪些摄像头").setItems(names.toTypedArray()) { _, i ->
      val list = when { i == 0 -> Cfg.favs(a).sorted(); i == names.size - 1 -> (1..Cfg.COUNT).toList(); else -> Cfg.groupChs(a, names[i]) }
      if (list.isEmpty()) { Toast.makeText(a, "「${names[i]}」里没有摄像头", Toast.LENGTH_SHORT).show(); return@setItems }
      val secs = intArrayOf(3, 5, 10, 30)
      MaterialAlertDialogBuilder(a).setTitle("切换间隔").setItems(secs.map { "${it} 秒" }.toTypedArray()) { _, j ->
        patrol = list; patrolSec = secs[j]; patrolName = names[i]; page = 0
        if (split == 0) { split = 4; group.check(104) }
        updPatrolBtn(); onHide(); onShow()
      }.show()
    }.show()
  }
  private var userSplit = false
  override fun onShow() { if (!Cfg.configured(a)) return
    if (!userSplit && patrol == null) { val d = if (a.wide()) 9 else 4; if (d != split) { split = d; page = 0; group.check(100 + d) } }
    if (split == 0) { if (patrol != null) { patrol = null; updPatrolBtn() }; showAll() } else showGrid()
    if (patrol != null) ph.postDelayed({ if (patrol != null) go(1) }, patrolSec * 1000L) }
  override fun onHide() {
    ph.removeCallbacksAndMessages(null)
    tiles.forEach { it.stop() }; tiles.clear()
    rv?.let { r -> for (i in 0 until r.childCount) (r.getChildAt(i) as? Tile)?.stop() }; rv = null
    body.removeAllViews(); dots.removeAllViews()
  }
  private fun drawDots() {
    dots.removeAllViews()
    pageLbl.text = "第 ${page + 1} / $pages 页" + if (patrol != null) " · 每${patrolSec}秒" else ""
    prevBtn.isEnabled = pages > 1; nextBtn.isEnabled = pages > 1
    if (pages > 12) { dots.addView(a.tv("${page + 1} / $pages", 12f, TEXT2)); return }
    for (i in 0 until pages) dots.addView(a.dot(if (i == page) ACCENT else LINE).apply {
      layoutParams = LinearLayout.LayoutParams(a.dp(if (i == page) 16 else 6), a.dp(6)).apply { setMargins(a.dp(3), 0, a.dp(3), 0) }
      background = a.round(if (i == page) ACCENT else LINE, 3) })
  }
  private fun go(d: Int) { if (split == 0) return; slotOverride.clear(); page = (page + d + pages) % pages; onHide(); onShow() }
  private fun showGrid() {
    if (page >= pages) page = 0
    val cols = when (split) { 1 -> 1; 4 -> 2; 9 -> 3; else -> 4 }
    val grid = GridLayout(a).apply { columnCount = cols; setBackgroundColor(Color.BLACK) }
    val wrap = object : FrameLayout(a) {
      var x0 = 0f
      override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) { MotionEvent.ACTION_DOWN -> x0 = e.x
          MotionEvent.ACTION_MOVE -> if (kotlin.math.abs(e.x - x0) > a.dp(48)) { go(if (e.x < x0) 1 else -1); x0 = Float.NaN; return true } }
        return false
      }
    }
    wrap.addView(grid, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
    body.addView(wrap, FrameLayout.LayoutParams(-1, -1))
    drawDots()
    body.post {
      if (!grid.isAttachedToWindow) return@post
      val w = body.width / cols; val hh = minOf(w * 9 / 16, body.height / cols)
      val gap = a.dp(1)
      for (i in 0 until split) {
        val ch = slotOverride[i] ?: src.getOrNull(page * split + i) ?: break
        val t = Tile(a); tiles += t; val slot = i
        grid.addView(t, GridLayout.LayoutParams().apply { width = w - gap * 2; height = hh - gap * 2; setMargins(gap, gap, gap, gap) })
        t.onDouble = { a.openPreview(ch) }; t.onTap = { a.openPreview(ch) }
        t.onLong = { a.pickChannel("这个格子换成哪路") { n -> slotOverride[slot] = n; onHide(); onShow() } }
        t.play(ch, if (split == 1) 0 else 1)
      }
    }
  }
  private fun showAll() {
    val r = RecyclerView(a); rv = r
    val cols = (a.widthDp() / 180).coerceIn(3, 6)
    val w = body.width.takeIf { it > 0 }?.let { it / cols } ?: (a.resources.displayMetrics.widthPixels / cols); val hh = w * 9 / 16
    r.layoutManager = GridLayoutManager(a, cols); r.setBackgroundColor(Color.BLACK)
    r.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
      override fun getItemCount() = Cfg.COUNT
      override fun onCreateViewHolder(p: ViewGroup, t: Int) = object : RecyclerView.ViewHolder(Tile(a).apply {
        layoutParams = RecyclerView.LayoutParams(-1, hh).apply { setMargins(1, 1, 1, 1) } }) {}
      override fun onBindViewHolder(vh: RecyclerView.ViewHolder, pos: Int) {
        val t = vh.itemView as Tile; val ch = pos + 1
        t.stop(); t.bind(ch); t.onDouble = { a.openPreview(ch) }; t.onTap = { a.openPreview(ch) }; t.onLong = { a.camMenu(t, ch) { t.bind(ch) } }
      }
      override fun onViewRecycled(vh: RecyclerView.ViewHolder) { (vh.itemView as Tile).stop() }
    }
    fun refresh() {
      if (rv !== r) return
      val lm = r.layoutManager as GridLayoutManager
      val first = lm.findFirstVisibleItemPosition(); if (first < 0) return
      val last = minOf(lm.findLastVisibleItemPosition(), first + 8)
      for (i in 0 until r.childCount) { val t = r.getChildAt(i) as Tile; val pos = r.getChildAdapterPosition(t)
        if (pos in first..last) { if (!t.playing) t.play(pos + 1, 1) } else t.stop() }
    }
    r.addOnScrollListener(object : RecyclerView.OnScrollListener() {
      override fun onScrollStateChanged(v: RecyclerView, s: Int) { if (s == RecyclerView.SCROLL_STATE_IDLE) refresh() } })
    body.addView(r, FrameLayout.LayoutParams(-1, -1))
    dots.addView(a.tv("共 ${Cfg.COUNT} 路 · 停止滑动后播放可见画面（最多 9 路）", 11f, TEXT2))
    pageLbl.text = "全部 ${Cfg.COUNT} 路"; prevBtn.isEnabled = false; nextBtn.isEnabled = false
    r.postDelayed({ refresh() }, 300)
  }
}


/* ---------------- 回放入口 ---------------- */
class PlaybackPage(a: MainActivity) : Page(a) {
  private var startMs: () -> Long = { System.currentTimeMillis() - 3600_000L }
  private val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
  private val recent = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
  override val view: View
  init {
    val root = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(0, a.dp(12), 0, a.dp(24)) }
    root.addView(a.tv("回放", 28f, TEXT1, true).apply { setPadding(a.dp(20), 0, 0, 0) })
    root.addView(a.tv("① 选从什么时候开始看", 13f, TEXT2, true).apply { setPadding(a.dp(28), a.dp(18), 0, a.dp(8)) })
    val chips = com.google.android.material.chip.ChipGroup(a).apply { isSingleSelection = true; isSelectionRequired = true; setPadding(a.dp(16), 0, a.dp(16), 0) }
    val opts = listOf<Pair<String, () -> Long>>(
      "最近 30 分钟" to { System.currentTimeMillis() - 30 * 60_000L },
      "最近 1 小时" to { System.currentTimeMillis() - 3600_000L },
      "今天凌晨起" to { java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0) }.timeInMillis },
      "昨天此时" to { System.currentTimeMillis() - 24 * 3600_000L })
    opts.forEachIndexed { i, (t, f) -> chips.addView(Chip(a, null, com.google.android.material.R.attr.chipStyle).apply { id = View.generateViewId(); text = t; isCheckable = true
      if (i == 1) isChecked = true; setOnCheckedChangeListener { _, c -> if (c) startMs = f } }) }
    root.addView(chips)
    root.addView(a.tv("进入后还可以用日期条和时间轴精确选择", 12f, TEXT2).apply { setPadding(a.dp(28), a.dp(4), 0, 0) })
    root.addView(recent)
    root.addView(a.tv("② 选摄像头", 13f, TEXT2, true).apply { setPadding(a.dp(28), a.dp(18), 0, a.dp(8)) })
    val c = a.card(); c.addView(list); root.addView(c, LinearLayout.LayoutParams(-1, -2).apply { setMargins(a.dp(16), 0, a.dp(16), 0) })
    view = ScrollView(a).apply { addView(root); isVerticalScrollBarEnabled = false }
  }
  private fun row(ch: Int, parent: LinearLayout, first: Boolean) {
    if (!first) parent.addView(View(a).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(-1, maxOf(1, a.dp(1) / 2)).apply { marginStart = a.dp(92) })
    val r = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = a.dp(60); setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(8))
      background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(LINE), null, null)
      setOnClickListener { Cfg.setFlag(a, "pb_has", true); a.getSharedPreferences("cfg", 0).edit().putInt("pb_last", ch).apply(); a.openPlayback(ch, startMs()) } }
    val img = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = a.round(FILL, 8); clipToOutline = true; setImageBitmap(Thumbs.load(a, ch)) }
    r.addView(img, LinearLayout.LayoutParams(a.dp(68), a.dp(38)))
    val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(12), 0, 0, 0) }
    col.addView(a.tv((if (Cfg.isFav(a, ch)) "★ " else "") + Cfg.name(a, ch), 15f, TEXT1).apply { maxLines = 1 }); col.addView(a.tv("通道 $ch", 12f, TEXT2))
    r.addView(col, LinearLayout.LayoutParams(0, -2, 1f)); r.addView(a.tv("›", 20f, TEXT2))
    parent.addView(r)
  }
  override fun onShow() {
    list.removeAllViews(); recent.removeAllViews()
    if (!Cfg.configured(a)) { list.addView(a.tv("请先到「我的 → 连接与账号」填写录像机信息", 14f, TEXT2).apply { setPadding(a.dp(16), a.dp(24), a.dp(16), a.dp(24)); gravity = Gravity.CENTER }); return }
    val last = a.getSharedPreferences("cfg", 0).getInt("pb_last", 0)
    if (last > 0) { recent.addView(a.tv("上次看过", 13f, TEXT2, true).apply { setPadding(a.dp(28), a.dp(18), 0, a.dp(8)) })
      val c = a.card(); val l = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }; c.addView(l); row(last, l, true)
      recent.addView(c, LinearLayout.LayoutParams(-1, -2).apply { setMargins(a.dp(16), 0, a.dp(16), 0) }) }
    val order = Cfg.favs(a).sorted() + (1..Cfg.COUNT).filter { !Cfg.isFav(a, it) }
    order.forEachIndexed { i, ch -> row(ch, list, i == 0) }
  }
}
