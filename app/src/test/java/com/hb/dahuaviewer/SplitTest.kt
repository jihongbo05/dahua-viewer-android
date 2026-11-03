package com.hb.dahuaviewer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** 回归：1.7.0 已配置时点「分屏」闪退（schedulePatrol 自递归 StackOverflowError） */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SplitTest {
  private fun run(site: Int) {
    Cloud.enabled = false; Vlc.enabled = false
    val c = RuntimeEnvironment.getApplication()
    Sites.init(c); Sites.switch(c, site)
    c.getSharedPreferences(Sites.prefsName(site), 0).edit().putBoolean("ok", true).putString("host", "127.0.0.1").putInt("port", 1).putString("user", "u").apply()
    val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    val bars = mutableListOf<com.google.android.material.navigation.NavigationBarView>()
    fun walk(v: android.view.View) { if (v is com.google.android.material.navigation.NavigationBarView && v.isAttachedToWindow) bars += v; if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
    walk(a.window.decorView)
    bars.first().selectedItemId = 2; ShadowLooper.idleMainLooper()
    val groups = mutableListOf<com.google.android.material.button.MaterialButtonToggleGroup>()
    fun walk2(v: android.view.View) { if (v is com.google.android.material.button.MaterialButtonToggleGroup && v.isShown) groups += v; if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk2(v.getChildAt(i)) }
    walk2(a.window.decorView)
    val g = groups.first()
    for (id in listOf(101, 109, 116, 100, 104)) { g.check(id); ShadowLooper.idleMainLooper() }
    bars.first().selectedItemId = 1; ShadowLooper.idleMainLooper()
    bars.first().selectedItemId = 2; ShadowLooper.idleMainLooper()
    assertEquals(if (site == 1) 64 else 32, Cfg.COUNT)
  }
  @Test @Config(qualifiers = "w373dp-h830dp") fun site1Narrow() = run(1)
  @Test @Config(qualifiers = "w880dp-h840dp") fun site2Wide32ch() = run(2)
}
