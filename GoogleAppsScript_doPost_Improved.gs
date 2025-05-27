/**
 * Improved doPost function with detailed logging for debugging.
 */
function doPost(e) {
  try {
    var ss = SpreadsheetApp.openById("1iWZniqX3_lYEplJYLy6h46p3GLoR5ckSTij0YKKfLgU"); // Use your Spreadsheet ID here

    Logger.log("Raw POST data: " + e.postData.contents);

    // Parse parameters manually from URL-encoded POST body
    var params = {};
    var raw = e.postData.contents;
    var pairs = raw.split("&");
    for (var i = 0; i < pairs.length; i++) {
      var parts = pairs[i].split("=");
      var key = decodeURIComponent(parts[0]);
      var value = decodeURIComponent(parts[1] || "");
      params[key] = value;
    }

    Logger.log("Parsed parameters: " + JSON.stringify(params));

    // Debugging: Append all received parameters to a new sheet "DebugParams"
    var debugSheet = ss.getSheetByName("DebugParams");
    if (!debugSheet) {
      debugSheet = ss.insertSheet("DebugParams");
      debugSheet.appendRow(["Parameter", "Value"]);
    }
    for (var key in params) {
      debugSheet.appendRow([key, params[key]]);
    }

    var userId = params.UserId;
    var type = params.Type ? params.Type.trim().toLowerCase() : "";
    var details = params.Details;
    var latitude = params.Latitude;
    var longitude = params.Longitude;
    var timestamp = params.Timestamp;

    var userName = params.UserName ? params.UserName.trim() : "";
    var phoneNumber = params.PhoneNumber ? params.PhoneNumber.trim() : "";
    var email = params.Email ? params.Email.trim() : "";

    Logger.log("Received parameters: " + JSON.stringify(params));
    Logger.log("Parsed userName: '" + userName + "', phoneNumber: '" + phoneNumber + "', email: '" + email + "'");
    Logger.log("Type parameter value: '" + type + "'");

    if (!userId) {
      Logger.log("Missing UserId parameter");
      return ContentService.createTextOutput("Error: Missing UserId");
    }

    if (type === "userdata") {
      // Log to Sheet1 (User) sheet without checking for empty fields
      var userSheet = ss.getSheetByName("Sheet1");
      if (!userSheet) {
        userSheet = ss.insertSheet("Sheet1");
        userSheet.appendRow(["UserId", "UserName", "PhoneNumber", "Email"]);
      }
      userSheet.appendRow([userId, userName, phoneNumber, email]);
      Logger.log("Logged user data to Sheet1");
    } else if (type) {
      // Log to Alerts sheet
      var alertsSheet = ss.getSheetByName("Alerts");
      alertsSheet.appendRow([userId, type, details || "", new Date(parseInt(timestamp) || Date.now())]);
      Logger.log("Logged alert to Alerts sheet");
    } else if (latitude && longitude) {
      // Log to Logs sheet
      var logsSheet = ss.getSheetByName("Logs");
      logsSheet.appendRow([userId, latitude, longitude, new Date(parseInt(timestamp) || Date.now())]);
      Logger.log("Logged location to Logs sheet");
    } else {
      Logger.log("Unknown data received: " + JSON.stringify(params));
      return ContentService.createTextOutput("Error: Unknown data");
    }

    return ContentService.createTextOutput("Success");
  } catch (error) {
    Logger.log("Error in doPost: " + error);
    return ContentService.createTextOutput("Error: " + error.message);
  }
}

/**
 * Simple doGet function to confirm the web app is running.
 */
function doGet(e) {
  return ContentService.createTextOutput("Google Sheet Integration Web App is running.");
}
