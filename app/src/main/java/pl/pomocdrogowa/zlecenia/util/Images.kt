package pl.pomocdrogowa.zlecenia.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Plik i adres, pod który aparat zapisze zdjęcie. */
fun newCameraFile(context: Context): Pair<File, Uri> {
    val file = File(mediaDir(context), "camera_${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    return file to uri
}

/**
 * Zmniejsza zdjęcie (dłuższy bok max [maxSide] px), obraca wg EXIF i zapisuje jako JPEG.
 * Oszczędza transfer kierowcy w trasie.
 */
fun compressImage(context: Context, source: Uri, maxSide: Int = 1920): File {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(source)!!.use { BitmapFactory.decodeStream(it, null, bounds) }

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val decoded = resolver.openInputStream(source)!!.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Nie udało się odczytać zdjęcia")

    val rotation = runCatching {
        resolver.openInputStream(source)!!.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
    }.getOrDefault(0f)

    val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(rotation)
    }
    val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

    val out = File(mediaDir(context), "photo_${System.currentTimeMillis()}.jpg")
    out.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 82, it) }
    if (result !== decoded) result.recycle()
    decoded.recycle()
    return out
}
