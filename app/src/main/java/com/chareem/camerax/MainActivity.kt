package com.chareem.camerax

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.chareem.camerax.basic.ChareemCameraX
import com.chareem.camerax.basic.utils.CameraHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.ByteArrayOutputStream
import java.io.File

class MainActivity : AppCompatActivity() {
    private val resCode = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val btnFto = findViewById<FloatingActionButton>(R.id.foto_btn)
        btnFto.setOnClickListener {
            ChareemCameraX.with(this)
                .setCameraFacing(CameraHelper.FACING_FRONT)
                .setFaceDetection(true)
                .setImageName("imageku")
                .setMockDetection(true)
                .setCameraForceLandscape(false)
                .setTimeStamp(true)
                .setResultCode(resCode)
                .setDirectoryName("apa/picture")
                .launchCamera()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if ((requestCode == resCode) && resultCode == Activity.RESULT_OK){
            val filePath:String = data?.getStringExtra(CameraHelper.RESULT_PATH) ?: ""
            val img = findViewById<ImageView>(R.id.img)
            val file:File? = File(filePath)
            file?.let {
                val fotoStr = image2String(this, it)
                val options: RequestOptions = RequestOptions()
                    .placeholder(com.chareem.camerax.basic.R.drawable.circle_frame_background_dark)
                    .error(com.chareem.camerax.basic.R.drawable.circle_frame_background_dark)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .priority(Priority.HIGH)
                Glide.with(this)
                    .load(filePath)
                    .apply(options)
                    .into(img)
            } ?: kotlin.run {
                Toast.makeText(this, "No image chaptured", Toast.LENGTH_SHORT).show()
            }

        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun image2String(context: Context, img : File) : String {
        try{
            val bitmap = resize(MediaStore.Images.Media.getBitmap(context.contentResolver, Uri.fromFile(img)), 780, 780)
            val outputStream = ByteArrayOutputStream()
            bitmap?.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            val imgbyte = outputStream.toByteArray()
            val base64 = Base64.encodeToString(imgbyte, Base64.NO_WRAP)

            return base64
        } catch (e : Exception) {
            e.printStackTrace()
        }

        return ""
    }

    private fun resize(image: Bitmap?, maxWidth: Int, maxHeight: Int): Bitmap? {
        try {
            var image = image
            image?.let {
                if (maxHeight > 0 && maxWidth > 0) {
                    val width = it.width
                    val height = it.height
                    val ratioBitmap = width.toFloat() / height.toFloat()
                    val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

                    var finalWidth = maxWidth
                    var finalHeight = maxHeight
                    if (ratioMax > ratioBitmap) {
                        finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
                    } else {
                        finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
                    }
                    image = Bitmap.createScaledBitmap(it, finalWidth, finalHeight, true)
                    return image
                } else {
                    return image
                }
            }
        } catch (e : Exception) {
            e.printStackTrace()
        }

        return null
    }
}