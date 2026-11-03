package com.hb.dahuaviewer
import org.junit.Assert.*
import org.junit.Test
class UpdTest {
  @Test fun parseHtml() {
    val h = """<a href="/jihongbo05/dahua-viewer-android/releases/download/v1.6.0/cloud-config.enc">x</a><a href="/jihongbo05/dahua-viewer-android/releases/download/v1.6.0/DahuaViewer-1.6.0.apk">a</a><a href="/jihongbo05/dahua-viewer-android/releases/download/v1.6.0/DahuaViewer-1.6.0.apk.sha256">s</a>"""
    val r = Updater.parseAssetsHtml("v1.6.0", h)!!
    assertTrue(r.apkUrl.endsWith("/v1.6.0/DahuaViewer-1.6.0.apk")); assertTrue(r.shaUrl!!.endsWith(".sha256"))
    assertTrue(Updater.cmp("v1.6.0", "1.7.1") < 0); assertTrue(Updater.cmp("v1.7.2", "1.7.1") > 0)
  }
}
