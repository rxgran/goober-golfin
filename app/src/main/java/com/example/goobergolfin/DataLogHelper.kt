package com.example.goobergolfin

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.*

data class LoggedSwing(
    val timestamp: String,
    val user: String,
    val club: String,
    val metrics: SwingMetrics,
    val carry: String,
    val total: String,
    val apex: String,
    val ballSpeed: String,
    val swingSpeed: String,
    val spin: String,
    val accSide: String,
    val accDist: String,
    val shotShape: String,
    val contact: String
)

class DataLogHelper(val context: Context) {

    private val fileName = "goober_golf_data.csv"
    private val majorJoints = listOf(0, 11, 12, 15, 16, 23, 24, 25, 26, 27, 28)
    
    fun saveSession(swings: List<LoggedSwing>): String? {
        val folder = context.getExternalFilesDir(null)
        val file = File(folder, fileName)
        
        try {
            val out = FileOutputStream(file, false) 
            
            var header = "ID,Timestamp,User,Mode,Club,Tempo,Backswing_ms,Downswing_ms,Head_Movement,Hip_Stability,Hip_Turn,Foot_Stability," +
                        "Carry_Distance,Total_Distance,Apex_Height,Ball_Speed,Swing_Speed,Spin,Accuracy_Side,Accuracy_Dist,Shot_Shape,Contact_Quality"
            
            val moments = listOf("Address", "Top", "Impact")
            for (moment in moments) {
                for (joint in majorJoints) {
                    header += ",${moment}_J${joint}_X,${moment}_J${joint}_Y"
                }
            }
            header += "\n"
            out.write(header.toByteArray())

            for (item in swings) {
                val mode = if (item.metrics.isFaceOn) "Face-On" else "DTL"
                var row = String.format(Locale.US, 
                    "%s,%s,%s,%s,%s,%.2f,%d,%d,%.3f,%.3f,%.3f,%.3f,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                    item.metrics.id, item.timestamp, item.user, mode, item.club, 
                    item.metrics.tempo, item.metrics.backswingMs, item.metrics.downswingMs,
                    item.metrics.headMovement, item.metrics.hipStability, item.metrics.hipTurn, item.metrics.footStability,
                    item.carry, item.total, item.apex, item.ballSpeed, item.swingSpeed, item.spin, 
                    item.accSide, item.accDist, item.shotShape, item.contact
                )

                val snapshots = listOf(item.metrics.addressPose, item.metrics.topPose, item.metrics.impactPose)
                for (snap in snapshots) {
                    for (joint in majorJoints) {
                        val coords = snap?.joints?.get(joint)
                        row += ",${coords?.first ?: ""},${coords?.second ?: ""}"
                    }
                }
                row += "\n"
                out.write(row.toByteArray())
            }
            
            out.close()
            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun exportToDownloads() {
        val folder = context.getExternalFilesDir(null)
        val sourceFile = File(folder, fileName)
        if (!sourceFile.exists()) {
            Toast.makeText(context, "Nothing to export!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "goober_golf_export_${System.currentTimeMillis()}.csv")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/GooberGolfin")
                }
            }

            val resolver = context.contentResolver
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                null
            }

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Toast.makeText(context, "Exported to Downloads/GooberGolfin", Toast.LENGTH_LONG).show()
            } ?: run {
                Toast.makeText(context, "Export failed on this Android version", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Export Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun shareCSV() {
        val folder = context.getExternalFilesDir(null)
        val file = File(folder, fileName)
        if (!file.exists()) {
            Toast.makeText(context, "No data collected yet!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(context, "com.example.goobergolfin.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Swing Data", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share Swing Data")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
