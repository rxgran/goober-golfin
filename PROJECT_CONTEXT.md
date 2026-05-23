# GooberGolfin Project Context

## Project Vision
**GooberGolfin** is an Android application designed for backyard golf swing tracking and distance estimation. The primary goal is to use a phone camera to analyze a golfer's biomechanics and predict ball flight metrics (like distance) without expensive hardware.

The current development phase focuses on **Data Collection**. By using the app at a professional golf simulator, the user creates a "Labeled Dataset" that pairs high-fidelity simulator data (Ground Truth) with the phone's visual analysis (Biometric Features).

## Technical Architecture

### 1. Vision Pipeline
*   **CameraX:** Provides the real-time video feed.
*   **MediaPipe Pose Landmarker:** Tracks 33 body landmarks in real-time.
*   **OverlayView.kt:** Handles scaling, centering, and drawing the skeleton and "Tracer" lines. It prioritizes the lead wrist for swing timing.

### 2. Swing Intelligence (`SwingAnalyzer.kt`)
A state machine that identifies phases of a golf swing based on wrist velocity and peak-lock logic:
*   **SCANNING:** Looking for a golfer.
*   **READY:** Triggered when the golfer is still and centered for ~0.6s.
*   **TAKEAWAY:** Movement detected away from address.
*   **DOWNSWING:** Triggered when wrists drop from their peak height.
*   **FINISH:** Triggered by "Impact Blur," "Horizontal Whip," or "Y-axis reversal."

### 3. Biometric Metrics (AI Features)
*   **Tempo:** Backswing-to-Downswing ratio (Pro target is 3.0:1).
*   **Head Stability:** Displacement % of the nose from address.
*   **Hip Stability:** 
    *   *Face-On:* Lateral "Sway" %.
    *   *Down-the-Line:* Posture/Depth shift %.
*   **Hip Turn:** Horizontal rotation/compression of hips at impact.
*   **Foot Stability:** Movement of the lead heel.

## Data Logging & Dataset Format
The app saves every swing to a local CSV file: `/Android/data/com.example.goobergolfin/files/goober_golf_data.csv`.

### CSV Columns (86 total):
*   **Context:** Timestamp, User (Ranier/Cara/Custom), Mode (Face-On/DTL), Club.
*   **Labels (Simulator Data):** Carry, Total, Apex, Ball Speed, Swing Speed, Spin, Accuracy, Shot Shape, Contact Quality.
*   **Features:** Tempo, BT (ms), DT (ms), Head%, Hip Stability%, Hip Turn%, Foot%.
*   **Raw Data (Visual DNA):** X/Y coordinates for 11 major joints captured at **Address, Top, and Impact**.

## UX Features
*   **Dual Mode Support:** Specific calibration guides and triggers for Face-On and Down-the-Line views.
*   **Audio Feedback:** Beeps for "READY" and "FINISH" states.
*   **History Sidebar:** Shows the last 3 swings with clickable cards for editing/deleting data.
*   **Export Flow:** A "Download" button copies the CSV to the public `Downloads/GooberGolfin` folder for easy PC access.

## Current Setup Recommendations
*   **Distance:** 8–10 feet.
*   **Height:** Knee to Waist level (Stay consistent between simulator and backyard).
*   **Alignment:** Use the "Wrench" icon to toggle the silhouette guide.

## Next Steps
1.  **Collect Dataset:** Hit 50+ shots per user across various clubs at a simulator.
2.  **Model Training:** Use the CSV to train Regression and Classification Models (e.g., XGBoost, Random Forest, or Neural Networks) in Python. Goal is to predict all simulator labels (Carry, Ball Speed, Swing Speed, Spin, Apex, Accuracy, etc.) using the biometric and raw visual DNA features.
3.  **Backyard Deployment:** Integrate the trained models back into the app for real-time shot estimation and feedback without a simulator.
