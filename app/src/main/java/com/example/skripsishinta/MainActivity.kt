package com.example.skripsishinta

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.skripsishinta.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.common.ops.NormalizeOp

import com.example.skripsishinta.ResultActivity.Companion.EXTRA_FOOD_LIST
import com.example.skripsishinta.ResultActivity.Companion.EXTRA_IMAGE_URI

class MainActivity : AppCompatActivity() {

    private lateinit var tflite: Interpreter
    private lateinit var binding: ActivityMainBinding

    private val foodLabels = listOf(
        "Ayam Goreng",
        "Cheese Cake",
        "Donat Coklat",
        "French Fries",
        "Ikan Goreng",
        "Nasi Putih",
        "Telur Mata Sapi",
        "Telur Rebus",
        "Tempe Goreng",
        "Waffle"
    )

    private val foodCalories = listOf(
        239,
        257,
        204,
        253,
        192,
        135,
        92,
        77,
        34,
        229
    )

    private val foodWeights = listOf(
        86,
        80,
        43,
        150,
        70,
        105,
        45,
        50,
        15,
        74
    )

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            binding.imagePlaceholder.setImageURI(it)
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let {
            binding.imagePlaceholder.setImageBitmap(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tflite = Interpreter(loadModelFile("last.tflite"))

        binding.buttonGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        binding.buttonCamera.setOnClickListener {
            cameraLauncher.launch(null)
        }

        binding.buttonUpload.setOnClickListener {
            val drawable = binding.imagePlaceholder.drawable
            if (drawable != null && drawable is BitmapDrawable) {
                val bitmap = drawable.bitmap

                val imageProcessor = ImageProcessor.Builder()
                    .add(ResizeWithCropOrPadOp(640, 640))
                    .add(NormalizeOp(0f, 255f))
                    .build()

                var tensorImage = TensorImage.fromBitmap(bitmap)
                tensorImage = imageProcessor.process(tensorImage)
                val inputBuffer = tensorImage.buffer

                val outputBuffer = Array(1) { Array(14) { FloatArray(8400) } }

                // Run model
                tflite.run(inputBuffer, outputBuffer)

                val detections = parseYoloOutput(outputBuffer, confThreshold = 0.4f)
                val nmsDetections = nonMaxSuppression(detections, iouThreshold = 0.5f)
                val bitmapWithBoxes = drawDetectionsOnBitmap(bitmap, nmsDetections)

                binding.imagePlaceholder.setImageBitmap(bitmapWithBoxes)

                if (nmsDetections.isNotEmpty()) {
                    val foodCounts = nmsDetections.groupingBy { it.classId }.eachCount()

                    val foodListForIntent = ArrayList<FoodItem>()
                    foodCounts.forEach { (classId, count) ->
                        foodListForIntent.add(
                            FoodItem(
                                name = foodLabels.getOrElse(classId) { "Unknown" },
                                quantity = count, // The number of times this food was detected
                                calories = foodCalories.getOrElse(classId) { 0 },
                                weight = foodWeights.getOrElse(classId) { 0 }
                            )
                        )
                    }

                    val bitmapWithBoxes = drawDetectionsOnBitmap(bitmap, nmsDetections)
                    val imageUri = saveBitmapToCache(bitmap)

                    val intent = Intent(this, ResultActivity::class.java).apply {
                        putParcelableArrayListExtra(
                            ResultActivity.EXTRA_FOOD_LIST,
                            foodListForIntent
                        )
                        putExtra(ResultActivity.EXTRA_IMAGE_URI, imageUri.toString())
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Gambar belum dipilih", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri {
        val cachePath = File(cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "input_image.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()
        return FileProvider.getUriForFile(this, "${packageName}.provider", file)
    }

    private fun loadModelFile(filename: String): ByteBuffer {
        val fileDescriptor = assets.openFd(filename)
        val inputStream = fileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(1 * 640 * 640 * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(640 * 640)
        bitmap.getPixels(intValues, 0, 640, 0, 0, 640, 640)
        for (pixel in intValues) {
            val r = (pixel shr 16 and 0xFF).toFloat()
            val g = (pixel shr 8 and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()
            byteBuffer.putFloat(r / 255.0f)
            byteBuffer.putFloat(g / 255.0f)
            byteBuffer.putFloat(b / 255.0f)
        }
        return byteBuffer
    }

    data class Detection(val classId: Int, val score: Float, val box: FloatArray)


    private fun parseYoloOutput(
        output: Array<Array<FloatArray>>,
        confThreshold: Float = 0.1f
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numAnchors = 8400
        val numClasses = output[0].size - 4

        val predictions = output[0] // Shape [14, 8400]

        for (i in 0 until numAnchors) {
            var bestClassId = -1
            var maxClassScore = 0f

            for (c in 4 until (4 + numClasses)) {
                val classScore = predictions[c][i]
                if (classScore > maxClassScore) {
                    maxClassScore = classScore
                    bestClassId = c - 4
                }
            }

            if (maxClassScore > confThreshold) {
                val cx = predictions[0][i]
                val cy = predictions[1][i]
                val w = predictions[2][i]
                val h = predictions[3][i]

                val left = cx - w / 2
                val top = cy - h / 2
                val right = cx + w / 2
                val bottom = cy + h / 2

                val box = floatArrayOf(left, top, right, bottom)
                detections.add(Detection(bestClassId, maxClassScore, box))
            }
        }

        return detections
    }


    private fun drawDetectionsOnBitmap(bitmap: Bitmap, detections: List<Detection>): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutableBitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.RED
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
        }
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GREEN
            textSize = 32f
            style = android.graphics.Paint.Style.FILL
        }

        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        val modelWidth = 640f
        val modelHeight = 640f

        val scale = minOf(modelWidth / originalWidth, modelHeight / originalHeight)
        val xOffset = (modelWidth - originalWidth * scale) / 2
        val yOffset = (modelHeight - originalHeight * scale) / 2

        for (det in detections) {
            val left = (det.box[0] - xOffset) / scale
            val top = (det.box[1] - yOffset) / scale
            val right = (det.box[2] - xOffset) / scale
            val bottom = (det.box[3] - yOffset) / scale

            canvas.drawRect(left, top, right, bottom, paint)
            canvas.drawText("Class ${det.classId} (${"%.2f".format(det.score)})", left, top - 10, textPaint)
        }

        return mutableBitmap
    }

    private fun nonMaxSuppression(
        detections: List<Detection>,
        iouThreshold: Float = 0.5f
    ): List<Detection> {
        val detectionsByClass = detections.groupBy { it.classId }
        val finalDetections = mutableListOf<Detection>()

        detectionsByClass.forEach { (_, classDetections) ->
            val sorted = classDetections.sortedByDescending { it.score }.toMutableList()

            while (sorted.isNotEmpty()) {
                val best = sorted.removeAt(0)
                finalDetections.add(best)

                val iterator = sorted.iterator()
                while (iterator.hasNext()) {
                    val other = iterator.next()
                    val iou = calculateIoU(best.box, other.box)
                    if (iou > iouThreshold) {
                        iterator.remove()
                    }
                }
            }
        }

        return finalDetections
    }

    private fun calculateIoU(boxA: FloatArray, boxB: FloatArray): Float {
        val xA = maxOf(boxA[0], boxB[0])
        val yA = maxOf(boxA[1], boxB[1])
        val xB = minOf(boxA[2], boxB[2])
        val yB = minOf(boxA[3], boxB[3])

        val interArea = maxOf(0f, xB - xA) * maxOf(0f, yB - yA)

        val boxAArea = maxOf(0f, boxA[2] - boxA[0]) * maxOf(0f, boxA[3] - boxA[1])
        val boxBArea = maxOf(0f, boxB[2] - boxB[0]) * maxOf(0f, boxB[3] - boxB[1])

        val iou = interArea / (boxAArea + boxBArea - interArea + 1e-6f)
        return iou
    }


}
