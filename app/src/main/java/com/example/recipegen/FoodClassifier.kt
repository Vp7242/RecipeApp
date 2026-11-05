package com.example.recipegen

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.nio.FloatBuffer

/** Runs food_model.onnx (see training/train_food_model_windows.py) fully on-device with ONNX Runtime. */
class FoodClassifier(private val context: Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val labels: List<String>
    private val size = 224

    init {
        val bytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
        session = env.createSession(bytes, OrtSession.SessionOptions())
        labels = context.assets.open(LABELS_FILE).bufferedReader().readLines().filter { it.isNotBlank() }
    }

    /** Returns the top-K (label, probability) pairs. */
    fun classify(uri: Uri, topK: Int = 3): List<Pair<String, Float>> {
        val data = toChw(loadBitmap(uri))
        OnnxTensor.createTensor(env, data, longArrayOf(1, 3, size.toLong(), size.toLong())).use { input ->
            session.run(mapOf(session.inputNames.first() to input)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val probs = (result[0].value as Array<FloatArray>)[0]
                require(probs.size == labels.size) { "labels.txt does not match model" }
                return probs.withIndex().sortedByDescending { it.value }.take(topK).map { labels[it.index] to it.value }
            }
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2 * size) sample *= 2
        val bmp = resolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Cannot decode image")
        val rotation = resolver.openInputStream(uri)!!.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
        return if (rotation == 0f) bmp
        else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
    }

    /** Centre-crop, resize to 224x224 and lay out as NCHW floats 0..255 (the model normalises itself). */
    private fun toChw(bmp: Bitmap): FloatBuffer {
        val side = minOf(bmp.width, bmp.height)
        val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, size, size, true)
        val px = IntArray(size * size)
        scaled.getPixels(px, 0, size, 0, 0, size, size)
        val plane = size * size
        val out = FloatArray(3 * plane)
        for (i in px.indices) {
            out[i] = ((px[i] shr 16) and 0xFF).toFloat()            // R plane
            out[plane + i] = ((px[i] shr 8) and 0xFF).toFloat()     // G plane
            out[2 * plane + i] = (px[i] and 0xFF).toFloat()         // B plane
        }
        return FloatBuffer.wrap(out)
    }

    override fun close() { session.close() }

    companion object {
        const val MODEL_FILE = "food_model.onnx"
        const val LABELS_FILE = "labels.txt"
        fun isAvailable(context: Context) =
            context.assets.list("")?.toList()?.containsAll(listOf(MODEL_FILE, LABELS_FILE)) == true
    }
}
