package com.chareem.camerax.basic.utils

import android.content.Context
import android.content.pm.PackageManager

/**
 * Main entry point into our app. This app follows the single-activity pattern, and all
 * functionality is implemented in the form of fragments.
 */
object CameraHelper{
    val RESULT_CODE = 56789
    val RESULT_PATH = "result_path"
    val FACING_FRONT = 1
    val FACING_BACK = 0

    fun hasCamera(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) ||
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
    }

    object Arguments {
        val SHOW_LOCATION = "com.chareem.chareemCamera.show_location"
        val MOCK_LOCATION = "com.chareem.chareemCamera.mock_location"
        val DEVAULT_LAT = "com.chareem.chareemCamera.devault_lat"
        val DEVAULT_LON = "com.chareem.chareemCamera.devault_lon"
        val CAMERA_FACING_TYPE = "com.chareem.chareemCamera.camera_facing_type"
        val FACE_DETECTION = "com.chareem.chareemCamera.face_detection"
        val FORCE_LANDSCAPE = "com.chareem.chareemCamera.force_landscape"
        val IMAGE_NAME = "com.chareem.chareemCamera.image_name"
        val DIR_NAME = "com.chareem.chareemCamera.dir_name"
    }
}
