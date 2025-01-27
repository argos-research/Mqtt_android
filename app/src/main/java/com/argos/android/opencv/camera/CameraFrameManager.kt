package com.argos.android.opencv.camera

import android.util.Log
import com.argos.android.opencv.activity.CameraActivity
import com.argos.android.opencv.driving.DnnHelper
import com.argos.android.opencv.lanekeeping.LaneKeeper
import com.argos.android.opencv.lineDetection.LaneFinder
import com.argos.android.opencv.model.Feature
import com.argos.android.opencv.mqtt.MqttClientInstance
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class CameraFrameManager(private val mCaller: CameraFrameMangerCaller, private val mFeature: String, private val mDnnHelper: DnnHelper, private val mqttClientInstance: MqttClientInstance) : Thread() {
    private var mLaneFinder = LaneFinder()
    private var mLaneKeeper = LaneKeeper(mCaller)
    private lateinit var mFrameInfo: Mat
    private lateinit var mDebugImage: Mat
    private var mDistance: Double = 0.0
    private var mRunning: Boolean = true
    var mQTTClient: MqttClientInstance = mqttClientInstance

    private val mGreyWidth = (CameraActivity.SCREEN_WIDTH - CameraActivity.SCREEN_HEIGHT)/2

    fun finish() {
        mRunning = false
    }

    override fun run() {
        while (mRunning) {
            try {
                when (mFeature) {
                    Feature.LANE_DETECTION -> laneDetection(mCaller.getCopyOfCurrentFrame())
                    Feature.OVERTAKING -> overTaking(mCaller.getCopyOfCurrentFrame())
                    Feature.LANE_KEEPING -> laneKeeping(mCaller.getCopyOfCurrentFrame())
                }
            } catch (e: NoCurrentFrameAvailableException) {
                sleep(50)
            }
        }
    }

    private fun laneDetection(frame: Mat) {
        val (frameInfo, binaryImage) = processForLaneDetection(frame)
        setFrameInfo(frameInfo.clone())

        Imgproc.resize(binaryImage, binaryImage, Size(CameraActivity.SCREEN_HEIGHT.toDouble(), CameraActivity.SCREEN_HEIGHT.toDouble()))
        binaryImage.let { setDebugImage(binaryImage.clone()) }
    }

    private fun processForLaneDetection(frame: Mat) : Pair<Mat, Mat> {
        Imgproc.resize(frame, frame, Size(LaneFinder.WIDTH_IMAGE.toDouble(), LaneFinder.HEIGHT_IMAGE.toDouble()))
        val (frameInfo, binaryImage) = mLaneFinder.getLanesAndBinaryImage(frame)

        Imgproc.resize(frameInfo, frameInfo, Size(CameraActivity.SCREEN_WIDTH.toDouble(), CameraActivity.SCREEN_HEIGHT.toDouble()))
        Imgproc.cvtColor(frameInfo, frameInfo, Imgproc.COLOR_RGB2BGR)

        return Pair(frameInfo, binaryImage)

    }
    private fun overTaking(frame: Mat) {
        Log.d("THREAD",frame.toString())
        try {

            val dnnResponse = mDnnHelper.processMat(frame)
            val frameInfo = processVehicleDetection(frame)
            val grayImage = Mat(CameraActivity.SCREEN_HEIGHT, mGreyWidth, CvType.CV_8UC3, Scalar(255.0, 0.0, 0.0))

            grayImage.copyTo(frameInfo.submat(Rect(0, 0, mGreyWidth, CameraActivity.SCREEN_HEIGHT)))
            grayImage.copyTo(frameInfo.submat(Rect(CameraActivity.SCREEN_WIDTH - mGreyWidth, 0, mGreyWidth, CameraActivity.SCREEN_HEIGHT)))


            Imgproc.cvtColor(frameInfo, frameInfo, Imgproc.COLOR_RGB2BGR)

            if (frameInfo != null) {
                setFrameInfo(frameInfo.clone())
            }
            mCaller.setDistance(mDistance)

        }catch (e:CvException){

        }
    }

    private fun processVehicleDetection(frame: Mat) : Mat {

        val dnnResponse = mDnnHelper.processMat(frame)
        mDistance = dnnResponse.distance
        val frameInfo = dnnResponse.mat

        return frameInfo
    }

    private fun laneKeeping(frame: Mat) {
        val newImg = mLaneKeeper.initImageProcessing(frame)
        setFrameInfo(newImg.clone())
    }

    @Synchronized
    private fun setFrameInfo(image: Mat) {
        mFrameInfo = image
    }

    @Synchronized
    fun getFrameInfo(): Mat {
        try {
            return mFrameInfo
        } catch (e: UninitializedPropertyAccessException) {
            throw NoCameraFrameInfoAvailableException("Frame info not initialized")
        }
    }

    @Synchronized
    private fun setDebugImage(image: Mat) {
        mDebugImage = image
    }

    @Synchronized
    fun getDebugImage(): Mat {
        try {
            return mDebugImage
        } catch (e: UninitializedPropertyAccessException) {
            throw NoDebugImageAvailableException("Debug image not initialized")
        }
    }
}
