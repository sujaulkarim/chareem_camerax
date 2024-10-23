package com.chareem.camerax.basic

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chareem.camerax.basic.databinding.ActivityCameraxBinding
import com.chareem.camerax.basic.utils.CameraHelper
import java.io.File

const val KEY_EVENT_ACTION = "key_event_action"
const val KEY_EVENT_EXTRA = "key_event_extra"
private const val IMMERSIVE_FLAG_TIMEOUT = 500L

/**
 * Main entry point into our app. This app follows the single-activity pattern, and all
 * functionality is implemented in the form of fragments.
 */
class CameraXActivity : AppCompatActivity() {

    private lateinit var activityMainBinding: ActivityCameraxBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.extras?.let {
            setIntentData(it)
        }
        activityMainBinding = ActivityCameraxBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)
    }

    private fun setIntentData(bundle: Bundle){
        if (bundle.containsKey(CameraHelper.Arguments.CAMERA_FACING_TYPE))
            cameraType = bundle.getInt(CameraHelper.Arguments.CAMERA_FACING_TYPE, CameraHelper.FACING_BACK)
        if (bundle.containsKey(CameraHelper.Arguments.FACE_DETECTION))
            isUseFaceDetection = bundle.getBoolean(CameraHelper.Arguments.FACE_DETECTION, false)
        if (bundle.containsKey(CameraHelper.Arguments.DEVAULT_LAT))
            latitude = bundle.getString(CameraHelper.Arguments.DEVAULT_LAT, "")
        if (bundle.containsKey(CameraHelper.Arguments.DEVAULT_LON))
            longitude = bundle.getString(CameraHelper.Arguments.DEVAULT_LON, "")
        if (bundle.containsKey(CameraHelper.Arguments.MOCK_LOCATION))
            isUseMockDetection = bundle.getBoolean(CameraHelper.Arguments.MOCK_LOCATION, false)
        if (bundle.containsKey(CameraHelper.Arguments.SHOW_LOCATION))
            isUseTimestamp = bundle.getBoolean(CameraHelper.Arguments.SHOW_LOCATION, false)
        if (bundle.containsKey(CameraHelper.Arguments.FORCE_LANDSCAPE))
            cameraForceLandscape = bundle.getBoolean(CameraHelper.Arguments.FORCE_LANDSCAPE, false)
        if (bundle.containsKey(CameraHelper.Arguments.IMAGE_NAME))
            imageName = bundle.getString(CameraHelper.Arguments.IMAGE_NAME, "")
        if (bundle.containsKey(CameraHelper.Arguments.DIR_NAME))
            dirName = bundle.getString(CameraHelper.Arguments.DIR_NAME, "")
    }

    override fun onStart() {
        super.onStart()
        // Before setting full screen flags, we must wait a bit to let UI settle; otherwise, we may
        // be trying to set app to immersive mode before it's ready and the flags do not stick
        activityMainBinding.fragmentContainer.postDelayed({
            hideSystemUI()
        }, IMMERSIVE_FLAG_TIMEOUT)
    }

    /** When key down event is triggered, relay it via local broadcast so fragments can handle it */
    /*override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        *//*return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val intent = Intent(KEY_EVENT_ACTION).apply { putExtra(KEY_EVENT_EXTRA, keyCode) }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }*//*
        return true
    }*/

    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Workaround for Android Q memory leak issue in IRequestFinishCallback$Stub.
            // (https://issuetracker.google.com/issues/139738913)
            finishAfterTransition()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private var cameraType = CameraHelper.FACING_BACK
        private var latitude = ""
        private var longitude = ""
        private var isUseFaceDetection = false
        private var isUseMockDetection = false
        private var isUseTimestamp = false
        private var cameraForceLandscape = false
        private var imageName = ""
        private var dirName = ""

        /** Use external media if it is available, our app's file directory otherwise */
        fun getOutputDirectory(context: Context): File {
            val appContext = context.applicationContext
            val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
                File(it, dirName.ifEmpty { appContext.resources.getString(R.string.app_name) }).apply {
                    if (!exists()) mkdirs() } }
            return if (mediaDir != null && mediaDir.exists())
                mediaDir else appContext.filesDir
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, activityMainBinding.fragmentContainer).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    fun getImageName(): String{
        return imageName
    }

    fun getCameraType(): Int{
        return cameraType
    }

    fun getLatitude(): String{
        return latitude
    }

    fun getLongitude(): String{
        return longitude
    }

    fun isUseFaceDetection(): Boolean{
        return isUseFaceDetection
    }

    fun isCameraForceLandscape(): Boolean{
        return cameraForceLandscape
    }

    fun isUseMockDetection(): Boolean{
        return isUseMockDetection
    }

    fun isUseTimeStamp(): Boolean{
        return isUseTimestamp
    }
}
