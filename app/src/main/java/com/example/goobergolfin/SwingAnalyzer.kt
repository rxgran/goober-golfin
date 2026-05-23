package com.example.goobergolfin

import android.util.Log

enum class SwingState {
    IDLE, TAKEAWAY, DOWNSWING, FINISH
}

class SwingAnalyzer {
    private var currentState = SwingState.IDLE
    
    private var takeawayTime: Long = 0
    private var topOfBackswingTime: Long = 0
    private var impactTime: Long = 0
    
    private var minWristY = 1.0f // Normalized Y (0 is top, 1 is bottom)
    private var addressWristY = 0f
    
    private val velocityThreshold = 0.01f // Movement threshold
    private var lastWristY = -1f

    var lastTempo: Float = 0f
    var onStateChanged: ((SwingState) -> Unit)? = null
    var onSwingComplete: ((Float) -> Unit)? = null

    fun reset() {
        currentState = SwingState.IDLE
        minWristY = 1.0f
        lastWristY = -1f
        onStateChanged?.invoke(currentState)
    }

    fun addFrame(wristY: Float, timestamp: Long) {
        if (lastWristY < 0) {
            lastWristY = wristY
            return
        }

        val velocity = lastWristY - wristY // Positive means moving UP (Y decreasing)

        when (currentState) {
            SwingState.IDLE -> {
                // Detect Takeaway: Significant upward movement from address
                if (velocity > velocityThreshold) {
                    currentState = SwingState.TAKEAWAY
                    takeawayTime = timestamp
                    addressWristY = lastWristY
                    minWristY = wristY
                    onStateChanged?.invoke(currentState)
                }
            }
            SwingState.TAKEAWAY -> {
                // Keep track of highest point
                if (wristY < minWristY) {
                    minWristY = wristY
                }
                
                // Detect Top: Wrist starts moving DOWN significantly
                if (velocity < -velocityThreshold && wristY < 0.5f) { // Must be in top half
                    currentState = SwingState.DOWNSWING
                    topOfBackswingTime = timestamp
                    onStateChanged?.invoke(currentState)
                }
            }
            SwingState.DOWNSWING -> {
                // Detect Impact: Wrist returns to address height
                if (wristY >= addressWristY - 0.05f) {
                    currentState = SwingState.FINISH
                    impactTime = timestamp
                    calculateTempo()
                    onStateChanged?.invoke(currentState)
                }
            }
            SwingState.FINISH -> {
                // Wait for reset (handled by OverlayView auto-reset)
            }
        }

        lastWristY = wristY
    }

    private fun calculateTempo() {
        val backswingDuration = topOfBackswingTime - takeawayTime
        val downswingDuration = impactTime - topOfBackswingTime

        if (downswingDuration > 0) {
            lastTempo = backswingDuration.toFloat() / downswingDuration.toFloat()
            Log.d("SwingAnalyzer", "Swing Complete! Back: $backswingDuration, Down: $downswingDuration, Tempo: $lastTempo")
            onSwingComplete?.invoke(lastTempo)
        }
    }
}
