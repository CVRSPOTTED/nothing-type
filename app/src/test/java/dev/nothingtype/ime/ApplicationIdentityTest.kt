package dev.nothingtype.ime

import android.content.ComponentName
import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApplicationIdentityTest {
    @Test fun systemIdentityAndLauncherUseNothingType() {
        val app = RuntimeEnvironment.getApplication()
        assertEquals("dev.nothingtype.keyboard", app.packageName)
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)
        assertEquals("dev.nothingtype.app.MainActivity", launch?.component?.className)
    }
    @Test fun newImeComponentIsProtectedAndHasItsSettingsEntryPoint() {
        val app = RuntimeEnvironment.getApplication()
        val service = app.packageManager.getServiceInfo(ComponentName(app, NothingTypeIme::class.java), PackageManager.GET_META_DATA)
        assertEquals("android.permission.BIND_INPUT_METHOD", service.permission)
        assertTrue(service.exported)
        val parser = app.resources.getXml(service.metaData.getInt("android.view.im"))
        parser.use {
            while (it.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) it.next()
            assertEquals("dev.nothingtype.app.MainActivity", it.getAttributeValue("http://schemas.android.com/apk/res/android", "settingsActivity"))
        }
    }
}
