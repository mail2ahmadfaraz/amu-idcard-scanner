package `in`.ac.amuonline.idverifier

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import `in`.ac.amuonline.idverifier.databinding.ActivityVerifyBinding

/**
 * Renders the already-validated AMU verification URL. Even after loading, navigation
 * is re-checked on every single request (redirects, links, JS navigation) so a
 * compromised or malicious page can never pivot the user off the allow-listed host.
 */
class VerifyResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_SOURCE_LABEL = "extra_source_label"
    }

    private lateinit var binding: ActivityVerifyBinding
    private var allowedHost: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        val url = intent.getStringExtra(EXTRA_URL)
        val sourceLabel = intent.getStringExtra(EXTRA_SOURCE_LABEL)

        if (url == null) {
            finish()
            return
        }

        // Re-validate here too: never trust that only MainActivity can start this
        // activity (defense in depth against another component on the device
        // launching it directly with an arbitrary URL).
        val validated = QrUrlValidator.validate(url)
        if (validated !is QrValidationResult.Valid) {
            val reason = (validated as? QrValidationResult.Invalid)?.reason
            Toast.makeText(this, reason ?: getString(R.string.status_camera_error), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        allowedHost = Uri.parse(validated.url).host?.lowercase()
        binding.sourceLabel.text = getString(R.string.verify_source_prefix) + (sourceLabel ?: "")

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.scanAnotherButton.setOnClickListener { finish() }

        setupWebView()
        binding.webView.loadUrl(validated.url)
    }

    private fun setupWebView() {
        val webView = binding.webView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        // No file/content access — this WebView only ever needs to render the
        // official verification page.
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val host = request.url.host?.lowercase()
                val scheme = request.url.scheme?.lowercase()
                val onAllowedHost = scheme == "https" && host != null && host == allowedHost
                if (!onAllowedHost) {
                    Toast.makeText(
                        this@VerifyResultActivity,
                        R.string.verify_blocked_navigation,
                        Toast.LENGTH_SHORT
                    ).show()
                    return true // block navigation, stay on current page
                }
                return false // allow WebView to load it normally
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
