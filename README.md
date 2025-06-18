# Tow Truck Driver App

A modern Android app for tow truck drivers with live location tracking and Google Maps integration.

## Features

- **Live Location Tracking**: Real-time location updates with Google Maps
- **Location Permission Handling**: Comprehensive permission management for foreground and background location access
- **Location Services Detection**: Automatic detection and prompting when location services are disabled
- **Continuous Location Updates**: Location updates every 5-10 seconds for accurate tracking
- **Modern UI**: Built with Jetpack Compose and Material 3 design

## Setup Instructions

### 1. Google Maps API Key

To use the live map functionality, you need to set up a Google Maps API key:

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the following APIs:
   - Maps SDK for Android
   - Places API (if you plan to add place search features)
4. Create credentials (API Key)
5. Replace `YOUR_MAPS_API_KEY` in `app/src/main/AndroidManifest.xml` with your actual API key:

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_ACTUAL_API_KEY_HERE" />
```

### 2. Location Permissions

The app automatically handles location permissions:
- **Foreground Location**: Required for basic map functionality
- **Background Location**: Optional, for continuous tracking when app is in background
- **Location Services**: Automatically detects if GPS is disabled and prompts user to enable it

### 3. Build and Run

1. Open the project in Android Studio
2. Sync the project with Gradle files
3. Build and run the app on a device or emulator

## Location Features

### Permission Flow
1. App requests location permission on first launch
2. User can grant or deny permission
3. If denied, app shows a card explaining why location access is needed
4. User can retry permission request

### Location Services
- Automatically detects if GPS is enabled
- Shows warning card if location services are disabled
- Provides direct link to device location settings

### Live Tracking
- Updates location every 5-10 seconds
- Smooth camera animations when location changes
- Sends location data to RabbitMQ service (if configured)
- Shows current coordinates in marker info

## Technical Details

- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **Architecture**: MVVM with Jetpack Compose
- **Location**: Google Play Services Location API
- **Maps**: Google Maps SDK for Android with Compose integration
- **Coroutines**: For asynchronous location updates

## Dependencies

- Google Maps Compose: `2.15.0`
- Google Play Services Maps: `18.2.0`
- Google Play Services Location: `21.1.0`
- Jetpack Compose BOM
- Material 3

## Security Notes

- Never commit your actual API key to version control
- Use environment variables or secure key management in production
- Consider implementing API key restrictions in Google Cloud Console