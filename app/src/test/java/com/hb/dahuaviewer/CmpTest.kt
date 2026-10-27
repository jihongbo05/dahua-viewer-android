package com.hb.dahuaviewer
import org.junit.Assert.assertTrue
import org.junit.Test
class CmpTest {
  @Test fun cmp() {
    assertTrue(Updater.cmp("v1.3.1", "1.3.0") > 0); assertTrue(Updater.cmp("v1.3.0", "1.3.0") == 0)
    assertTrue(Updater.cmp("v1.10.0", "1.9.9") > 0); assertTrue(Updater.cmp("v1.3", "1.3.0") == 0)
    assertTrue(Updater.cmp("v1.2.9", "1.3.0") < 0); assertTrue(Updater.cmp("V2.0.0-beta", "1.3.0") > 0)
  }
}
