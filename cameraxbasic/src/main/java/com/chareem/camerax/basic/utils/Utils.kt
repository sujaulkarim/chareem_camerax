/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chareem.camerax.basic.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.camera.core.impl.utils.Exif
import androidx.core.content.res.ResourcesCompat
import androidx.exifinterface.media.ExifInterface
import com.chareem.camerax.basic.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main entry point into our app. This app follows the single-activity pattern, and all
 * functionality is implemented in the form of fragments.
 */
object Utils{
    fun isMockLocationOn(
        location: Location
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            location.isFromMockProvider
        }
    }

    fun getAddress(context: Context, lat: Double, lng: Double, listener: (Address?) -> Unit) {
        try {
            val locale = Locale.getDefault();

            val geocoder = Geocoder(context, locale);

            val obj = geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
            listener.invoke(obj)

            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lat, lng, 1) {
                    val obj = it.firstOrNull()
                    listener.invoke(obj)
                }
            } else {
                val obj = geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
                listener.invoke(obj)
            }*/
        } catch (e : Exception) {
            e.printStackTrace()
            listener.invoke(null)
        }
    }

    fun getCurrentTimeStr(formatStr: String?): String? {
        val format =
            SimpleDateFormat(formatStr, Locale.US)
        val cal = Calendar.getInstance()
        return format.format(cal.time)
    }

    fun drawMultilineTextToBitmap(
        gContext: Context,
        gResId: Bitmap,
        gText: String?,
        textSize: Int?
    ): Bitmap {
        var textSize = textSize
        if (textSize == null) textSize = 12

        // prepare canvas
        val resources = gContext.resources
        val scale = resources.displayMetrics.density
        var bitmap = gResId
        var bitmapConfig = bitmap.config
        // set default bitmap config if none
        if (bitmapConfig == null) {
            bitmapConfig = Bitmap.Config.ARGB_8888
        }
        // resource bitmaps are imutable,
        // so we need to convert it to mutable one
        bitmap = bitmap.copy(bitmapConfig, true)
        val canvas = Canvas(bitmap)

        // new antialiased Paint
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        // text color - #3D3D3D
        paint.color = Color.WHITE
        // text size in pixels
        paint.setTextSize(textSize * scale)
        // text shadow
        //paint.setShadowLayer(1f, 0f, 1f, Color.WHITE);
        val customTypeface = ResourcesCompat.getFont(gContext, R.font.opensans_semibold)
        paint.typeface = customTypeface


        // set text width to canvas width minus 16dp padding
        val textWidth = canvas.width - (16 * scale).toInt()

        // init StaticLayout for text
        val textLayout = StaticLayout(
            gText, paint, textWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 2.0f, false
        )

        // get height of multiline text
        val textHeight = textLayout.height

        // get position of text's top left corner
        val x = (bitmap.width - textWidth) / 2
        val y = (bitmap.height - textHeight) * 98 / 100

        // draw text to the Canvas center
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        textLayout.draw(canvas)
        canvas.restore()
        return bitmap
    }

    fun saveBitmap(path: String, bitmap: Bitmap, listener : (Boolean, String) -> Unit) {
        val file = File(path)
        try {
            val iPath: File = Environment.getDataDirectory()
            val iStat = StatFs(iPath.path)
            val iBlockSize = iStat.blockSizeLong
            val iAvailableBlocks = iStat.availableBlocksLong
            val iTotalBlocks = iStat.blockCountLong
            val iAvailableSpace = formatSizeMb(iAvailableBlocks * iBlockSize)
            val iTotalSpace = formatSize(iTotalBlocks * iBlockSize)
            if (iAvailableSpace > 50){
                if (file.exists()) {
                    file.delete()
                }
                val out = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.flush()
                out.close()
            } else {
                listener(false, "Memory less than 50MB, Please free up the storage")
            }
        } catch (e: IOException) {
            listener(false, "Save image failed")
            e.printStackTrace()
        } catch (e: OutOfMemoryError) {
            listener(false, "Out of memory")
            e.printStackTrace()
        } catch (e: java.lang.Exception) {
            listener(false, "Save image failed")
            e.printStackTrace()
        } finally {
            listener(true, "Save image success")
        }
    }

    fun formatSize(size: Long): String? {
        var size = size
        var suffix: String? = null
        if (size >= 1024) {
            suffix = "KB"
            size /= 1024
            if (size >= 1024) {
                suffix = "MB"
                size /= 1024
            }
        }
        val resultBuffer = StringBuilder(java.lang.Long.toString(size))
        var commaOffset = resultBuffer.length - 3
        while (commaOffset > 0) {
            resultBuffer.insert(commaOffset, ',')
            commaOffset -= 3
        }
        if (suffix != null) resultBuffer.append(suffix)
        return resultBuffer.toString()
    }

    fun formatSizeMb(size: Long): Long {
        var size = size
        if (size >= 1024) {
            size /= 1024*1024
        }
        return size
    }

    @SuppressLint("RestrictedApi")
    @Throws(IOException::class)
    fun rotateImageIfRequired(selectedImage: String, context: Context): Bitmap {
        val bmOptions = BitmapFactory.Options()
        val bitmap = BitmapFactory.decodeFile(selectedImage, bmOptions)
        val input = context.contentResolver.openInputStream(Uri.fromFile(File(selectedImage)))
       /* val ei: ExifInterface = if (Build.VERSION.SDK_INT > 23) ExifInterface(input!!) else ExifInterface(
                selectedImage
            )*/
        val exif = Exif.createFromInputStream(input!!)
        val orientation = exif.rotation
        Log.d("lsklkdlkd", orientation.toString())
       /* val orientation =
            ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)*/
        return rotateImage(
            bitmap,
            orientation.toFloat()
        )
        /*return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(
                bitmap,
                90f
            )
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(
                bitmap,
                180f
            )
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(
                bitmap,
                270f
            )
            else -> bitmap
        }*/
    }

    @SuppressLint("RestrictedApi")
    @Throws(IOException::class)
    fun rotateImageIfRequired(bitmap: Bitmap, selectedImage: String, context: Context): Bitmap {
        val input = context.contentResolver.openInputStream(Uri.fromFile(File(selectedImage)))
        val ei: ExifInterface = if (Build.VERSION.SDK_INT > 23) ExifInterface(input!!) else ExifInterface(
                 selectedImage
             )
         val orientation =
             ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(
                bitmap,
                90f
            )
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(
                bitmap,
                180f
            )
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(
                bitmap,
                270f
            )
            else -> bitmap
        }
    }

    fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }

    fun resize(image: Bitmap?, maxWidth: Int, maxHeight: Int): Bitmap? {
        var image = image
        try {
            if (image != null) {
                return if (maxHeight > 0 && maxWidth > 0) {
                    val width = image.width.toFloat()
                    val height = image.height.toFloat()
                    val ratioBitmap = width / height
                    val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()
                    var finalWidth = maxWidth
                    var finalHeight = maxHeight
                    if (ratioMax > ratioBitmap) {
                        finalWidth = (maxHeight * ratioBitmap).toInt()
                    } else {
                        finalHeight = (maxWidth / ratioBitmap).toInt()
                    }
                    image = Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true)
                    image
                } else {
                    image
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun convertDpToPixel(dp: Int): Int = (dp * Resources.getSystem().displayMetrics.density).toInt()

    fun saveSoundType(context: Context, soundType: Int) {
        val preferences =
            context.getSharedPreferences("chareem_camerax_setting.conf", Context.MODE_PRIVATE)
        val prefEdt = preferences.edit()
        prefEdt.putInt("sound_on_off", soundType)
        prefEdt.apply()
    }

    fun getSoundType(context: Context): Int {
        val preferences =
            context.getSharedPreferences("chareem_camerax_setting.conf", Context.MODE_PRIVATE)
        return preferences.getInt("sound_on_off", CameraSoundView.SOUND_TYPE_ON)
    }

    fun saveFlashType(context: Context, flashType: Int) {
        val preferences =
            context.getSharedPreferences("chareem_camerax_setting.conf", Context.MODE_PRIVATE)
        val prefEdt = preferences.edit()
        prefEdt.putInt("flash_type", flashType)
        prefEdt.apply()
    }

    fun getFlashType(context: Context): Int {
        val preferences =
            context.getSharedPreferences("chareem_camerax_setting.conf", Context.MODE_PRIVATE)
        return preferences.getInt("flash_type", FlashSwitchView.FLASH_AUTO)
    }
}
