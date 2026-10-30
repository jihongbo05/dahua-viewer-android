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
  private lateinit var home: HomePage; private lateinit var all: AllPage; private lateinit var mine: MinePage
  private var cur: Page? = null

  override fun onCreate(b: Bundle?) {
    setTheme(R.style.Theme_App); initTheme(this)
    super.onCreate(b)
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    content = FrameLayout(this)
    val thumbHost = FrameLayout(this); content.addView(thumbHost, FrameLayout.LayoutParams(-1, -1)); Thumbs.host = thumbHost
    nav = BottomNavigationView(this).apply {
      setBackgroundColor(SURFACE); elevation = 0f; itemActiveIndicatorColor = android.content.res.ColorStateList.valueOf(soft(ACCENT, 0x2E))
      menu.add(0, 1, 0, "首页").setIcon(R.drawable.ic_home)
      menu.add(0, 2, 1, "全部画面").setIcon(R.drawable.ic_grid)
      menu.add(0, 3, 2, "我的").setIcon(R.drawable.ic_person)
    }
    root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(nav)
    setContentView(root)
    home = HomePage(this); all = AllPage(this); mine = MinePage(this)
    for (pg in listOf(home, all, mine)) { pg.view.setBackgroundColor(BG); pg.view.visibility = View.GONE; content.addView(pg.view, FrameLayout.LayoutParams(-1, -1)) }
    nav.setOnItemSelectedListener { select(it.itemId); true }
    Updater.autoCheck(this)
    if (Cfg.configured(this)) nav.selectedItemId = 1 else { nav.selectedItemId = 3; Toast.makeText(this, "首次使用，请先填写连接信息", Toast.LENGTH_LONG).show() }
  }
  fun select(id: Int) {
    val n = when (id) { 1 -> home; 2 -> all; else -> mine }
    if (n === cur) return
    cur?.let { it.onHide(); it.view.visibility = View.GONE }
    cur = n; n.view.alpha = 0f; n.view.visibility = View.VISIBLE; n.view.animate().alpha(1f).setDuration(180).start(); n.onShow()
  }
  fun goHome() { nav.selectedItemId = 1 }
  override fun onResume() { super.onResume(); cur?.onShow()
    if (Cfg.flag(this, "notify", false) && android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9) }
  override fun onPause() { super.onPause(); cur?.onHide() }
  override fun onDestroy() { Thumbs.host = null; Thumbs.cancel(); super.onDestroy() }
  override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); cur?.let { it.onHide(); it.onShow() } }
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
      menu.add(0, 1, 0, "重命名"); menu.add(0, 3, 2, "加入分组…"); menu.add(0, 2, 1, if (Cfg.isFav(this@MainActivity, ch)) "取消收藏" else "收藏")
      setOnMenuItemClickListener { when (it.itemId) { 1 -> rename(ch, done); 3 -> assignGroups(ch, done); else -> { Cfg.toggleFav(this@MainActivity, ch); done() } }; true }
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
  private fun buildChips() {
    chips.removeAllViews()
    for (g in listOf("全部", "收藏") + Cfg.groups(a)) chips.addView(Chip(a, null, com.google.android.material.R.attr.chipStyle).apply {
      text = g; isCheckable = true; isChecked = g == filter; setOnClickListener { filter = g; buildChips(); rebuild() } })
    chips.addView(Chip(a).apply { text = "+ 分组"; setOnClickListener { a.manageGroups { if (filter !in listOf("全部", "收藏") + Cfg.groups(a)) filter = "全部"; buildChips(); rebuild() } } })
  }
  init {
    val lm = GridLayoutManager(a, 2)
    lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() { override fun getSpanSize(p: Int) = if (items.getOrNull(p) is Int) 1 else 2 }
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
      if (!ok) Toast.makeText(a, "连不上录像机：网络断开或公网地址变了，可到「我的 → 连接」检查", Toast.LENGTH_LONG).show()
      chip.setTextColor(if (ok) OK else ERR)
      chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(soft(if (ok) OK else ERR)) } }
  fun rebuild() {
    val q = search.text.toString().trim()
    val base = when (filter) { "全部" -> (1..Cfg.COUNT).toList(); "收藏" -> Cfg.favs(a).sorted(); else -> Cfg.groupChs(a, filter) }
    val match = base.filter { q.isEmpty() || it.toString() == q || Cfg.name(a, it).contains(q, true) || "通道$it".contains(q) }
    val favs = match.filter { Cfg.isFav(a, it) }
    val l = mutableListOf<Any>()
    if (favs.isNotEmpty() && q.isEmpty() && filter == "全部") { l += "收藏"; l.addAll(favs) }
    l.add(if (q.isNotEmpty()) "搜索结果 (${match.size})" else if (filter == "全部") "全部摄像头 (${Cfg.COUNT})" else "$filter (${match.size})")
    if (match.isEmpty()) l.add(Empty(if (q.isNotEmpty()) "没有找到“$q”相关的摄像头" else "该分组还没有摄像头，长按首页摄像头卡片 → 加入分组")) else l.addAll(match)
    items = l; rv.adapter?.notifyDataSetChanged()
  }
  private fun refreshThumbs(force: Boolean) {
    if (!Cfg.configured(a)) return
    val favs = Cfg.favs(a).toList(); Thumbs.request(a, favs + (1..Cfg.COUNT).filter { it !in favs }, force)
  }
  override fun onShow() { if (filter !in listOf("全部", "收藏") + Cfg.groups(a)) filter = "全部"; buildChips(); rebuild(); check(); Thumbs.listeners += listener; h.removeCallbacks(periodic); h.post(periodic) }
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
      itemView.setOnLongClickListener { a.camMenu(itemView, ch) { rebuild() }; true }
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
  private val patrolBtn = MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { setIconResource(R.drawable.ic_loop) }
  override val view: View = LinearLayout(a).apply {
    orientation = LinearLayout.VERTICAL
    addView(a.tv("全部画面", 28f, TEXT1, true).apply { setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(8)) })
    val ids = mapOf(1 to "单画面", 4 to "4分屏", 9 to "9分屏", 0 to "全部")
    for ((n, t) in ids) group.addView(MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { id = 100 + n; text = t; textSize = 13f },
      LinearLayout.LayoutParams(0, -2, 1f))
    addView(group, LinearLayout.LayoutParams(-1, -2).apply { setMargins(a.dp(16), 0, a.dp(16), a.dp(8)) })
    addView(patrolBtn, LinearLayout.LayoutParams(-1, -2).apply { setMargins(a.dp(16), 0, a.dp(16), a.dp(4)) })
    addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
    addView(dots, LinearLayout.LayoutParams(-1, a.dp(28)))
  }
  init {
    group.check(100 + split); updPatrolBtn(); patrolBtn.setOnClickListener { choosePatrol() }
    group.addOnButtonCheckedListener { _, id, checked -> if (checked && id - 100 != split) { page = if (id == 100) 0 else page * maxOf(split, 1) / (id - 100).coerceAtLeast(1); split = id - 100; onHide(); onShow() } }
  }
  private fun updPatrolBtn() { patrolBtn.text = if (patrol == null) "开始轮巡" else "停止轮巡（$patrolName · 每${patrolSec}秒）" }
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
  override fun onShow() { if (!Cfg.configured(a)) return
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
    for (i in 0 until pages) dots.addView(a.dot(if (i == page) ACCENT else LINE).apply {
      layoutParams = LinearLayout.LayoutParams(a.dp(if (i == page) 16 else 6), a.dp(6)).apply { setMargins(a.dp(3), 0, a.dp(3), 0) }
      background = a.round(if (i == page) ACCENT else LINE, 3) })
  }
  private fun go(d: Int) { page = (page + d + pages) % pages; onHide(); onShow() }
  private fun showGrid() {
    if (page >= pages) page = 0
    val cols = when (split) { 1 -> 1; 4 -> 2; else -> 3 }
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
        val ch = src.getOrNull(page * split + i) ?: break
        val t = Tile(a); tiles += t
        grid.addView(t, GridLayout.LayoutParams().apply { width = w - gap * 2; height = hh - gap * 2; setMargins(gap, gap, gap, gap) })
        t.onDouble = { a.openPreview(ch) }; t.onTap = { Toast.makeText(a, "双击放大到实时预览", Toast.LENGTH_SHORT).show() }
        t.onLong = { a.camMenu(t, ch) { t.bind(ch) } }
        t.play(ch, if (split == 1) 0 else 1)
      }
    }
  }
  private fun showAll() {
    val r = RecyclerView(a); rv = r
    val cols = if (a.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 3
    val w = a.resources.displayMetrics.widthPixels / cols; val hh = w * 9 / 16
    r.layoutManager = GridLayoutManager(a, cols); r.setBackgroundColor(Color.BLACK)
    r.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
      override fun getItemCount() = Cfg.COUNT
      override fun onCreateViewHolder(p: ViewGroup, t: Int) = object : RecyclerView.ViewHolder(Tile(a).apply {
        layoutParams = RecyclerView.LayoutParams(-1, hh).apply { setMargins(1, 1, 1, 1) } }) {}
      override fun onBindViewHolder(vh: RecyclerView.ViewHolder, pos: Int) {
        val t = vh.itemView as Tile; val ch = pos + 1
        t.stop(); t.bind(ch); t.onDouble = { a.openPreview(ch) }; t.onLong = { a.camMenu(t, ch) { t.bind(ch) } }
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
    dots.addView(a.tv("共 ${Cfg.COUNT} 路 · 滑动停下后播放可见画面（最多 9 路）· 双击放大", 11f, TEXT2))
    r.postDelayed({ refresh() }, 300)
  }
}

