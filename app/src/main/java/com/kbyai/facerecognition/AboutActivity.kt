package com.kbyai.facerecognition

import android.content.Intent
import android.os.Bundle
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    private lateinit var personAdapter: PersonAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // Get reference to the ListView
        val listView: ListView = findViewById(R.id.listPerson)

        // Initialize the adapter with the list of people from the database
        personAdapter = PersonAdapter(this, DBManager.personList)

        // Set the adapter to the ListView
        listView.adapter = personAdapter

        // You can still keep your existing contact options functionality if needed
        findViewById<TextView>(R.id.txtMail).setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "plain/text"
            intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("contact@kby-ai.com"))
            intent.putExtra(Intent.EXTRA_SUBJECT, "License Request")
            intent.putExtra(Intent.EXTRA_TEXT, "")
            startActivity(Intent.createChooser(intent, ""))
        }

        // Other contact methods (WhatsApp, Telegram, etc.) can remain the same
    }
}
