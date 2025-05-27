package com.example.guardiantrack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import okhttp3.*
import java.io.IOException

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private val channelID = "GuardianTrackChannel"
    private val notifID =1


    private val client = OkHttpClient()
    private val googleSheetsUrl = "https://script.google.com/macros/s/AKfycbwulHIIPZrQXErYiQ1FDyW8yp_R8J0VU4ZjYfk-x-z1AK6hqYpIycu-CHQPovGBTGKcjg/exec"

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, channelID)
            .setContentTitle("GuardianTrack")
            .setContentText("Tracking location in background")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Make sure this resource exists
            .build()

        startForeground(notifID, notification)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            60_000L
        )
            .setMinUpdateIntervalMillis(60_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location: Location? = locationResult.lastLocation
                location?.let {
                    Log.d("LocationService", "Location: ${it.latitude}, ${it.longitude}")
                    sendLocationToGoogleSheets(it.latitude, it.longitude)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun sendLocationToGoogleSheets(latitude: Double, longitude: Double) {
        val formBody = FormBody.Builder()
            .add("latitude", latitude.toString())
            .add("longitude", longitude.toString())
            .build()

        val request = Request.Builder()
            .url(googleSheetsUrl)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("LocationService", "Failed to send location to Google Sheets", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("LocationService", "Location sent to Google Sheets successfully")
                } else {
                    Log.e("LocationService", "Error sending location to Google Sheets: ${response.code}")
                }
                response.close()
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelID,
                "GuardianTrack Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
