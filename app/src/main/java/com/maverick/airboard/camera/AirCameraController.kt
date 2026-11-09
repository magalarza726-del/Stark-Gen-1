package com.maverick.airboard.camera

import android.content.Context
import android.util.Size
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.maverick.airboard.tracking.BlueCapAnalyzer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AirCameraController(
    private val context: Context,
    private val analyzer: BlueCapAnalyzer,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var generation = 0

    fun bind(owner: LifecycleOwner, previewView: PreviewView) {
        val gen = ++generation
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (gen != generation) return@addListener
            val p = future.get()
            provider = p
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
            val preview = Preview.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    ).build()
                )
                .setTargetRotation(rotation)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    ).build()
                )
                .setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            runCatching {
                p.unbindAll()
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.onFailure {
                camera = null
                p.unbindAll()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun focusAt(previewView: PreviewView, x: Float, y: Float) {
        val cam = camera ?: return
        if (previewView.width <= 0 || previewView.height <= 0) return
        val point = previewView.meteringPointFactory.createPoint(
            x.coerceIn(0f, previewView.width.toFloat()),
            y.coerceIn(0f, previewView.height.toFloat()),
            .16f,
        )
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB,
        ).setAutoCancelDuration(4, TimeUnit.SECONDS).build()
        runCatching { cam.cameraControl.startFocusAndMetering(action) }
    }

    fun focusCenter(previewView: PreviewView) {
        focusAt(previewView, previewView.width * .5f, previewView.height * .5f)
    }

    fun unbind() {
        generation++
        provider?.unbindAll()
        camera = null
    }

    fun close() {
        unbind()
        executor.shutdown()
    }
}
