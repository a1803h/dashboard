package tj.lumina.photo.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Decode sampled JPEG/PNG/HEIC through Android's platform codecs, respecting EXIF rotation. */
object PhotoStore {
    fun decode(context: Context, uri: Uri, maxEdge: Int = 2048): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: error("Не удалось открыть фотографию")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Неподдерживаемый формат фотографии")
        var sampling = 1
        while (max(bounds.outWidth / sampling, bounds.outHeight / sampling) > maxEdge * 1.4f) {
            sampling *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampling
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val source = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Не удалось прочитать изображение")
        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postScale(-1f, 1f); matrix.postRotate(90f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postScale(-1f, 1f); matrix.postRotate(270f) }
        }
        val corrected = if (matrix.isIdentity) source else Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (corrected !== source) source.recycle()
        val largest = max(corrected.width, corrected.height)
        if (largest <= maxEdge) return corrected
        val scale = maxEdge.toFloat() / largest
        val scaled = Bitmap.createScaledBitmap(corrected, (corrected.width * scale).toInt().coerceAtLeast(1), (corrected.height * scale).toInt().coerceAtLeast(1), true)
        if (scaled !== corrected) corrected.recycle()
        return scaled
    }

    /** Android 10+ MediaStore export: no all-files permission required. */
    fun saveToGallery(context: Context, bitmap: Bitmap, presetName: String): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val name = "LUMINA_${presetName.replace(" ", "_")}_$stamp.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Lumina")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: error("Не удалось создать файл в галерее")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 96, output)) { "Ошибка JPEG-кодирования" }
            } ?: error("Нет доступа к записи фотографии")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    fun storeHistory(context: Context, bitmap: Bitmap, presetName: String) {
        val folder = File(context.filesDir, "history").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val filename = "photo_${stamp}_${(0..999).random()}.jpg"
        FileOutputStream(File(folder, filename)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        val prefs = context.getSharedPreferences("lumina_history", Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString("items", "[]"))
        val item = JSONObject().put("file", filename).put("preset", presetName).put("time", stamp)
        val next = JSONArray().put(item)
        for (index in 0 until minOf(array.length(), 99)) next.put(array.getJSONObject(index))
        prefs.edit().putString("items", next.toString()).apply()
    }

    fun listHistory(context: Context): List<HistoryItem> {
        val prefs = context.getSharedPreferences("lumina_history", Context.MODE_PRIVATE)
        return try {
            val array = JSONArray(prefs.getString("items", "[]"))
            (0 until array.length()).map { array.getJSONObject(it) }.mapNotNull { o ->
                val file = File(File(context.filesDir, "history"), o.getString("file"))
                if (file.exists()) HistoryItem(file, o.getString("preset"), o.getLong("time")) else null
            }
        } catch (_: Exception) { emptyList() }
    }

    fun loadHistoryBitmap(file: File): Bitmap? = BitmapFactory.decodeFile(file.absolutePath)

    fun clearHistory(context: Context) {
        File(context.filesDir, "history").listFiles()?.forEach { it.delete() }
        context.getSharedPreferences("lumina_history", Context.MODE_PRIVATE).edit().remove("items").apply()
    }
}

data class HistoryItem(val file: File, val preset: String, val time: Long)
