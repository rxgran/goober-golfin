package com.example.goobergolfin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.LinkedList
import kotlin.math.max
import kotlin.math.sqrt

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private var pointPaint = Paint()
    private var linePaint = Paint()
    private var tracerPaint = Paint()
    private var guidePaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    private var postScaleWidthOffset: Float = 0f
    private var postScaleHeightOffset: Float = 0f

    // Calibration Guide
    var showGuide: Boolean = false
    var currentSwingView: SwingView = SwingView.FACE_ON

    // History for hand tracer
    private val leftHandHistory = LinkedList<Pair<Float, Float>>()
    private val rightHandHistory = LinkedList<Pair<Float, Float>>()
    private val maxHistorySize = 100 

    // Smoothing (Low-pass filter)
    private var lastLeftX = -1f
    private var lastLeftY = -1f
    private var lastRightX = -1f
    private var lastRightY = -1f
    private val smoothingFactor = 0.2f 

    // Auto-reset logic
    private var framesStill = 0
    private val stillThreshold = 15f 
    private val framesToReset = 10 

    // Swing Analysis
    private val swingAnalyzer = SwingAnalyzer()
    var onSwingDetected: ((Float) -> Unit)? = null
    var onStateChanged: ((SwingState) -> Unit)? = null

    init {
        initPaints()
        swingAnalyzer.onSwingComplete = { tempo ->
            onSwingDetected?.invoke(tempo)
        }
        swingAnalyzer.onStateChanged = { state ->
            onStateChanged?.invoke(state)
        }
    }

    fun clear() {
        results = null
        leftHandHistory.clear()
        rightHandHistory.clear()
        lastLeftX = -1f
        lastLeftY = -1f
        lastRightX = -1f
        lastRightY = -1f
        framesStill = 0
        swingAnalyzer.reset()
        invalidate()
    }

    private fun initPaints() {
        linePaint.color = ContextCompat.getColor(context!!, android.R.color.holo_green_light)
        linePaint.strokeWidth = 8f
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = 15f
        pointPaint.style = Paint.Style.FILL

        tracerPaint.color = Color.CYAN
        tracerPaint.strokeWidth = 8f
        tracerPaint.style = Paint.Style.STROKE
        tracerPaint.strokeJoin = Paint.Join.ROUND
        tracerPaint.strokeCap = Paint.Cap.ROUND
        tracerPaint.alpha = 180

        guidePaint.color = Color.WHITE
        guidePaint.strokeWidth = 4f
        guidePaint.style = Paint.Style.STROKE
        guidePaint.alpha = 100 
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        if (showGuide) {
            drawCalibrationGuide(canvas)
        }

        drawTracerPath(canvas, leftHandHistory)
        drawTracerPath(canvas, rightHandHistory)

        results?.let { poseLandmarkerResult ->
            for (landmark in poseLandmarkerResult.landmarks()) {
                PoseLandmarker.POSE_LANDMARKS.forEach {
                    canvas.drawLine(
                        landmark[it.start()].x() * imageWidth * scaleFactor + postScaleWidthOffset,
                        landmark[it.start()].y() * imageHeight * scaleFactor + postScaleHeightOffset,
                        landmark[it.end()].x() * imageWidth * scaleFactor + postScaleWidthOffset,
                        landmark[it.end()].y() * imageHeight * scaleFactor + postScaleHeightOffset,
                        linePaint
                    )
                }
                for (normalizedLandmark in landmark) {
                    canvas.drawPoint(
                        normalizedLandmark.x() * imageWidth * scaleFactor + postScaleWidthOffset,
                        normalizedLandmark.y() * imageHeight * scaleFactor + postScaleHeightOffset,
                        pointPaint,
                    )
                }
            }
        }
    }

    private fun drawCalibrationGuide(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        
        if (currentSwingView == SwingView.FACE_ON) {
            val boxWidth = w * 0.5f
            val boxHeight = h * 0.7f
            val left = (w - boxWidth) / 2
            val top = (h - boxHeight) / 2
            canvas.drawRect(left, top, left + boxWidth, top + boxHeight, guidePaint)
            val waistY = h * 0.6f
            canvas.drawLine(0f, waistY, w, waistY, guidePaint)
            canvas.drawCircle(w/2, top + (boxHeight * 0.15f), boxWidth * 0.15f, guidePaint)
        } else {
            val boxWidth = w * 0.35f
            val boxHeight = h * 0.7f
            val left = w * 0.15f 
            val top = (h - boxHeight) / 2
            canvas.drawRect(left, top, left + boxWidth, top + boxHeight, guidePaint)
            canvas.drawLine(w * 0.75f, h * 0.85f, left + (boxWidth * 0.5f), h * 0.3f, guidePaint)
            val waistY = h * 0.6f
            canvas.drawLine(0f, waistY, w, waistY, guidePaint)
        }
    }

    private fun drawTracerPath(canvas: Canvas, history: LinkedList<Pair<Float, Float>>) {
        if (history.size < 2) return
        val path = Path()
        val (startX, startY) = history[0]
        path.moveTo(startX, startY)
        for (i in 1 until history.size) {
            val (x, y) = history[i]
            path.lineTo(x, y)
        }
        canvas.drawPath(path, tracerPaint)
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

        scaleFactor = max((width * 1f) / imageWidth, (height * 1f) / imageHeight)
        postScaleWidthOffset = (width - imageWidth * scaleFactor) / 2
        postScaleHeightOffset = (height - imageHeight * scaleFactor) / 2

        poseLandmarkerResults.landmarks().firstOrNull()?.let { landmarks ->
            val leftWrist = landmarks[15]
            val rightWrist = landmarks[16]

            if (rightWrist.visibility().orElse(0f) > 0.8f) {
                val rawX = rightWrist.x() * imageWidth * scaleFactor + postScaleWidthOffset
                val rawY = rightWrist.y() * imageHeight * scaleFactor + postScaleHeightOffset
                val smoothedX = if (lastRightX < 0) rawX else lastRightX + smoothingFactor * (rawX - lastRightX)
                val smoothedY = if (lastRightY < 0) rawY else lastRightY + smoothingFactor * (rawY - lastRightY)
                val dist = if (lastRightX < 0) 0f else sqrt((smoothedX - lastRightX).let { it * it } + (smoothedY - lastRightY).let { it * it })
                
                if (rightWrist.y() > 0.6f && dist < stillThreshold) {
                    framesStill++
                    if (framesStill > framesToReset && rightHandHistory.isNotEmpty()) {
                        leftHandHistory.clear()
                        rightHandHistory.clear()
                        swingAnalyzer.reset()
                    }
                } else {
                    framesStill = 0
                }

                lastRightX = smoothedX
                lastRightY = smoothedY
                addToHistory(rightHandHistory, smoothedX, smoothedY)
                
                // Process Swing State
                swingAnalyzer.addFrame(rightWrist.y(), System.currentTimeMillis())
            }

            if (leftWrist.visibility().orElse(0f) > 0.8f) {
                val rawX = leftWrist.x() * imageWidth * scaleFactor + postScaleWidthOffset
                val rawY = leftWrist.y() * imageHeight * scaleFactor + postScaleHeightOffset
                val smoothedX = if (lastLeftX < 0) rawX else lastLeftX + smoothingFactor * (rawX - lastLeftX)
                val smoothedY = if (lastLeftY < 0) rawY else lastLeftY + smoothingFactor * (rawY - lastLeftY)
                lastLeftX = smoothedX
                lastLeftY = smoothedY
                addToHistory(leftHandHistory, smoothedX, smoothedY)
            }
        }
        invalidate()
    }

    private fun addToHistory(history: LinkedList<Pair<Float, Float>>, screenX: Float, screenY: Float) {
        history.add(Pair(screenX, screenY))
        if (history.size > maxHistorySize) history.removeFirst()
    }
}
