# GuardianTrack Android Application

## Overview
GuardianTrack is an Android application designed to track user location, call logs, and SMS logs. It securely sends this data to a Google Sheets backend for monitoring and analysis. The app prompts users to input their details on first launch, which are saved locally and appended to a Google Sheets document.

## Features
- User details input and storage using SharedPreferences.
- Background location tracking using FusedLocationProviderClient.
- Fetching and sending call logs and SMS logs.
- Foreground service with persistent notification.
- Data submission to Google Sheets via Google Apps Script web app.
- Robust error handling and permission checks.

## Setup and Installation
1. Clone the repository.
2. Open the project in Android Studio.
3. Configure the Google Apps Script URL in `MainActivity.kt` and `LocationService.kt`.
4. Ensure the Google Sheets document has sheets named:
   - `Sheet1` for user data.
   - `Alerts` for alerts.
   - `Logs` for location logs.
5. Build and run the app on an Android device or emulator.
6. Grant necessary permissions when prompted.

## Usage
- On first launch, enter user details and save.
- The app will start tracking location, call logs, and SMS logs in the background.
- Data is sent to Google Sheets for monitoring.

## Troubleshooting
- Ensure all required permissions are granted.
- Verify the Google Apps Script URL and Google Sheets setup.
- Check the `DebugParams` sheet in Google Sheets for received parameters.
- Review app logs for errors.

## Testing
- Test user details input and saving.
- Verify location updates and data submission.
- Confirm call and SMS logs are fetched and sent.
- Check Google Sheets for correct data appending.

## Dependencies
- AndroidX libraries
- Google Play Services Location
- OkHttp for HTTP requests

## License
MIT License

## Contact
For issues or contributions, please contact the maintainer.
