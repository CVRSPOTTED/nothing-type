package dev.nothingtype.testing

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView

/** A second editor application, packaged in the instrumentation APK only. */
class ExternalEditorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val editor = WebView(this)
        setContentView(editor)
        editor.loadDataWithBaseURL(null, """
            <!doctype html><html><head><meta name="viewport" content="width=device-width"></head>
            <body style="padding:72px 12px 0"><input type="text" aria-label="Test web editor" autocapitalize="none"
                style="width:90%;height:48px;font-size:20px"></body></html>
        """.trimIndent(), "text/html", "UTF-8", null)
    }
}
