package com.example.goobergolfin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private var pointPaint = Paint()
    private var linePaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    // Add offsets for centering
    private var postScaleWidthOffset: Float = 0f
    private var postScaleHeightOffset: Float = 0f

    init {
        initPaints()
    }

    fun clear() {
        results = null
        pointPaint.reset()
        linePaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color = ContextCompat.getColor(context!!, android.R.color.holo_green_light)
        linePaint.strokeWidth = 12f
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = 20f
        pointPaint.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        results?.let { poseLandmarkerResult ->
            for (landmark in poseLandmarkerResult.landmarks()) {
                for (normalizedLandmark in landmark) {
                    canvas.drawPoint(
                        normalizedLandmark.x() * imageWidth * scaleFactor + postScaleWidthOffset,
                        normalizedLandmark.y() * imageHeight * scaleFactor + postScaleHeightOffset,
                        pointPaint,
                    )
                }

                PoseLandmarker.POSE_LANDMARKS.forEach {
                    canvas.drawLine(
                        poseLandmarkerResult.landmarks()[0][it.start()].x() * imageWidth * scaleFactor + postScaleWidthOffset,
                        poseLandmarkerResult.landmarks()[0][it.start()].y() * imageHeight * scaleFactor + postScaleHeightOffset,
                        poseLandmarkerResult.landmarks()[0][it.end()].x() * imageWidth * scaleFactor + postScaleWidthOffset,
                        poseLandmarkerResult.landmarks()[0][it.end()].y() * imageHeight * scaleFactor + postScaleHeightOffset,
                        linePaint
                    )
                }
            }
        }
    }

    fun setResults(
        poseLandmarkerResults: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: com.google.mediapipe.tasks.vision.core.RunningMode = com.google.mediapipe.tasks.vision.core.RunningMode.LIVE_STREAM
    ) {
        results = poseLandmarkerResults

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        // Calculate scale and offsets to match CameraX PreviewView's FILL_CENTER behavior
        scaleFactor = max((width * 1f) / imageWidth, (height * 1f) / imageHeight)
        postScaleWidthOffset = (width - imageWidth * scaleFactor) / 2
        postScaleHeightOffset = (height - imageHeight * scaleFactor) / 2

        invalidate()
    }
}
