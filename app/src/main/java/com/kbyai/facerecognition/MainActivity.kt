package com.kbyai.facerecognition

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
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
        private const val CAMERA_MODE_FRONT = 7
        private const val CAMERA_MODE_BACK = 6
    }

    private lateinit var cameraView: CameraView
    private lateinit var faceView: FaceView
    private lateinit var fotoapparat: Fotoapparat
    private lateinit var context: Context
    private lateinit var dbManager: DBManager
    private lateinit var personAdapter: PersonAdapter
    private lateinit var textWarning: TextView
    @Volatile private var recognized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        context = this

        // Setup views
        cameraView = findViewById(R.id.preview)
        faceView = findViewById(R.id.faceView)
        textWarning = findViewById(R.id.textWarning)
        val listView: ListView = findViewById(R.id.listPerson)

        // Setup SDK
        var ret = FaceSDK.setActivation("S18+rOL1H3BXjAWGP7gEdgbJVotQ4g1o+YMcZruzEaKWFUQJHB2P1ylgw1FAfi+enDQA3nE4E9h6\n" +
                "NF6xL8uRrs33P9vekwdJCBLlIPcx+keHdNiFjq/3848TZjgMeJ3Xpvh1grWIh9kdGbEfnh6x0/xI\n" +
                "eCRCuxDn3Za5bRneYyKuUnmt2DGUx9ipZXZawZRT1kob9WxqABMMymYvCFpJMn6XVTZoRU2kRBxM\n" +
                "ZbMHN43Hu8HePUIPe01ytEGzEx7y0wRL3w794FpPQwAUepimUfifhSOhdx56SIwy4N0HZtGCNVaS\n" +
                "ZhP4SRsAKRbpmIXZ43daLCo4QKx1Kjh8IOrwHg==")
        if (ret == FaceSDK.SDK_SUCCESS) ret = FaceSDK.init(assets)
        if (ret != FaceSDK.SDK_SUCCESS) showSdkError(ret)

        // Setup database
        dbManager = DBManager(this)
        dbManager.loadPerson()

        // Setup person list
        personAdapter = PersonAdapter(this, DBManager.personList)
        listView.adapter = personAdapter

        // Setup camera
        setupCamera()

        // Buttons
        findViewById<Button>(R.id.buttonEnroll).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_picture)), SELECT_PHOTO_REQUEST_CODE)
        }
        val settingsButton = findViewById<ImageButton>(R.id.gearIcon)

// Set background color to blue
        settingsButton.setBackgroundColor(Color.parseColor("#0061AE"))

// Tint the gear icon to white
        settingsButton.setColorFilter(ContextCompat.getColor(this, android.R.color.white))

        findViewById<ImageButton>(R.id.gearIcon).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.buttonAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
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

    inner class FaceFrameProcessor : FrameProcessor {
        override fun process(frame: Frame) {
            if (recognized) return

            val useBackCamera = SettingsActivity.getCameraLens(context) == CameraSelector.LENS_FACING_BACK
            val cameraMode = if (useBackCamera) CAMERA_MODE_BACK else CAMERA_MODE_FRONT
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
                        val faceImage = Utils.cropFace(bitmap, faceBox)

                        runOnUiThread {
                            Intent(context, ResultActivity::class.java).apply {
                                putExtra("identified_face", faceImage)
                                putExtra("enrolled_face", bestMatch!!.face)
                                putExtra("identified_name", bestMatch.name)
                                putExtra("similarity", highestSimilarity)
                                putExtra("liveness", faceBox.liveness)
                                putExtra("yaw", faceBox.yaw)
                                putExtra("roll", faceBox.roll)
                                putExtra("pitch", faceBox.pitch)
                                startActivity(this)
                            }
                        }
                    }
                }
            }
        }
    }
}
