package com.example.guardiantrack

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*

class MainActivity : AppCompatActivity() {

    private lateinit var editTextUserId: EditText
    private lateinit var editTextUserName: EditText
    private lateinit var editTextPhoneNumber: EditText
    private lateinit var editTextEmail: EditText
    private lateinit var buttonSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called")

        setContentView(R.layout.activity_user_details)
        Log.d("MainActivity", "Set content view to activity_user_details")

        editTextUserId = findViewById(R.id.editTextUserId)
        editTextUserName = findViewById(R.id.editTextUserName)
        editTextPhoneNumber = findViewById(R.id.editTextPhoneNumber)
        editTextEmail = findViewById(R.id.editTextEmail)
        buttonSave = findViewById(R.id.buttonSave)

        // Pre-fill fields if data exists
        val prefs = getSharedPreferences("GuardianTrackPrefs", Context.MODE_PRIVATE)
        val userId = prefs.getInt("UserId", -1)
        val userName = prefs.getString("UserName", "")
        val phoneNumber = prefs.getString("PhoneNumber", "")
        val email = prefs.getString("Email", "")

        if (userId != -1) {
            editTextUserId.setText(userId.toString())
        }
        editTextUserName.setText(userName)
        editTextPhoneNumber.setText(phoneNumber)
        editTextEmail.setText(email)

        buttonSave.setOnClickListener {
            saveUserDetails()
        }
    }

    private fun saveUserDetails() {
        val userIdStr = editTextUserId.text.toString().trim()
        val userName = editTextUserName.text.toString().trim()
        val phoneNumber = editTextPhoneNumber.text.toString().trim()
        val email = editTextEmail.text.toString().trim()

        if (userIdStr.isEmpty() || userName.isEmpty() || phoneNumber.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = try {
            userIdStr.toInt()
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "User ID must be a number", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("GuardianTrackPrefs", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putInt("UserId", userId)
            putString("UserName", userName)
            putString("PhoneNumber", phoneNumber)
            putString("Email", email)
            apply()
        }

        Toast.makeText(this, "User details saved", Toast.LENGTH_SHORT).show()
        Log.d("MainActivity", "User details saved to SharedPreferences")

        sendUserDataToGoogleSheets(userId, userName, phoneNumber, email)

        startLocationService()
        finish()
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java)
        intent.action = LocationService.ACTION_START_TRACKING
        startService(intent)
        Log.d("MainActivity", "LocationService started")
    }

    private fun sendUserDataToGoogleSheets(userId: Int, userName: String, phoneNumber: String, email: String) {
        val url = "https://script.google.com/macros/s/AKfycbwulHIIPZrQXErYiQ1FDyW8yp_R8J0VU4ZjYfk-x-z1AK6hqYpIycu-CHQPovGBTGKcjg/exec"
        val client = OkHttpClient()

        val formBody = FormBody.Builder()
            .add("UserId", userId.toString())
            .add("UserName", userName)
            .add("PhoneNumber", phoneNumber)
            .add("Email", email)
            .add("Type", "UserData")
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e("MainActivity", "Failed to send user data to Google Sheets", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        Log.d("MainActivity", "User data sent successfully to Google Sheets")
                    } else {
                        Log.e("MainActivity", "Error sending user data: HTTP ${it.code}")
                    }
                }
            }
        })
    }
}
