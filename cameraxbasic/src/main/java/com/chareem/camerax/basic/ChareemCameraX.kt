package com.chareem.camerax.basic

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import com.chareem.camerax.basic.utils.CameraHelper
import com.permissionx.guolindev.PermissionX
import com.permissionx.guolindev.callback.ForwardToSettingsCallback
import com.permissionx.guolindev.callback.RequestCallback
import com.permissionx.guolindev.request.ForwardScope

/**
 * Main entry point into our app. This app follows the single-activity pattern, and all
 * functionality is implemented in the form of fragments.
 */
class ChareemCameraX{
    private var cameraType = CameraHelper.FACING_BACK
    private var latitude = ""
    private var longitude = ""
    private var isUseFaceDetection = false
    private var isUseMockDetection = false
    private var isUseTimestamp = false
    private var isCameraForceLandscape = false
    private var fileName = ""
    private var dirName = ""
    private var resultCode = CameraHelper.RESULT_CODE
    private var mCcontext: Activity? = null
    private var mResultLauncher: ActivityResultLauncher<Intent>? = null
    private lateinit var mInstance: ChareemCameraX
    companion object {
        fun with(context: Activity) : ChareemCameraX{
            val instance = ChareemCameraX()
            instance.mInstance = instance
            instance.mCcontext = context
            return instance
        }
    }

    fun setCameraFacing(cameraTypes: Int): ChareemCameraX {
        cameraType = cameraTypes
        return mInstance
    }

    fun setDevaultPosition(latitudes: String, longitudes: String): ChareemCameraX {
        latitude = latitudes
        longitude = longitudes
        return mInstance
    }

    fun setFaceDetection(isUseFaceDetections: Boolean): ChareemCameraX {
        isUseFaceDetection = isUseFaceDetections
        return mInstance
    }

    fun setDirectoryName(dirNames: String): ChareemCameraX {
        dirName = dirNames
        return mInstance
    }

    fun setMockDetection(isUseMockDetections: Boolean): ChareemCameraX {
        isUseMockDetection = isUseMockDetections
        return mInstance
    }

    fun setTimeStamp(isUseTimestamps: Boolean): ChareemCameraX {
        isUseTimestamp = isUseTimestamps
        return mInstance
    }

    fun setCameraForceLandscape(isCameraForceLandscapes: Boolean): ChareemCameraX {
        isCameraForceLandscape = isCameraForceLandscapes
        return mInstance
    }

    fun setImageName(imageName: String): ChareemCameraX {
        fileName = imageName
        return mInstance
    }

    fun setResultCode(resultCodes: Int): ChareemCameraX {
        resultCode = resultCodes
        return mInstance
    }

    fun setResultLauncher(mResultLaunchers: ActivityResultLauncher<Intent>): ChareemCameraX {
        mResultLauncher = mResultLaunchers
        return mInstance
    }

    fun launchCamera() {
        mCcontext?.let { context ->
            if (CameraHelper.hasCamera(context)) {
                val permissions = ArrayList<String>()
                permissions.add(Manifest.permission.CAMERA)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R){
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
                    permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                }
                if (isUseTimestamp) {
                    permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                PermissionX.init(context as FragmentActivity)
                    .permissions(permissions)
                    .onForwardToSettings(object : ForwardToSettingsCallback {
                        override fun onForwardToSettings(scope: ForwardScope, deniedList: List<String>) {
                            scope.showForwardToSettingsDialog(
                                deniedList,
                                "You need to allow oermission in Settings manually",
                                "OK",
                                "Cancel"
                            )
                        }
                    }).request(object : RequestCallback {
                        override fun onResult(
                            allGranted: Boolean,
                            grantedList: List<String?>,
                            deniedList: List<String?>
                        ) {
                            if (allGranted) {
                                val cameraIntent = Intent(context, CameraXActivity::class.java)
                                cameraIntent.putExtra(CameraHelper.Arguments.SHOW_LOCATION, isUseTimestamp)
                                cameraIntent.putExtra(CameraHelper.Arguments.MOCK_LOCATION, isUseMockDetection)
                                cameraIntent.putExtra(CameraHelper.Arguments.DEVAULT_LAT, latitude)
                                cameraIntent.putExtra(CameraHelper.Arguments.DEVAULT_LON, longitude)
                                cameraIntent.putExtra(CameraHelper.Arguments.CAMERA_FACING_TYPE, cameraType)
                                cameraIntent.putExtra(CameraHelper.Arguments.FACE_DETECTION, isUseFaceDetection)
                                cameraIntent.putExtra(CameraHelper.Arguments.FORCE_LANDSCAPE, isCameraForceLandscape)
                                cameraIntent.putExtra(CameraHelper.Arguments.IMAGE_NAME, fileName)
                                cameraIntent.putExtra(CameraHelper.Arguments.DIR_NAME, dirName)
                                mResultLauncher?.launch(cameraIntent) ?: context.startActivityForResult(cameraIntent, resultCode)
                            } else {
                                Toast.makeText(
                                    context,
                                    "You need to grant all permissions ",
                                    Toast.LENGTH_LONG
                                ).show()
                                context.finish()
                            }
                        }
                    })
            } else Toast.makeText(
                context,
                "Error camera occurred, you have no camera",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun getCameraIntent(): Intent? {
        mCcontext?.let { context ->
            if (CameraHelper.hasCamera(context)) {
                val permissions = java.util.ArrayList<String>()
                permissions.add(Manifest.permission.CAMERA)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R){
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
                    permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                }
                if (isUseTimestamp) {
                    permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                for (permision in permissions) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            permision
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Toast.makeText(
                            context,
                            "You need to granted permission $permision first",
                            Toast.LENGTH_SHORT
                        ).show()
                        return null
                    }
                }
                val cameraIntent = Intent(context, CameraXActivity::class.java)
                cameraIntent.putExtra(CameraHelper.Arguments.SHOW_LOCATION, isUseTimestamp)
                cameraIntent.putExtra(CameraHelper.Arguments.MOCK_LOCATION, isUseMockDetection)
                cameraIntent.putExtra(CameraHelper.Arguments.DEVAULT_LAT, latitude)
                cameraIntent.putExtra(CameraHelper.Arguments.DEVAULT_LON, longitude)
                cameraIntent.putExtra(CameraHelper.Arguments.CAMERA_FACING_TYPE, cameraType)
                cameraIntent.putExtra(CameraHelper.Arguments.FACE_DETECTION, isUseFaceDetection)
                cameraIntent.putExtra(CameraHelper.Arguments.FORCE_LANDSCAPE, isCameraForceLandscape)
                cameraIntent.putExtra(CameraHelper.Arguments.IMAGE_NAME, fileName)
                cameraIntent.putExtra(CameraHelper.Arguments.DIR_NAME, dirName)
                return cameraIntent
            }
            Toast.makeText(context, "Error camera occurred, you have no camera", Toast.LENGTH_SHORT)
                .show()
            return null
        } ?: return null
    }
}
