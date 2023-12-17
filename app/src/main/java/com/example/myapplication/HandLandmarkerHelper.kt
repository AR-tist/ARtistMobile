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
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.serialization.Serializable

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
    val handLandmarkerHelperListener: LandmarkerListener? = null,
    val serverManager : WebSocketServerManager? = null

    // this listener is only used when running in RunningMode.LIVE_STREAM
) {

    // For this example this needs to be a var so it can be reset on changes.
    // If the Hand Landmarker will not change, a lazy val would be preferable.
    private var handLandmarker: HandLandmarker? = null

    // 웹 소켓 서버를 생성하고 시작합니다.
//    val serverManager = WebSocketServerManager("0.0.0.0", 4439)
    init {
        Log.d("준엽", serverManager.toString());
//        serverManager?.broadcast("연결성공")
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
        val secondHandLandmarks = landmarksList.getOrNull(1)

        var worldLandmarkList = resultBundle.results.first().worldLandmarks()

        val firstWorldHandLandmarks = worldLandmarkList.getOrNull(0)
        val secondWorldHandLandmarks = worldLandmarkList.getOrNull(1)

        if(handType1 == "Left hand"){
            LeftCalibration(firstHandLandmarks, firstWorldHandLandmarks)
            RightCalibration(secondHandLandmarks, secondWorldHandLandmarks)
        }else{
            RightCalibration(firstHandLandmarks, firstWorldHandLandmarks)
            LeftCalibration(secondHandLandmarks, secondWorldHandLandmarks)
        }


    }


    data class Point(val x: Float, val y: Float)
    data class Point2(var x: Float?, var y:Float?)

    fun euclideanDistance(point1: Point, point2: Point): Double {
        var sum = 0.0

        sum += (point1.x - point2.x).pow(2)
        sum += (point1.y - point2.y).pow(2)

        return sqrt(sum)
    }
    // 모든 점을 담을 수 있는 데이터 클래스만들어야함.


    @Serializable// hand Left : 0, Right : 1
    data class Coordinates(var hand : Int ,val points: List<PointForBrodcast>)

    var rcheck0 = false;
    var rcheck1 = false;
    var rcheck2 = false;
    var rcheck3 = false;
    var rcheck4 = false;

    @Serializable
    data class PointForBrodcast(val marknum: Int, val x: Float, val y: Float)

    private fun RightCalibration(
        Landmarks: MutableList<NormalizedLandmark>?, // 손가락위치
        worldLandmarks: MutableList<Landmark>?
    ) {
//    val calibratedValues = calibrate(firstHandLandmarks)

        // Ensure firstHandLandmarks is not null and contains at least 21 landmarks
        if (worldLandmarks == null || worldLandmarks.size < 21) {
//            Log.d("준엽", "Invalid number of landmarks")
            return
        }
        if( Landmarks == null || Landmarks.size <21){
            return
        }
//
        var distance0 = euclideanDistance(Point(worldLandmarks[4].x(), worldLandmarks[4].y()), Point(worldLandmarks[9].x(), worldLandmarks[9].y())) * 100
        var distance1 = euclideanDistance(Point(worldLandmarks[8].x(), worldLandmarks[8].y()), Point(worldLandmarks[5].x(), worldLandmarks[5].y())) * 100
        var distance2 = euclideanDistance(Point(worldLandmarks[12].x(), worldLandmarks[12].y()), Point(worldLandmarks[9].x(), worldLandmarks[9].y())) * 100
        var distance3 = euclideanDistance(Point(worldLandmarks[16].x(), worldLandmarks[16].y()), Point(worldLandmarks[13].x(), worldLandmarks[13].y())) * 100
        var distance4 = euclideanDistance(Point(worldLandmarks[20].x(), worldLandmarks[20].y()), Point(worldLandmarks[17].x(), worldLandmarks[17].y())) * 100


        Log.d("지해", "$distance4")


        if(distance0 < 3.2){
            if(!rcheck0){
                Log.d("준엽", "오른손 엄지가 굽었습니다. $distance0")
                serverManager?.broadcast("1! 1? 0? 0")
            }
            rcheck0 = true;
        }else{
            if(rcheck0){
                serverManager?.broadcast("1! 1? 0? 1")
                Log.d("준엽", "오른손 엄지가 펴졌습니다. $distance0")
            }
            rcheck0 = false;
        }

         if(distance1 < 4.8)
         {
            if(!rcheck1) {
                 Log.d("준엽", "오른손 1번째 손가락이 굽었습니다. $distance1")
                 serverManager?.broadcast("1! 1? 1? 0")
             }
            rcheck1 = true;
         }else{
             if(rcheck1) {
                 serverManager?.broadcast("1! 1? 1? 1")
                 Log.d("준엽", "오른손 1번째 손가락이 펴졌습니다. $distance1")
             }
             rcheck1 = false;
         }
         if(distance2 < 5.5)
         {
            if(!rcheck2) {
                Log.d("준엽", "오른손 2번째 손가락이 굽었습니다. $distance2")
                serverManager?.broadcast("1! 1? 2? 0")
            }
            rcheck2 = true;
         }else{
            if(rcheck2) {
                serverManager?.broadcast("1! 1? 2? 1")
                Log.d("준엽", "오른손 2번째 손가락이 펴졌습니다. $distance2")
            }
            rcheck2 = false;
         }

        if(distance3 < 5.0)
        {
            if(!rcheck3) {
                Log.d("준엽", "오른손 3번째 손가락이 굽었습니다. $distance3")
                serverManager?.broadcast("1! 1? 3? 0")
            }
            rcheck3 = true;
        }else{
            if(rcheck3) {
                serverManager?.broadcast("1! 1? 3? 1")
                Log.d("준엽", "오른손 3번째 손가락이 펴졌습니다. $distance3")
            }
            rcheck3 = false;
        }

        if(distance4 < 3.7)
        {
            if(!rcheck4) {
                Log.d("준엽", "오른손 4번째 손가락이 굽었습니다. $distance4")
                serverManager?.broadcast("1! 1? 4? 0")
            }
            rcheck4 = true;
        }else{
            if(rcheck4) {
                serverManager?.broadcast("1! 1? 4? 1")
                Log.d("준엽", "오른손 4번째 손가락이 펴졌습니다. $distance4")
            }
            rcheck4 = false;
        }

        var X4 = Landmarks[4].x()
        var Y4 = Landmarks[4].y()

        var X8 = Landmarks[8].x()
        var Y8 = Landmarks[8].y()

        var X12 = Landmarks[12].x()
        var Y12 = Landmarks[12].y()

        var X16 = Landmarks[16].x()
        var Y16 = Landmarks[16].y()

        var X20 = Landmarks[20].x()
        var Y20 = Landmarks[20].y()

        // list에 담아서 보내야함.
        var X = listOf(X4, X8, X12, X16, X20)
        var Y = listOf(Y4, Y8, Y12, Y16, Y20)

//        var pointList = mutableListOf<PointForBrodcast>()
//
//        for (i in 0..4) {
//            pointList.add(PointForBrodcast(i, X[i], Y[i])  )
//        }
//
//        var coordinates = "1? $pointList"
//
//        serverManager?.broadcast(coordinates)
//
//
//         Log.d("민규", "$coordinates")

        var test = "0! 1? $X ? $Y"
        serverManager?.broadcast(test)

        Log.d("민규",test)
    }

    var lcheck0 = false;
    var lcheck1 = false;
    var lcheck2 = false;
    var lcheck3 = false;
    var lcheck4 = false;


    private fun LeftCalibration(
        Landmarks: MutableList<NormalizedLandmark>?, // 손가락위치
        worldLandmarks: MutableList<Landmark>?
    ) {

       if (worldLandmarks == null || worldLandmarks.size < 21) {
//            Log.d("준엽", "Invalid number of landmarks")
            return
        }
        if( Landmarks == null || Landmarks.size <21){
            return
        }

        var distance0 = euclideanDistance(Point(worldLandmarks[4].x(), worldLandmarks[4].y()), Point(worldLandmarks[9].x(), worldLandmarks[9].y())) * 100
        var distance1 = euclideanDistance(Point(worldLandmarks[8].x(), worldLandmarks[8].y()), Point(worldLandmarks[7].x(), worldLandmarks[7].y())) * 100
        var distance2 = euclideanDistance(Point(worldLandmarks[12].x(), worldLandmarks[12].y()), Point(worldLandmarks[11].x(), worldLandmarks[11].y())) * 100
        var distance3 = euclideanDistance(Point(worldLandmarks[16].x(), worldLandmarks[16].y()), Point(worldLandmarks[15].x(), worldLandmarks[15].y())) * 100
        var distance4 = euclideanDistance(Point(worldLandmarks[20].x(), worldLandmarks[20].y()), Point(worldLandmarks[19].x(), worldLandmarks[19].y())) * 100

//        Log.d("지해", "$distance1")

        if(distance0 < 3.2){
            if(!lcheck0) {
                Log.d("준엽", "왼손 엄지가 굽었습니다. $distance0")
                serverManager?.broadcast("1! 0? 0? 0")
            }
            lcheck0 = true;
        }else{
            if(lcheck0) {
                serverManager?.broadcast("1! 0? 0? 1")
                Log.d("준엽", "왼손 엄지가 펴졌습니다. $distance0")
            }
            lcheck0 = false;
        }

        if(distance1 < 1.2){
            if(!lcheck1) {
                Log.d("준엽", "왼손 1번째 손가락이 굽었습니다. $distance1")
                serverManager?.broadcast("1! 0? 1? 0")
            }
            lcheck1 = true;
        }else{
            if(lcheck1) {
                serverManager?.broadcast("1! 0? 1? 1")
                Log.d("준엽", "왼손 1번째 손가락이 펴졌습니다. $distance1")
            }
            lcheck1 = false;
        }

        if(distance2 < 2.0){
            if(!lcheck2) {
                Log.d("준엽", "왼손 2번째 손가락이 굽었습니다. $distance2")
                serverManager?.broadcast("1! 0? 2? 0")
            }
            lcheck2 = true;
        }else{
            if(lcheck2) {
                serverManager?.broadcast("1! 0? 2? 1")
                Log.d("준엽", "왼손 2번째 손가락이 펴졌습니다. $distance2")
            }
            lcheck2 = false;
        }

        if(distance3 < 1.5){
            if(!lcheck3) {
                Log.d("준엽", "왼손 3번째 손가락이 굽었습니다. $distance3")
                serverManager?.broadcast("1! 0? 3? 0")
            }
            lcheck3 = true;
        }else{
            if(lcheck3) {
                serverManager?.broadcast("1! 0? 3? 1")
                Log.d("준엽", "왼손 3번째 손가락이 펴졌습니다. $distance3")
            }
            lcheck3 = false;
        }

        if(distance4 < 1.2){
            if(!lcheck4) {
                Log.d("준엽", "왼손 4번째 손가락이 굽었습니다. $distance4")
                serverManager?.broadcast("1! 0? 4? 0")
            }
            lcheck4 = true;
        }else{
            if(lcheck4) {
                serverManager?.broadcast("1! 0? 4? 1")
                Log.d("준엽", "왼손 4번째 손가락이 펴졌습니다. $distance4")
            }
            lcheck4 = false;
        }
        
        var X4 = Landmarks[4].x()
        var Y4 = Landmarks[4].y()

        var X8 = Landmarks[8].x()
        var Y8 = Landmarks[8].y()

        var X12 = Landmarks[12].x()
        var Y12 = Landmarks[12].y()

        var X16 = Landmarks[16].x()
        var Y16 = Landmarks[16].y()

        var X20 = Landmarks[20].x()
        var Y20 = Landmarks[20].y()

        // list에 담아서 보내야함.
        var X = listOf(X4, X8, X12, X16, X20)
        var Y = listOf(Y4, Y8, Y12, Y16, Y20)

//        var pointList = mutableListOf<PointForBrodcast>()
//
//        for (i in 0..4) {
//            pointList.add(PointForBrodcast(i, X[i], Y[i])  )
//        }
//
//        var coordinates = "1? $pointList"
//
//        serverManager?.broadcast(coordinates)
//
//
//         Log.d("민규", "$coordinates")

        var test1 = "0! 0? $X ? $Y"
        serverManager?.broadcast(test1)

        Log.d("민규",test1)

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
