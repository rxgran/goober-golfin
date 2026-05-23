package com.example.goobergolfin

import kotlin.math.abs
import kotlin.math.sqrt

enum class SwingState {
    SCANNING, READY, TAKEAWAY, DOWNSWING, FINISH
}

data class PoseSnapshot(
    val joints: Map<Int, Pair<Float, Float>> // Landmark Index -> (X, Y)
)

data class SwingMetrics(
    val id: String = java.util.UUID.randomUUID().toString(),
    val tempo: Float,
    val backswingMs: Long,
    val downswingMs: Long,
    val headMovement: Float, 
    val hipStability: Float, 
    val hipTurn: Float,      
    val footStability: Float, 
    val isFaceOn: Boolean,
    val addressPose: PoseSnapshot?,
    val topPose: PoseSnapshot?,
    val impactPose: PoseSnapshot?
)

class SwingAnalyzer {
    var currentState = SwingState.SCANNING
        private set
    
    private var takeawayTime: Long = 0
    private var topOfBackswingTime: Long = 0
    private var impactTime: Long = 0
    
    // Snapshot Storage
    private var addressPose: PoseSnapshot? = null
    private var topPose: PoseSnapshot? = null
    private var impactPose: PoseSnapshot? = null

    private var addressWristY = 0f
    private var addressHeadX = 0f
    private var addressHeadY = 0f
    private var addressHipX = 0f
    private var addressHipWidth = 0f
    private var addressLeadHeelX = 0f
    private var addressLeadHeelY = 0f
    
    private var maxHeadDisplacement = 0f
    private var maxHipStabilityChange = 0f
    private var minHipWidthDuringSwing = 1.0f
    private var maxHipWidthDuringSwing = 0f
    private var maxLeadHeelMovement = 0f
    
    private var peakWristY = 1.0f 
    private var lowestWristYDuringDownswing = 1.0f
    private var maxDownswingSpeed = 0f
    
    private var smoothedX = -1f
    private var smoothedY = -1f
    private val alpha = 0.25f 

    private val takeawayThreshold = 0.015f 
    private val stillThreshold = 0.01f
    
    private var lastY = -1f
    private var lastX = -1f
    private var framesStill = 0
    private val framesToReady = 20 
    private var framesInDownswing = 0
    private var framesHidden = 0
    private var lastIsFaceOn = true

    var lastBackswingMs: Long = 0
    var lastDownswingMs: Long = 0
    var lastMetrics: SwingMetrics? = null

    var debugWristY: Float = 0f
    var debugVelocity: Float = 0f
    
    var onStateChanged: ((SwingState) -> Unit)? = null
    var onSwingComplete: ((SwingMetrics) -> Unit)? = null

    fun reset() {
        currentState = SwingState.SCANNING
        peakWristY = 1.0f
        lastY = -1f
        lastX = -1f
        smoothedX = -1f
        smoothedY = -1f
        maxDownswingSpeed = 0f
        maxHeadDisplacement = 0f
        maxHipStabilityChange = 0f
        minHipWidthDuringSwing = 1.0f
        maxHipWidthDuringSwing = 0f
        maxLeadHeelMovement = 0f
        framesStill = 0
        framesHidden = 0
        addressPose = null
        topPose = null
        impactPose = null
        onStateChanged?.invoke(currentState)
    }

    fun setScanning() {
        if (currentState == SwingState.READY || currentState == SwingState.SCANNING) {
            reset()
        }
    }

    fun addFrame(
        wristX: Float?, wristY: Float?, 
        noseX: Float?, noseY: Float?,
        hipLeftX: Float?, hipRightX: Float?,
        leadHeelX: Float?, leadHeelY: Float?,
        fullLandmarks: Map<Int, Pair<Float, Float>>?, // Passing major raw joints
        timestamp: Long, isFaceOn: Boolean
    ) {
        lastIsFaceOn = isFaceOn
        if (wristY == null || wristX == null) {
            if (isFaceOn && currentState == SwingState.DOWNSWING && framesInDownswing > 3) {
                triggerFinish(fullLandmarks, timestamp)
            } else if (currentState != SwingState.SCANNING && currentState != SwingState.FINISH) {
                framesHidden++
                if (framesHidden > 20) reset()
            }
            return
        }
        
        framesHidden = 0
        smoothedX = if (smoothedX < 0) wristX else smoothedX + alpha * (wristX - smoothedX)
        smoothedY = if (smoothedY < 0) wristY else smoothedY + alpha * (wristY - smoothedY)
        
        if (lastY < 0) {
            lastY = smoothedY
            lastX = smoothedX
            return
        }

        val vY = lastY - smoothedY 
        val vX = lastX - smoothedX
        val currentSpeed = sqrt(vY*vY + vX*vX)

        when (currentState) {
            SwingState.SCANNING -> {
                if (currentSpeed < stillThreshold) {
                    framesStill++
                    if (framesStill > framesToReady) {
                        currentState = SwingState.READY
                        addressHeadX = noseX ?: 0.5f
                        addressHeadY = noseY ?: 0.2f
                        addressHipX = ((hipLeftX ?: 0.45f) + (hipRightX ?: 0.55f)) / 2f
                        addressHipWidth = abs((hipRightX ?: 0.55f) - (hipLeftX ?: 0.45f))
                        addressLeadHeelX = leadHeelX ?: 0.4f
                        addressLeadHeelY = leadHeelY ?: 0.8f
                        
                        // CAPTURE ADDRESS POSE
                        addressPose = fullLandmarks?.let { PoseSnapshot(it) }
                        
                        onStateChanged?.invoke(currentState)
                    }
                } else {
                    framesStill = 0
                }
            }
            
            SwingState.READY -> {
                if (currentSpeed > takeawayThreshold) {
                    currentState = SwingState.TAKEAWAY
                    takeawayTime = timestamp
                    addressWristY = lastY
                    peakWristY = smoothedY
                    onStateChanged?.invoke(currentState)
                }
            }
            
            SwingState.TAKEAWAY, SwingState.DOWNSWING -> {
                // Tracking metrics...
                noseX?.let { nx ->
                    noseY?.let { ny ->
                        val headDist = sqrt(abs(nx - addressHeadX).let { it*it } + abs(ny - addressHeadY).let { it*it })
                        if (headDist > maxHeadDisplacement) maxHeadDisplacement = headDist
                    }
                }
                val currentHipCenter = ((hipLeftX ?: 0.4f) + (hipRightX ?: 0.6f)) / 2f
                val shift = abs(currentHipCenter - addressHipX)
                if (shift > maxHipStabilityChange) maxHipStabilityChange = shift
                val currentHipWidth = abs((hipRightX ?: 0.6f) - (hipLeftX ?: 0.4f))
                if (isFaceOn) { if (currentHipWidth < minHipWidthDuringSwing) minHipWidthDuringSwing = currentHipWidth }
                else { if (currentHipWidth > maxHipWidthDuringSwing) maxHipWidthDuringSwing = currentHipWidth }
                leadHeelX?.let { hx ->
                    leadHeelY?.let { hy ->
                        val heelDist = sqrt(abs(hx - addressLeadHeelX).let { it*it } + abs(hy - addressLeadHeelY).let { it*it })
                        if (heelDist > maxLeadHeelMovement) maxLeadHeelMovement = heelDist
                    }
                }

                if (currentState == SwingState.TAKEAWAY) {
                    if (smoothedY < peakWristY) peakWristY = smoothedY
                    if (timestamp - takeawayTime > 3500) { reset(); return }
                    val requiredDrop = if (isFaceOn) 0.035f else 0.025f
                    if (smoothedY > (peakWristY + requiredDrop) && smoothedY < 0.75f) {
                        currentState = SwingState.DOWNSWING
                        topOfBackswingTime = timestamp
                        
                        // CAPTURE TOP POSE
                        topPose = fullLandmarks?.let { PoseSnapshot(it) }
                        
                        maxDownswingSpeed = 0f
                        framesInDownswing = 0
                        onStateChanged?.invoke(currentState)
                    }
                } else {
                    framesInDownswing++
                    if (currentSpeed > maxDownswingSpeed) maxDownswingSpeed = currentSpeed
                    if (timestamp - topOfBackswingTime > 1500) { reset(); return }
                    if (framesInDownswing > 2) {
                        val backAtAddress = smoothedY >= addressWristY - 0.04f
                        val hasReversedY = (lowestWristYDuringDownswing - smoothedY) > 0.015f
                        if (smoothedY > (addressWristY - 0.15f) && (abs(vX) > 0.02f || backAtAddress || hasReversedY)) {
                            triggerFinish(fullLandmarks, timestamp)
                        }
                    }
                    if (smoothedY > lowestWristYDuringDownswing) lowestWristYDuringDownswing = smoothedY
                }
            }
            SwingState.FINISH -> {}
        }
        lastY = smoothedY
        lastX = smoothedX
    }

    private fun triggerFinish(landmarks: Map<Int, Pair<Float, Float>>?, timestamp: Long) {
        if (currentState != SwingState.DOWNSWING) return
        currentState = SwingState.FINISH
        impactTime = timestamp
        
        // CAPTURE IMPACT POSE
        impactPose = landmarks?.let { PoseSnapshot(it) }
        
        calculateFinalMetrics()
        onStateChanged?.invoke(currentState)
    }

    private fun calculateFinalMetrics() {
        lastBackswingMs = topOfBackswingTime - takeawayTime
        lastDownswingMs = impactTime - topOfBackswingTime
        if (lastDownswingMs > 60 && lastBackswingMs > 300) {
            val turnScore = if (lastIsFaceOn) (1.0f - (minHipWidthDuringSwing / addressHipWidth)).coerceIn(0f, 1f)
                           else ((maxHipWidthDuringSwing - addressHipWidth) / 0.15f).coerceIn(0f, 1f)
            lastMetrics = SwingMetrics(
                tempo = lastBackswingMs.toFloat() / lastDownswingMs.toFloat(),
                backswingMs = lastBackswingMs, downswingMs = lastDownswingMs,
                headMovement = maxHeadDisplacement, hipStability = maxHipStabilityChange,
                hipTurn = turnScore, footStability = maxLeadHeelMovement, isFaceOn = lastIsFaceOn,
                addressPose = addressPose, topPose = topPose, impactPose = impactPose
            )
            onSwingComplete?.invoke(lastMetrics!!)
        } else { reset() }
    }
}
