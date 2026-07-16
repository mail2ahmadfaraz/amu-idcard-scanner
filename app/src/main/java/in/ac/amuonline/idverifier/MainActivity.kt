package `in`.ac.amuonline.idverifier

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import `in`.ac.amuonline.idverifier.databinding.ActivityMainBinding
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    companion object {
        private const val VALID_RESULT_DISPLAY_MS = 700L
        private const val INVALID_RESULT_DISPLAY_MS = 1600L
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private val scanningPaused = AtomicBoolean(false)

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                showPermissionDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyEdgeToEdgeInsets()

        cameraExecutor = Executors.newSingleThreadExecutor()
        setStatus(getString(R.string.status_point_camera), isError = false)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        // Resume scanning when returning from the verification screen.
        hideResultOverlay()
        scanningPaused.set(false)
        setStatus(getString(R.string.status_point_camera), isError = false)
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.statusBanner.setPadding(
                binding.statusBanner.paddingLeft,
                bars.top + resources.getDimensionPixelSize(R.dimen.banner_extra_padding),
                binding.statusBanner.paddingRight,
                binding.statusBanner.paddingBottom
            )
            binding.instructionText.setPadding(
                binding.instructionText.paddingLeft,
                binding.instructionText.paddingTop,
                binding.instructionText.paddingRight,
                bars.bottom + resources.getDimensionPixelSize(R.dimen.banner_extra_padding)
            )
            insets
        }
    }

    private fun showPermissionDenied() {
        setStatus(getString(R.string.status_camera_permission_needed), isError = true)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val scannerOptions = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val scanner = BarcodeScanning.getClient(scannerOptions)

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processFrame(imageProxy, scanner)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                setStatus(getString(R.string.status_camera_error), isError = true)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun processFrame(imageProxy: ImageProxy, scanner: com.google.mlkit.vision.barcode.BarcodeScanner) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || scanningPaused.get()) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                if (raw != null && scanningPaused.compareAndSet(false, true)) {
                    handleScanResult(raw)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun handleScanResult(rawText: String) {
        when (val result = QrUrlValidator.validate(rawText)) {
            is QrValidationResult.Valid -> {
                runOnUiThread {
                    showResultOverlay(isValid = true, message = getString(R.string.result_valid_label))
                    binding.root.postDelayed({
                        val intent = Intent(this, VerifyResultActivity::class.java).apply {
                            putExtra(VerifyResultActivity.EXTRA_URL, result.url)
                            putExtra(VerifyResultActivity.EXTRA_SOURCE_LABEL, result.sourceLabel)
                        }
                        startActivity(intent)
                    }, VALID_RESULT_DISPLAY_MS)
                }
            }
            is QrValidationResult.Invalid -> {
                runOnUiThread {
                    showResultOverlay(isValid = false, message = result.reason)
                }
                // Let the user see the rejection, then resume scanning automatically.
                binding.root.postDelayed({
                    hideResultOverlay()
                    scanningPaused.set(false)
                }, INVALID_RESULT_DISPLAY_MS)
            }
        }
    }

    private fun showResultOverlay(isValid: Boolean, message: String) {
        binding.resultIcon.setImageResource(if (isValid) R.drawable.ic_check else R.drawable.ic_x_mark)
        binding.resultLabel.text = message
        binding.resultOverlay.setBackgroundColor(
            ContextCompat.getColor(this, if (isValid) R.color.status_ok else R.color.status_error)
        )

        binding.resultOverlay.alpha = 0f
        binding.resultOverlay.visibility = View.VISIBLE
        binding.resultOverlay.animate().alpha(1f).setDuration(150).start()

        binding.resultIcon.scaleX = 0.3f
        binding.resultIcon.scaleY = 0.3f
        binding.resultIcon.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(350)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    private fun hideResultOverlay() {
        binding.resultOverlay.visibility = View.GONE
        binding.resultIcon.animate().cancel()
        binding.resultOverlay.animate().cancel()
    }

    private fun setStatus(text: String, isError: Boolean) {
        binding.statusBanner.text = text
        binding.statusBanner.setBackgroundColor(
            ContextCompat.getColor(
                this,
                if (isError) R.color.status_error else R.color.status_ok
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
