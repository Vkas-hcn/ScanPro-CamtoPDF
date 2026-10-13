package com.scanpro.cam.pdf.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.scanpro.cam.pdf.R
import com.scanpro.cam.pdf.base.SpActivity
import com.scanpro.cam.pdf.databinding.ActivityCamCaptureBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CamCaptureActivity : SpActivity<ActivityCamCaptureBinding>() {

    override val applySystemBarsPadding: Boolean = false
    override val lightStatusBarIcons: Boolean = false

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashMode = CamFlashMode.OFF
    private var gridEnabled = false
    private var resolution = CamCaptureQuality.presets[1]
    private lateinit var cameraExecutor: ExecutorService
    private var flashPopup: PopupWindow? = null
    private var hdPopup: PopupWindow? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else {
                Toast.makeText(this, R.string.scan_camera_permission, Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    private val reviewLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                finish()
            } else {
                refreshCaptureUi()
            }
        }

    override fun inflateBinding(inflater: LayoutInflater): ActivityCamCaptureBinding {
        return ActivityCamCaptureBinding.inflate(inflater)
    }

    override fun initView(savedInstanceState: Bundle?) {
        cameraExecutor = Executors.newSingleThreadExecutor()
        applyInsets()
        if (intent.getBooleanExtra(EXTRA_CONTINUE, false).not()) {
            CamCaptureSession.clear()
        }
        refreshCaptureUi()
        binding.ivThumbnail.clipToOutline = true
        binding.ivThumbnail.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        binding.btnClose.setOnClickListener { finish() }
        binding.btnFlash.setOnClickListener { showFlashPopup() }
        binding.btnHd.setOnClickListener { showResolutionPopup() }
        binding.btnGrid.setOnClickListener { toggleGrid() }
        binding.btnSwitch.setOnClickListener { switchCamera() }
        binding.btnShutter.setOnClickListener { takePhoto() }
        binding.btnNext.setOnClickListener { openReview() }
        binding.thumbnailContainer.setOnClickListener { openReview() }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        flashPopup?.dismiss()
        hdPopup?.dismiss()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun applyInsets() {
        val top = binding.topBar
        val bottom = binding.bottomBar
        val topPad = top.paddingTop
        val bottomPad = bottom.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.scanRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            top.updatePadding(top = topPad + bars.top)
            bottom.updatePadding(bottom = bottomPad + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.scanRoot)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            bindCamera(provider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera(provider: ProcessCameraProvider) {
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = binding.previewView.surfaceProvider
        }
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetResolution(Size(resolution.width, resolution.height))
            .setFlashMode(toCameraFlash(flashMode))
            .build()
        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, selector, preview, imageCapture)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.scan_capture_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val photoFile = File(
            getScanDir(),
            "SCAN_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg",
        )
        val options = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    CamCaptureSession.add(photoFile.absolutePath)
                    refreshCaptureUi()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@CamCaptureActivity, R.string.scan_capture_failed, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    private fun refreshCaptureUi() {
        val count = CamCaptureSession.size()
        val hasPhotos = count > 0
        binding.btnNext.isVisible = hasPhotos
        binding.thumbnailContainer.isVisible = hasPhotos
        binding.tvBadge.text = count.toString()
        val last = CamCaptureSession.lastOrNull()
        if (last != null) {
            binding.ivThumbnail.setImageBitmap(BitmapFactory.decodeFile(last))
        }
        updateFlashIcon()
    }

    private fun openReview() {
        if (CamCaptureSession.size() == 0) {
            Toast.makeText(this, R.string.scan_no_photos, Toast.LENGTH_SHORT).show()
            return
        }
        reviewLauncher.launch(Intent(this, CamCaptureReviewActivity::class.java))
    }

    private fun toggleGrid() {
        gridEnabled = !gridEnabled
        binding.gridOverlay.isVisible = gridEnabled
    }

    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        startCamera()
    }

    private fun setFlashMode(mode: CamFlashMode) {
        flashMode = mode
        imageCapture?.flashMode = toCameraFlash(mode)
        updateFlashIcon()
        flashPopup?.dismiss()
    }

    private fun updateFlashIcon() {
        // 顶栏深色背景，统一用白色闪电图标
        binding.btnFlash.setImageResource(
            when (flashMode) {
                CamFlashMode.AUTO -> R.drawable.ic_flash_auto
                CamFlashMode.ON -> R.drawable.ic_flash_on
                CamFlashMode.OFF -> R.drawable.ic_flash_off
            },
        )
        binding.btnFlash.setColorFilter(android.graphics.Color.WHITE)
    }

    private fun toCameraFlash(mode: CamFlashMode): Int = when (mode) {
        CamFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        CamFlashMode.ON -> ImageCapture.FLASH_MODE_ON
        CamFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
    }

    private fun showFlashPopup() {
        val content = layoutInflater.inflate(R.layout.popup_cam_flash_menu, null)
        content.findViewById<View>(R.id.item_flash_auto).setOnClickListener { setFlashMode(CamFlashMode.AUTO) }
        content.findViewById<View>(R.id.item_flash_on).setOnClickListener { setFlashMode(CamFlashMode.ON) }
        content.findViewById<View>(R.id.item_flash_off).setOnClickListener { setFlashMode(CamFlashMode.OFF) }
        content.findViewById<ImageView>(R.id.check_flash_auto).setImageResource(
            if (flashMode == CamFlashMode.AUTO) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected,
        )
        content.findViewById<ImageView>(R.id.check_flash_on).setImageResource(
            if (flashMode == CamFlashMode.ON) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected,
        )
        content.findViewById<ImageView>(R.id.check_flash_off).setImageResource(
            if (flashMode == CamFlashMode.OFF) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected,
        )
        flashPopup?.dismiss()
        content.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        flashPopup = PopupWindow(
            content,
            content.measuredWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            elevation = 12f
            isOutsideTouchable = true
            // 紧贴闪光灯图标正下方
            showAsDropDown(binding.btnFlash, 0, 8)
        }
    }

    private fun showResolutionPopup() {
        val content = layoutInflater.inflate(R.layout.popup_cam_resolution_menu, null)
        val list = content.findViewById<LinearLayout>(R.id.resolution_list)
        CamCaptureQuality.presets.forEach { preset ->
            val row = layoutInflater.inflate(R.layout.item_cam_resolution, list, false)
            val selected = preset.width == resolution.width && preset.height == resolution.height
            row.findViewById<TextView>(R.id.tv_label).text = preset.label
            row.findViewById<ImageView>(R.id.iv_check).setImageResource(
                if (selected) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected,
            )
            row.setOnClickListener {
                resolution = preset
                hdPopup?.dismiss()
                startCamera()
            }
            list.addView(row)
        }
        hdPopup?.dismiss()
        content.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        hdPopup = PopupWindow(
            content,
            content.measuredWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            elevation = 12f
            isOutsideTouchable = true
            showAsDropDown(binding.btnHd, 0, 8)
        }
    }

    private fun getScanDir(): File {
        return File(cacheDir, "scan_capture").apply { mkdirs() }
    }

    companion object {
        private const val EXTRA_CONTINUE = "extra_continue"

        fun start(context: Context, continueSession: Boolean = false) {
            context.startActivity(
                Intent(context, CamCaptureActivity::class.java)
                    .putExtra(EXTRA_CONTINUE, continueSession),
            )
        }
    }
}
