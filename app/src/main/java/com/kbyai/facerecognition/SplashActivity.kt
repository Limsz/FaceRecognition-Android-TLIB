package com.kbyai.facerecognition

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val splashDuration = 2000L // 2 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Delay the start of the MainActivity (or any other activity you want to launch)
        Handler().postDelayed({
            val intent = Intent(this, MainActivity::class.java) // Change to the activity you want to show after splash
            startActivity(intent)
            finish()
        }, splashDuration)
    }
}
