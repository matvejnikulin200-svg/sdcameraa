package com.example.selfdestructcam

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var chips: List<TextView>
    private lateinit var chipRow: LinearLayout
    private var imageCapture: ImageCapture? = null

    private val labels = listOf("1 час", "1 день", "1 неделя", "1 месяц")
    private val hour = 3_600_000L
    private val delays = listOf(hour, 24 * hour, 7 * 24 * hour, 30 * 24 * hour)

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startCamera() else Toast.makeText(this, "Нужен доступ к камере", Toast.LENGTH_LONG).show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Deleter.sweep(this)
        buildUi()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            startCamera() else permLauncher.launch(Manifest.permission.CAMERA)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewView = PreviewView(this)
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))

        chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        chips = labels.map { l ->
            TextView(this).apply {
                text = l; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(0x88000000.toInt()) }
            }.also { chipRow.addView(it, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), 0, dp(4), 0) }) }
        }
        root.addView(chipRow, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(150) })

        val shutter = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE); setStroke(dp(4), 0xFF888888.toInt()) }
        }
        root.addView(shutter, FrameLayout.LayoutParams(dp(76), dp(76), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(50) })

        var startX = 0f
        var selected = -1
        shutter.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = e.rawX; selected = -1; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = startX - e.rawX
                    if (dx > dp(30)) {
                        chipRow.visibility = View.VISIBLE
                        selected = ((dx - dp(30)) / dp(40)).toInt().coerceIn(0, 3)
                    } else { chipRow.visibility = View.GONE; selected = -1 }
                    chips.forEachIndexed { i, c ->
                        (c.background as GradientDrawable).setColor(if (i == selected) 0xFFE53935.toInt() else 0x88000000.toInt())
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = startX - e.rawX > dp(30)
                    chipRow.visibility = View.GONE
                    if (!moved) takePhoto(null) else if (selected >= 0) takePhoto(selected)
                    true
                }
                MotionEvent.ACTION_CANCEL -> { chipRow.visibility = View.GONE; true }
                else -> false
            }
        }
        setContentView(root)
    }

    private fun startCamera() {
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({
            val provider = f.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder().build()
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto(ttlIndex: Int?) {
        val ic = imageCapture ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SDCamera")
        }
        val opts = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        ic.takePicture(opts, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                val uri = r.savedUri
                if (ttlIndex != null && uri != null) {
                    Deleter.schedule(applicationContext, uri, delays[ttlIndex])
                    Toast.makeText(this@MainActivity, "Удалится через ${labels[ttlIndex]}", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this@MainActivity, "Фото сохранено", Toast.LENGTH_SHORT).show()
            }
            override fun onError(e: ImageCaptureException) {
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })
    }
}
