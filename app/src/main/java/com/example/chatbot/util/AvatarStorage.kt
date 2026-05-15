package com.example.chatbot.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.ImageViewCompat
import com.example.chatbot.R
import java.io.File
import java.io.FileOutputStream

object AvatarStorage {

    private const val DIR = "avatars"
    private const val MAX_DECODE_SIDE = 720

    fun avatarDir(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    fun saveFromUri(context: Context, uri: Uri, fileName: String): String? {
        return try {
            val outFile = File(avatarDir(context), fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            } ?: return null
            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun loadInto(imageView: ImageView, path: String?) {
        if (path.isNullOrBlank()) {
            showPlaceholder(imageView)
            return
        }
        val f = File(path)
        if (!f.exists()) {
            showPlaceholder(imageView)
            return
        }
        val bmp = decodeScaled(f.absolutePath)
        if (bmp == null) {
            showPlaceholder(imageView)
            return
        }
        imageView.setImageBitmap(bmp)
        ImageViewCompat.setImageTintList(imageView, null)
    }

    private fun showPlaceholder(imageView: ImageView) {
        imageView.setImageResource(R.drawable.ic_person)
        ImageViewCompat.setImageTintList(
            imageView,
            AppCompatResources.getColorStateList(imageView.context, R.color.pink_primary)
        )
    }

    private fun decodeScaled(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return BitmapFactory.decodeFile(path)
        while (w / sample > MAX_DECODE_SIDE || h / sample > MAX_DECODE_SIDE) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, opts)
    }

    /** 生成纯色圆形占位头像并落盘，用于「随机头像」 */
    fun saveTintedCircleAvatar(context: Context): String? {
        val palette = intArrayOf(
            0xFF5C6BC0.toInt(),
            0xFF26A69A.toInt(),
            0xFFEF5350.toInt(),
            0xFFAB47BC.toInt(),
            0xFF42A5F5.toInt(),
            0xFFFFA726.toInt(),
            0xFF789262.toInt(),
            0xFF8D6E63.toInt()
        )
        val color = palette.random()
        val size = 512
        return try {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            val r = size / 2f
            canvas.drawCircle(r, r, r * 0.92f, paint)
            val f = File(avatarDir(context), "char_rand_${System.currentTimeMillis()}.png")
            FileOutputStream(f).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 92, out) }
            bmp.recycle()
            f.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun deleteFileIfExists(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            File(path).takeIf { it.exists() }?.delete()
        } catch (_: Exception) { }
    }
}
