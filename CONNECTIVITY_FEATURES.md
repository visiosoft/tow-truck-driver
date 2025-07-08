# Connectivity and Location Checking Features

## Overview

The TowTruckDriver app now includes comprehensive connectivity and location checking features to ensure that drivers can only go online when all necessary services are available.

## Features Implemented

### 1. Network Connectivity Monitoring
- **Real-time network monitoring**: The app continuously monitors network connectivity status
- **Multiple network types**: Supports WiFi, cellular, and Ethernet connections
- **Network capability checking**: Verifies that the network has internet access capability
- **Automatic status updates**: Network status is updated in real-time and reflected in the UI

### 2. Location Services Checking
- **Location permission verification**: Checks if the app has location permissions granted
- **Location services status**: Verifies that GPS and network location providers are enabled
- **Background location support**: Includes support for background location access
- **Automatic permission requests**: Prompts users for location permissions when needed

### 3. Combined Connectivity Status
- **Unified status indicator**: Shows both internet and location status in one component
- **Visual status indicators**: Uses icons and colors to clearly show status
- **Real-time updates**: Status updates automatically as conditions change

### 4. Go Online Protection
- **Pre-flight checks**: Validates all requirements before allowing users to go online
- **Interactive dialog**: Shows detailed information about what's missing
- **Direct action buttons**: Provides quick access to relevant system settings
- **Clear feedback**: Users know exactly what needs to be enabled

## Technical Implementation

### Network Connectivity
```kotlin
// Network connectivity manager
object NetworkConnectivityManager {
    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()
}

// Network availability check
fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    } else {
        @Suppress("DEPRECATION")
        val networkInfo = connectivityManager.activeNetworkInfo
        @Suppress("DEPRECATION")
        return networkInfo != null && networkInfo.isConnected
    }
}
```

### Location Services
```kotlin
// Location permission check
fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
           ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

// Location services check
fun isLocationEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
           locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}
```

### Combined Check
```kotlin
// Combined check for going online
fun canGoOnline(context: Context): Boolean {
    val hasLocation = hasLocationPermission(context)
    val locationEnabled = isLocationEnabled(context)
    val hasInternet = hasInternetAccess(context)
    
    return hasLocation && locationEnabled && hasInternet
}
```

## UI Components

### ConnectivityStatusIndicator
- Shows internet connectivity status with appropriate icons
- Displays location services status
- Provides overall "Ready to Go Online" status
- Updates in real-time as conditions change

### ConnectivityCheckDialog
- Detailed breakdown of what's missing
- Direct action buttons to fix issues
- Clear visual indicators (checkmarks/X marks)
- Easy access to system settings

### Go Online Button
- Changes appearance based on readiness status
- Shows "Go Online" when ready, "Check Requirements" when not
- Triggers connectivity dialog when requirements aren't met
- Provides clear feedback about what's needed

## Permissions Required

The app requires the following permissions in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

## Usage Flow

1. **App Launch**: Network and location status are checked automatically
2. **Map Screen**: Connectivity status indicator shows current state
3. **Go Online Attempt**: 
   - If all requirements met: User can go online
   - If requirements missing: Connectivity dialog appears
4. **Dialog Actions**: 
   - Grant Permission: Opens permission request
   - Enable Location: Opens location settings
   - Network Settings: Opens network settings
5. **Real-time Updates**: Status updates automatically as conditions change

## Benefits

- **Prevents failed online attempts**: Users can't go online without proper connectivity
- **Clear feedback**: Users know exactly what's needed
- **Easy fixes**: Direct access to relevant system settings
- **Real-time monitoring**: Status updates automatically
- **Better user experience**: No confusion about why going online fails

## Debugging

The implementation includes comprehensive logging:

```
🔍 Connectivity Check:
  📍 Location Permission: true
  📍 Location Services: true
  🌐 Internet Access: true
  ✅ Can Go Online: true
```

This helps developers understand the current state of connectivity and location services. 