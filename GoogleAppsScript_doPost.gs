/**
 * doPost function to handle POST requests from GuardianTrack app.
 * It parses the POST parameters and writes data to Google Sheets.
 */

function doPost(e) {
  try {
    var ss = SpreadsheetApp.openById("YOUR_SPREADSHEET_ID"); // Replace with your spreadsheet ID
    var params = e.parameter;

    var userId = params.UserId;
    var type = params.Type;
    var details = params.Details;
    var latitude = params.Latitude;
    var longitude = params.Longitude;
    var timestamp = params.Timestamp;

    if (type) {
      // Log to Alerts sheet
      var alertsSheet = ss.getSheetByName("Alerts");
      alertsSheet.appendRow([userId, type, details, new Date(parseInt(timestamp))]);
    } else if (latitude && longitude) {
      // Log to Logs sheet
      var logsSheet = ss.getSheetByName("Logs");
      logsSheet.appendRow([userId, latitude, longitude, new Date(parseInt(timestamp))]);
    } else {
      // Unknown data, ignore or log error
      Logger.log("Unknown data received: " + JSON.stringify(params));
    }

    return ContentService.createTextOutput("Success");
  } catch (error) {
    Logger.log("Error in doPost: " + error);
    return ContentService.createTextOutput("Error: " + error.message);
  }
}
