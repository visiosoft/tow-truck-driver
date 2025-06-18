# Google Maps API Key Setup Guide

## Quick Setup (5 minutes):

### 1. Get API Key
1. Go to: https://console.cloud.google.com/
2. Create/Select a project
3. Go to "APIs & Services" > "Library"
4. Search for "Maps SDK for Android" and enable it
5. Go to "APIs & Services" > "Credentials"
6. Click "Create Credentials" > "API Key"
7. Copy the generated key

### 2. Replace in AndroidManifest.xml
Replace this line in `app/src/main/AndroidManifest.xml`:
```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_ACTUAL_API_KEY_HERE" />
```

### 3. Test
- Build and run the app
- Look for "✅ Google Maps loaded successfully!" in logs
- You should see a "Test Marker" on the map

## Troubleshooting:
- **"API key not found"**: Check if you replaced the placeholder
- **"API key invalid"**: Enable Maps SDK for Android in Google Cloud Console
- **"Billing required"**: Enable billing in Google Cloud Console
- **No map showing**: Check internet connection and Google Play Services

## Security Note:
- Never commit your API key to public repositories
- Use environment variables in production
- Set API key restrictions in Google Cloud Console 