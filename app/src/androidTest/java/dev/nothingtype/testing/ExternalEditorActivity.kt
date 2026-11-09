package dev.nothingtype.testing

import android.app.Activity
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView

/** A second editor application, packaged in the instrumentation APK only. */
class ExternalEditorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = TextView(this)
        val editor = WebView(this).apply {
            settings.javaScriptEnabled = true // This fixture loads only the HTML below.
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    result.text = title
                }
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(result)
            addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        editor.loadDataWithBaseURL(null, """
            <!doctype html><html><head><meta name="viewport" content="width=device-width"></head>
            <body style="padding:72px 12px 0"><input type="text" aria-label="Test web editor" autocapitalize="none"
                oninput="document.title = 'Typed [' + this.value + ']'"
                style="width:90%;height:48px;font-size:20px"></body></html>
        """.trimIndent(), "text/html", "UTF-8", null)
    }
}
