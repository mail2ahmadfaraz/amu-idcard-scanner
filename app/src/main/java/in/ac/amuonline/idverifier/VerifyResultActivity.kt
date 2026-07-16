package `in`.ac.amuonline.idverifier

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.webkit.WebResourceError
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
    private lateinit var connectivityManager: ConnectivityManager
    private var verifyUrl: String? = null
    private var pulseAnimator: ValueAnimator? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                if (binding.noInternetOverlay.visibility == View.VISIBLE) {
                    loadVerificationPage()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // Registered unconditionally (even if we finish() below) so onDestroy's
        // unregisterNetworkCallback always has a matching registration.
        connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)

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

        verifyUrl = validated.url
        binding.sourceLabel.text = getString(R.string.verify_source_prefix) + (sourceLabel ?: "")

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.scanAnotherButton.setOnClickListener { finish() }
        binding.retryButton.setOnClickListener { loadVerificationPage() }

        setupWebView()
        loadVerificationPage()
    }

    private fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun loadVerificationPage() {
        val url = verifyUrl ?: return
        if (!isOnline()) {
            showNoInternet()
            return
        }
        hideNoInternet()
        binding.webView.loadUrl(url)
    }

    private fun showNoInternet() {
        binding.webView.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.noInternetOverlay.visibility = View.VISIBLE
        if (pulseAnimator == null) {
            pulseAnimator = ObjectAnimator.ofFloat(binding.wifiOffIcon, View.ALPHA, 1f, 0.35f, 1f).apply {
                duration = 1400
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    private fun hideNoInternet() {
        binding.noInternetOverlay.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        pulseAnimator?.cancel()
        pulseAnimator = null
        binding.wifiOffIcon.alpha = 1f
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
                val onAllowedHost = scheme == "https" && host != null && AllowedSources.match(host) != null
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

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                // Only hijack the screen for errors that actually mean "no
                // connectivity" — anything else (odd redirects, a transient SSL/DNS
                // blip that resolves on the actual navigation, HTTP status errors,
                // etc.) must NOT blank out real content that did load.
                val isConnectivityError = error.errorCode == ERROR_HOST_LOOKUP ||
                    error.errorCode == ERROR_CONNECT ||
                    error.errorCode == ERROR_TIMEOUT ||
                    error.errorCode == ERROR_IO
                if (request.isForMainFrame && isConnectivityError) {
                    showNoInternet()
                }
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
        pulseAnimator?.cancel()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        binding.webView.destroy()
        super.onDestroy()
    }
}
