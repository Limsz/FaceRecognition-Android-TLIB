package com.kbyai.facerecognition

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.util.Size
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kbyai.facesdk.*
import io.fotoapparat.Fotoapparat
import io.fotoapparat.parameter.Resolution
import io.fotoapparat.preview.Frame
import io.fotoapparat.preview.FrameProcessor

import io.fotoapparat.selector.front
import io.fotoapparat.selector.back
import io.fotoapparat.view.CameraView
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SELECT_PHOTO_REQUEST_CODE = 1
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var cameraView: CameraView
    private lateinit var faceView: FaceView
    private lateinit var fotoapparat: Fotoapparat
    private lateinit var context: Context
    private lateinit var dbManager: DBManager
    private lateinit var personAdapter: PersonAdapter
    private lateinit var textWarning: TextView
    private lateinit var textViewIdentifiedName: TextView
    private lateinit var textTimestamp: TextView


    @Volatile private var recognized = false
    private var lastIdentifiedName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        context = this

        cameraView = findViewById(R.id.preview)
        faceView = findViewById(R.id.faceView)
        textWarning = findViewById(R.id.textWarning)
        val listView: ListView = findViewById(R.id.listPerson)
        textViewIdentifiedName = findViewById(R.id.textViewIdentifiedName)
        textTimestamp = findViewById(R.id.textTimestamp)


        var ret = FaceSDK.setActivation("S18+rOL1H3BXjAWGP7gEdgbJVotQ4g1o+YMcZruzEaKWFUQJHB2P1ylgw1FAfi+enDQA3nE4E9h6\n" +
                "NF6xL8uRrs33P9vekwdJCBLlIPcx+keHdNiFjq/3848TZjgMeJ3Xpvh1grWIh9kdGbEfnh6x0/xI\n" +
                "eCRCuxDn3Za5bRneYyKuUnmt2DGUx9ipZXZawZRT1kob9WxqABMMymYvCFpJMn6XVTZoRU2kRBxM\n" +
                "ZbMHN43Hu8HePUIPe01ytEGzEx7y0wRL3w794FpPQwAUepimUfifhSOhdx56SIwy4N0HZtGCNVaS\n" +
                "ZhP4SRsAKRbpmIXZ43daLCo4QKx1Kjh8IOrwHg==")
        if (ret == FaceSDK.SDK_SUCCESS) ret = FaceSDK.init(assets)
        if (ret != FaceSDK.SDK_SUCCESS) showSdkError(ret)


        dbManager = DBManager(this)
        dbManager.loadPerson()

        personAdapter = PersonAdapter(this, DBManager.personList)
        listView.adapter = personAdapter

        setupCamera()

        findViewById<ImageButton>(R.id.buttonEnroll).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_picture)), SELECT_PHOTO_REQUEST_CODE)
        }

        findViewById<ImageButton>(R.id.buttonSetting).setOnClickListener {
            // Open the SettingsActivity or show a dialog
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }


        findViewById<ImageButton>(R.id.buttonAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        findViewById<ImageButton>(R.id.buttonCapture).setOnClickListener {
            captureAndSaveFace()
        }
    }

    private fun setupCamera() {
        val useBackCamera = SettingsActivity.getCameraLens(context) == CameraSelector.LENS_FACING_BACK
        fotoapparat = Fotoapparat.with(this)
            .into(cameraView)
            .lensPosition(if (useBackCamera) back() else front())
            .frameProcessor(FaceFrameProcessor())
            .previewResolution { Resolution(1280, 720) }
            .build()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            fotoapparat.start()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        }
    }

    private fun captureAndSaveFace() {
        fotoapparat.takePicture().toBitmap().whenAvailable { photo ->
            if (photo == null) {
                toast("Capture failed")
                return@whenAvailable
            }

            var bitmap = photo.bitmap

            // Apply rotation to tilt the image to the right
            val matrix = android.graphics.Matrix()
            matrix.postRotate(240f)  // Rotate by 90 degrees (right tilt)
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            val param = FaceDetectionParam().apply {
                check_liveness = true
                check_liveness_level = SettingsActivity.getLivenessLevel(this@MainActivity)
            }

            val faceBoxes = FaceSDK.faceDetection(bitmap, param)

            when {
                faceBoxes.isNullOrEmpty() -> toast(getString(R.string.no_face_detected))
                faceBoxes.size > 1 -> toast(getString(R.string.multiple_face_detected))
                else -> {
                    val faceBox = faceBoxes[0]
                    val faceImage = Utils.cropFace(bitmap, faceBox)
                    val templates = FaceSDK.templateExtraction(bitmap, faceBox)

                    val threshold = SettingsActivity.getIdentifyThreshold(this)
                    for (person in DBManager.personList) {
                        val similarity = FaceSDK.similarityCalculation(templates, person.templates)
                        if (similarity > threshold) {
                            toast("Already registered as ${person.name}")
                            return@whenAvailable
                        }
                    }

                    promptName { name ->
                        dbManager.insertPerson(name, faceImage, templates)
                        personAdapter.notifyDataSetChanged()
                        toast("Person saved as $name")
                    }
                }
            }
        }
    }

    private fun promptName(onNameEntered: (String) -> Unit) {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Enter name")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val name = input.text.toString().ifBlank { "Person_${Random.nextInt(10000, 20000)}" }
                onNameEntered(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSdkError(code: Int) {
        textWarning.visibility = View.VISIBLE
        textWarning.text = when (code) {
            FaceSDK.SDK_LICENSE_KEY_ERROR -> "Invalid license!"
            FaceSDK.SDK_LICENSE_APPID_ERROR -> "Invalid App ID!"
            FaceSDK.SDK_LICENSE_EXPIRED -> "License expired!"
            FaceSDK.SDK_NO_ACTIVATED -> "Not activated!"
            FaceSDK.SDK_INIT_ERROR -> "Initialization error!"
            else -> "Unknown error!"
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        recognized = false
        personAdapter.notifyDataSetChanged()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            fotoapparat.start()
        }
    }

    override fun onPause() {
        super.onPause()
        fotoapparat.stop()
        faceView.setFaceBoxes(null)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            fotoapparat.start()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SELECT_PHOTO_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val bitmap = Utils.getCorrectlyOrientedImage(this, uri)
                    val param = FaceDetectionParam().apply {
                        check_liveness = true
                        check_liveness_level = SettingsActivity.getLivenessLevel(this@MainActivity)
                    }

                    val faceBoxes = FaceSDK.faceDetection(bitmap, param)

                    when {
                        faceBoxes.isNullOrEmpty() -> toast(getString(R.string.no_face_detected))
                        faceBoxes.size > 1 -> toast(getString(R.string.multiple_face_detected))
                        else -> {
                            val faceBox = faceBoxes[0]
                            val faceImage = Utils.cropFace(bitmap, faceBox)
                            val templates = FaceSDK.templateExtraction(bitmap, faceBox)

                            promptName { name ->
                                dbManager.insertPerson(name, faceImage, templates)
                                personAdapter.notifyDataSetChanged()
                                toast(getString(R.string.person_enrolled))
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    toast("Error processing image")
                }
            }
        }
    }

    inner class FaceFrameProcessor : FrameProcessor {
        override fun process(frame: Frame) {
            if (recognized) return

            val useBackCamera = SettingsActivity.getCameraLens(context) == CameraSelector.LENS_FACING_BACK
            val cameraMode = if (useBackCamera) 6 else 7
            val bitmap = FaceSDK.yuv2Bitmap(frame.image, frame.size.width, frame.size.height, cameraMode)

            val faceParam = FaceDetectionParam().apply {
                check_liveness = true
                check_liveness_level = SettingsActivity.getLivenessLevel(context)
            }

            val faceBoxes = FaceSDK.faceDetection(bitmap, faceParam)

            runOnUiThread {
                faceView.setFrameSize(Size(bitmap.width, bitmap.height))
                faceView.setFaceBoxes(faceBoxes)
            }

            faceBoxes.firstOrNull()?.let { faceBox ->
                if (faceBox.liveness > SettingsActivity.getLivenessThreshold(context)) {
                    val templates = FaceSDK.templateExtraction(bitmap, faceBox)

                    var bestMatch: Person? = null
                    var highestSimilarity = 0f

                    for (person in DBManager.personList) {
                        val similarity = FaceSDK.similarityCalculation(templates, person.templates)
                        if (similarity > highestSimilarity) {
                            highestSimilarity = similarity
                            bestMatch = person
                        }
                    }

                    if (highestSimilarity > SettingsActivity.getIdentifyThreshold(context)) {
                        recognized = true
                        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", java.util.Locale.getDefault())
                            .format(java.util.Date())
                        runOnUiThread {
                            textViewIdentifiedName.text = "Identified: ${bestMatch!!.name}"

                            textTimestamp.text = timestamp
                            textTimestamp.visibility = View.VISIBLE

                            Handler(mainLooper).postDelayed({
                                textViewIdentifiedName.text = "Identified: Unknown"
                                textTimestamp.text = "0000-00-00 00:00:00 AM/PM"
                                fotoapparat.start()
                                recognized = false
                            }, 3000)
                        }
                    }
                }
            }
        }
    }
}

