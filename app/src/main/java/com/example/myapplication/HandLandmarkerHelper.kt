/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.example.myapplication.position.PositionChange
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.acos

import kotlin.math.pow
import kotlin.math.sqrt

class HandLandmarkerHelper(
    var minHandDetectionConfidence: Float = DEFAULT_HAND_DETECTION_CONFIDENCE,
    var minHandTrackingConfidence: Float = DEFAULT_HAND_TRACKING_CONFIDENCE,
    var minHandPresenceConfidence: Float = DEFAULT_HAND_PRESENCE_CONFIDENCE,
    var maxNumHands: Int = DEFAULT_NUM_HANDS,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    val handLandmarkerHelperListener: LandmarkerListener? = null

    // this listener is only used when running in RunningMode.LIVE_STREAM
) {

    // For this example this needs to be a var so it can be reset on changes.
    // If the Hand Landmarker will not change, a lazy val would be preferable.
    private var handLandmarker: HandLandmarker? = null
    private var isCalibrate : Boolean = false

    private var positionChange : PositionChange = PositionChange()

    init {
        setupHandLandmarker()
    }

    fun clearHandLandmarker() {
        handLandmarker?.close()
        handLandmarker = null
    }

    // Return running status of HandLandmarkerHelper
    fun isClose(): Boolean {
        return handLandmarker == null
    }

    // Initialize the Hand landmarker using current settings on the
    // thread that is using it. CPU can be used with Landmarker
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the
    // Landmarker
    fun setupHandLandmarker() {
        // Set general hand landmarker options
        val baseOptionBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU
        when (currentDelegate) {
            DELEGATE_CPU -> {
                Log.e(
                    "민규", "CPU"
                )
                baseOptionBuilder.setDelegate(Delegate.CPU)
            }
            DELEGATE_GPU -> {
                Log.e(
                    "민규", "GPU"
                )
                baseOptionBuilder.setDelegate(Delegate.GPU)
            }
        }

        baseOptionBuilder.setModelAssetPath(MP_HAND_LANDMARKER_TASK)

        // Check if runningMode is consistent with handLandmarkerHelperListener
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (handLandmarkerHelperListener == null) {
                    throw IllegalStateException(
                        "handLandmarkerHelperListener must be set when runningMode is LIVE_STREAM."
                    )
                }
            }
            else -> {
                // no-op
            }
        }

        try {
            val baseOptions = baseOptionBuilder.build()
            // Create an option builder with base options and specific
            // options only use for Hand Landmarker.
            val optionsBuilder =
                HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinHandDetectionConfidence(minHandDetectionConfidence)
                    .setMinTrackingConfidence(minHandTrackingConfidence)
                    .setMinHandPresenceConfidence(minHandPresenceConfidence)
                    .setNumHands(maxNumHands)
                    .setRunningMode(runningMode)
            // The ResultListener and ErrorListener only use for LIVE_STREAM mode.
            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }
            val options = optionsBuilder.build()
            Log.e("민규", options.toString())
            handLandmarker =
                HandLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            handLandmarkerHelperListener?.onError(
                "Hand Landmarker failed to initialize. See error logs for " +
                        "details" + e.message
            )
            Log.e(
                TAG, "MediaPipe failed to load the task with error: " + e
                    .message
            )
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU
            handLandmarkerHelperListener?.onError(
                "Hand Landmarker failed to initialize. See error logs for " +
                        "details" + e.message, GPU_ERROR
            )
            Log.e(
                TAG,
                "Image classifier failed to load model with error: " + e.message
            )
        }
    }

    // Convert the ImageProxy to MP Image and feed it to HandlandmakerHelper.
    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLiveStream" +
                        " while not using RunningMode.LIVE_STREAM"
            )
        }
        val frameTime = SystemClock.uptimeMillis()

        // Copy out RGB bits from the frame to a bitmap buffer
        val bitmapBuffer =
            Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            // Rotate the frame received from the camera to be in the same direction as it'll be shown
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            // flip image if user use front camera
            if (isFrontCamera) {
                postScale(
                    -1f,
                    1f,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        detectAsync(mpImage, frameTime)
    }

    // Run hand hand landmark using MediaPipe Hand Landmarker API
    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
//        Log.d("민규","3")
//        Log.d("민규", mpImage.height.toString())
//        Log.d("민규", mpImage.width.toString())
//        Log.d("민규", mpImage.toString())
        handLandmarker?.detectAsync(mpImage, frameTime)
        // As we're using running mode LIVE_STREAM, the landmark result will
        // be returned in returnLivestreamResult function
    }

    // Accepts the URI for a video file loaded from the user's gallery and attempts to run
    // hand landmarker inference on the video. This process will evaluate every
    // frame in the video and attach the results to a bundle that will be
    // returned.
    fun detectVideoFile(
        videoUri: Uri,
        inferenceIntervalMs: Long
    ): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call detectVideoFile" +
                        " while not using RunningMode.VIDEO"
            )
        }

        // Inference time is the difference between the system time at the start and finish of the
        // process
        val startTime = SystemClock.uptimeMillis()

        var didErrorOccurred = false

        // Load frames from the video and run the hand landmarker.
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)
        val videoLengthMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLong()

        // Note: We need to read width/height from frame instead of getting the width/height
        // of the video directly because MediaRetriever returns frames that are smaller than the
        // actual dimension of the video file.
        val firstFrame = retriever.getFrameAtTime(0)
        val width = firstFrame?.width
        val height = firstFrame?.height

        // If the video is invalid, returns a null detection result
        if ((videoLengthMs == null) || (width == null) || (height == null)) return null

        // Next, we'll get one frame every frameInterval ms, then run detection on these frames.
        val resultList = mutableListOf<HandLandmarkerResult>()
        val numberOfFrameToRead = videoLengthMs.div(inferenceIntervalMs)

        for (i in 0..numberOfFrameToRead) {
            val timestampMs = i * inferenceIntervalMs // ms

            retriever
                .getFrameAtTime(
                    timestampMs * 1000, // convert from ms to micro-s
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                ?.let { frame ->
                    // Convert the video frame to ARGB_8888 which is required by the MediaPipe
                    val argb8888Frame =
                        if (frame.config == Bitmap.Config.ARGB_8888) frame
                        else frame.copy(Bitmap.Config.ARGB_8888, false)

                    // Convert the input Bitmap object to an MPImage object to run inference
                    val mpImage = BitmapImageBuilder(argb8888Frame).build()

                    // Run hand landmarker using MediaPipe Hand Landmarker API
                    handLandmarker?.detectForVideo(mpImage, timestampMs)
                        ?.let { detectionResult ->
                            resultList.add(detectionResult)
                        } ?: {
                        didErrorOccurred = true
                        handLandmarkerHelperListener?.onError(
                            "ResultBundle could not be returned" +
                                    " in detectVideoFile"
                        )
                    }
                }
                ?: run {
                    didErrorOccurred = true
                    handLandmarkerHelperListener?.onError(
                        "Frame at specified time could not be" +
                                " retrieved when detecting in video."
                    )
                }
        }

        retriever.release()

        val inferenceTimePerFrameMs =
            (SystemClock.uptimeMillis() - startTime).div(numberOfFrameToRead)

        return if (didErrorOccurred) {
            null
        } else {
            ResultBundle(resultList, inferenceTimePerFrameMs, height, width)
        }
    }

    // Accepted a Bitmap and runs hand landmarker inference on it to return
    // results back to the caller
    fun detectImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call detectImage" +
                        " while not using RunningMode.IMAGE"
            )
        }


        // Inference time is the difference between the system time at the
        // start and finish of the process
        val startTime = SystemClock.uptimeMillis()

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(image).build()

        // Run hand landmarker using MediaPipe Hand Landmarker API
        handLandmarker?.detect(mpImage)?.also { landmarkResult ->
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            return ResultBundle(
                listOf(landmarkResult),
                inferenceTimeMs,
                image.height,
                image.width
            )
        }

        // If handLandmarker?.detect() returns null, this is likely an error. Returning null
        // to indicate this.
        handLandmarkerHelperListener?.onError(
            "Hand Landmarker failed to detect."
        )
        return null
    }

    // Return the landmark result to this HandLandmarkerHelper's caller
    private fun returnLivestreamResult(
            result: HandLandmarkerResult,
        input: MPImage
    ) {
//        Log.d("민규", "2")
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        onResults(
            ResultBundle(
                listOf(result),
                inferenceTime,
                input.height,
                input.width
            )
        )
    }

    private fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        val landmarksList = resultBundle.results.first().landmarks()
        val handednessList = resultBundle.results.first().handednesses()

        val handedness1 = handednessList.getOrNull(0)?.toString()
        val rightRegex = Regex("Right")

        val handType1 = if (handedness1 != null) {
            if (rightRegex.find(handedness1)?.value == "Right") {
                "Left hand"
            } else {
                "Right hand"
            }
        } else {
            null
        }

        val handedness2 = handednessList.getOrNull(1)?.toString()

        val handType2 = if (handedness2 != null) {
            if (rightRegex.find(handedness2)?.value == "Right") {
                "Left hand"
            } else {
                "Right hand"
            }
        } else {
            null
        }

        val firstHandLandmarks = landmarksList.getOrNull(0)
//        if (firstHandLandmarks != null) {
//            Log.d("민규", "$handType1 Landmarks: $firstHandLandmarks")
//        }

        val secondHandLandmarks = landmarksList.getOrNull(1)
//        if (secondHandLandmarks != null) {
//            Log.d("민규", "$handType2 Landmarks: $secondHandLandmarks")
//        }

//        Log.d("민규", "Detected Hands: $handType1 + $handType2")

        if(!isCalibrate){
            if(handType1 == "Left hand"){
                LeftCalibration(firstHandLandmarks)
                RightCalibration(secondHandLandmarks)
            }else{
                RightCalibration(firstHandLandmarks)
                LeftCalibration(secondHandLandmarks)
            }
        }else{
            //캘리가 끝나 사이트에 값을 보내는 로직
        }


    }

//    private fun calibrate(handLandmarks: MutableList<NormalizedLandmark>?): List<Float>? {
//        if (handLandmarks == null) {
//            return null
//        }
//
//        val numLandmarks = 20
//
////        val averageLandmarks = MutableList(numLandmarks) { FloatArray(3) }
////
////        // Initialize averageLandmarks with zeros
////        for (i in 0 until numLandmarks) {
////            for (j in 0 until 3) {
////                averageLandmarks[i][j] = 0f
////            }
////        }
////
////        // Accumulate landmark positions over the frames
////
////
////        // Calculate the average by dividing by the number of frames
////        for (i in 0 until numLandmarks) {
////            for (j in 0 until 3) {
////                averageLandmarks[i][j] /= numFrames.toFloat()
////            }
////        }
//
//        // Flatten the 2D array to a 1D list
////        val flattenedList = averageLandmarks.flatten()
//
////        return flattenedList
//        return nullc
//    } 


    data class Point(val x: Float, val y: Float)

    fun euclideanDistance(point1: Point, point2: Point): Double {
        var sum = 0.0

        sum += (point1.x - point2.x).pow(2)
        sum += (point1.y - point2.y).pow(2)

        return sqrt(sum)
    }

    private fun RightCalibration(firstHandLandmarks: MutableList<NormalizedLandmark>?) {
//    val calibratedValues = calibrate(firstHandLandmarks)

        // Ensure firstHandLandmarks is not null and contains at least 21 landmarks
        if (firstHandLandmarks == null || firstHandLandmarks.size < 21) {
            Log.d("준엽", "Invalid number of landmarks")
            return
        }

        fun calculateVector(p1: Point, p2: Point): Point {
            return Point(p2.x - p1.x, p2.y - p1.y)
        }

        fun calculateDotProduct(v1: Point, v2: Point): Float {
            return v1.x * v2.x + v1.y * v2.y
        }

        fun calculateMagnitude(v: Point): Float {
            return sqrt(v.x * v.x + v.y * v.y)
        }

        fun calculateAngleBetweenVectors(v1: Point, v2: Point): Double {
            val dotProduct = calculateDotProduct(v1, v2)
            val magnitudeV1 = calculateMagnitude(v1)
            val magnitudeV2 = calculateMagnitude(v2)

            val cosTheta = dotProduct / (magnitudeV1 * magnitudeV2)

    //        // Ensure that the value passed to acos is within the valid range [-1, 1]
            val safeCosTheta = if (cosTheta < -1.0) -1.0 else if (cosTheta > 1.0) 1.0 else cosTheta
    //
    //        // Calculate the angle in radians
            val angleRad = acos(safeCosTheta.toDouble())
    //
    //        // Convert radians to degrees
            val angleDegrees = Math.toDegrees(angleRad)

            return angleDegrees
        }

        var distance0 = euclideanDistance(Point(firstHandLandmarks[3].x(), firstHandLandmarks[3].y()), Point(firstHandLandmarks[5].x(), firstHandLandmarks[5].y())) * 100
        var distance1 = euclideanDistance(Point(firstHandLandmarks[8].x(), firstHandLandmarks[8].y()), Point(firstHandLandmarks[7].x(), firstHandLandmarks[7].y())) * 100
        var distance2 = euclideanDistance(Point(firstHandLandmarks[12].x(), firstHandLandmarks[12].y()), Point(firstHandLandmarks[11].x(), firstHandLandmarks[11].y())) * 100
        var distance3 = euclideanDistance(Point(firstHandLandmarks[16].x(), firstHandLandmarks[16].y()), Point(firstHandLandmarks[15].x(), firstHandLandmarks[15].y())) * 100
        var distance4 = euclideanDistance(Point(firstHandLandmarks[20].x(), firstHandLandmarks[20].y()), Point(firstHandLandmarks[19].x(), firstHandLandmarks[19].y())) * 100

    //    val point4 = Point(firstHandLandmarks[4].x(), firstHandLandmarks[4].y())
    //    val point3 = Point(firstHandLandmarks[3].x(), firstHandLandmarks[3].y())
    //    val point2 = Point(firstHandLandmarks[2].x(), firstHandLandmarks[2].y())
    //
    //    val vector1 = calculateVector(point3, point4)
    //    val vector2 = calculateVector(point3, point2)
    //
    //    val angle = calculateAngleBetweenVectors(vector1, vector2)
        // Log.d("준엽", "0번째 손가락이 굽은 각도는 $angle 입니다.")

        // Log.d("준엽", "0번째 손가락의 길이는 ${firstHandLandmarks[4].z()}입니다.")

        // if(distance0 < 7.0)
        // {
        //     // Log.d("준엽", "0번째 손가락이 굽었습니다. $distance0" )
        // }
         if(distance1 < 3.0)
         {
             Log.d("준엽", "1번째 손가락이 굽었습니다. $distance1" )
         }
         if(distance2 < 3.0)
         {
             Log.d("준엽", "2번째 손가락이 굽었습니다. $distance2" )
         }
         if(distance3 < 3.25)
         {
             Log.d("준엽", "3번째 손가락이 굽었습니다. $distance3" )
         }
         if(distance4 < 3.0)
         {
             Log.d("준엽", "4번째 손가락이 굽었습니다. $distance4" )
         }
    }




    private fun LeftCalibration(secondHandLandmarks: MutableList<NormalizedLandmark>?) {
//        val calibratedValues = calibrate(secondHandLandmarks)

//        if (calibratedValues != null) {
//            // Use calibrated values for further processing or updating parameters
//            Log.d("민규", "Calibrated Left Hand: $calibratedValues")
//        } else {
//            Log.d("민규", "Left Hand Landmarks are null during calibration.")
//        }
    }

    // Return errors thrown during detection to this HandLandmarkerHelper's
    // caller
    private fun returnLivestreamError(error: RuntimeException) {
//        Log.d("민규", error.toString())
        handLandmarkerHelperListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    companion object {
        const val TAG = "HandLandmarkerHelper"
        private const val MP_HAND_LANDMARKER_TASK = "hand_landmarker.task"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_HAND_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_HANDS = 2
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
    }

    data class ResultBundle(
        val results: List<HandLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
