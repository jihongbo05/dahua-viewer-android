package com.hb.dahuaviewer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavTest {
  private fun run() {
    val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    val bars = mutableListOf<com.google.android.material.navigation.NavigationBarView>()
    fun walk(v: android.view.View) { if (v is com.google.android.material.navigation.NavigationBarView && v.isAttachedToWindow) bars += v; if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
    walk(a.window.decorView)
    val bar = bars.first()
    for (id in listOf(2, 4, 3, 1, 3, 2)) { bar.selectedItemId = id; org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    a.onBackPressed()
  }
  @Test @Config(qualifiers = "w373dp-h830dp") fun narrow() = run()
  @Test @Config(qualifiers = "w880dp-h840dp") fun wide() = run()
}
