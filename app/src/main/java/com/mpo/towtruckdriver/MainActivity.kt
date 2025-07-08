package com.mpo.towtruckdriver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mpo.towtruckdriver.ui.theme.TowTruckDriverTheme
import kotlinx.coroutines.delay
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import android.location.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.collectAsState
import android.os.Looper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically

// Location manager to handle location state
object AppLocationManager {
    private val _location = MutableStateFlow<LatLng?>(null)
    val location = _location.asStateFlow()

    fun updateLocation(newLocation: LatLng) {
        _location.value = newLocation
    }
}

// Network connectivity manager
object NetworkConnectivityManager {
    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    fun updateConnectionStatus(connected: Boolean) {
        _isConnected.value = connected
    }
}

// Utility functions for connectivity checking
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

fun hasInternetAccess(context: Context): Boolean {
    return isNetworkAvailable(context)
}

// Utility functions for location checking
fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
           ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

fun isLocationEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
           locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
}

// Sound and vibration manager for tow requests
class TowRequestNotifier(private val context: Context) {
    private var ringtone: android.media.Ringtone? = null
    private var vibrator: Vibrator? = null
    
    init {
        try {
            // Get default notification ringtone
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(context, notification)
            
            // Get vibrator service
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as Vibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (e: Exception) {
            println("Error initializing sound/vibration: ${e.message}")
        }
    }
    
    fun playTowRequestAlert() {
        try {
            // Play ringtone
            ringtone?.let { rt ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    rt.audioAttributes = audioAttributes
                }
                rt.play()
            }
            
            // Vibrate device
            vibrator?.let { vib ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val vibrationEffect = VibrationEffect.createOneShot(
                        1000, // Duration in milliseconds
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                    vib.vibrate(vibrationEffect)
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(1000) // Duration in milliseconds
                }
            }
        } catch (e: Exception) {
            println("Error playing tow request alert: ${e.message}")
        }
    }
    
    fun stopAlert() {
        try {
            ringtone?.stop()
        } catch (e: Exception) {
            println("Error stopping alert: ${e.message}")
        }
    }
}

// Combined check for going online
fun canGoOnline(context: Context): Boolean {
    val hasLocation = hasLocationPermission(context)
    val locationEnabled = isLocationEnabled(context)
    val hasInternet = hasInternetAccess(context)
    
    val canGo = hasLocation && locationEnabled && hasInternet
    
    println("🔍 Connectivity Check:")
    println("  📍 Location Permission: $hasLocation")
    println("  📍 Location Services: $locationEnabled")
    println("  🌐 Internet Access: $hasInternet")
    println("  ✅ Can Go Online: $canGo")
    
    return canGo
}

class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var connectivityManager: ConnectivityManager

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted
                startLocationUpdates()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted
                startLocationUpdates()
            }
            else -> {
                // No location access granted
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("TowTruckDriverPrefs", Context.MODE_PRIVATE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        // Set up network connectivity monitoring
        setupNetworkMonitoring()
        
        enableEdgeToEdge()
        setContent {
            TowTruckDriverTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        requestLocationPermission = { requestLocationPermission() },
                        sharedPreferences = sharedPreferences
                    )
                }
            }
        }
    }

    private fun setupNetworkMonitoring() {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                NetworkConnectivityManager.updateConnectionStatus(true)
                println("🌐 Network available")
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                NetworkConnectivityManager.updateConnectionStatus(false)
                println("❌ Network lost")
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                NetworkConnectivityManager.updateConnectionStatus(hasInternet)
                println("🌐 Network capabilities changed - Internet: $hasInternet")
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                startLocationUpdates()
                // Request background location if needed
                requestBackgroundLocationPermission()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // Show permission rationale
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            else -> {
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun requestBackgroundLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            // Request background location permission
            locationPermissionRequest.launch(
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            )
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(5000)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                // Update the location state
                AppLocationManager.updateLocation(LatLng(location.latitude, location.longitude))
                println("Location updated: ${location.latitude}, ${location.longitude}")
            }
        }

        override fun onLocationAvailability(locationAvailability: LocationAvailability) {
            if (!locationAvailability.isLocationAvailable) {
                println("Location is not available")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

@Composable
fun AppBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Row(
            modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
        Text(
                    text = "TowTruck Pro",
            color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
            }
            
            // Status indicator
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Online",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

enum class PasswordStrength {
    WEAK, MEDIUM, STRONG
}

@Composable
fun PasswordStrengthMeter(
    password: String,
    modifier: Modifier = Modifier
) {
    val strength = when {
        password.length < 8 -> PasswordStrength.WEAK
        password.length < 12 || !password.any { it.isDigit() } || !password.any { it.isUpperCase() } -> PasswordStrength.MEDIUM
        else -> PasswordStrength.STRONG
    }

    val color = when (strength) {
        PasswordStrength.WEAK -> Color.Red
        PasswordStrength.MEDIUM -> Color(0xFFFFA500) // Orange
                    PasswordStrength.STRONG -> Color(0xFF006400) // Dark green
    }

    val text = when (strength) {
        PasswordStrength.WEAK -> "Weak"
        PasswordStrength.MEDIUM -> "Medium"
        PasswordStrength.STRONG -> "Strong"
    }

    Column(modifier = modifier) {
            LinearProgressIndicator(
        progress = { when (strength) {
            PasswordStrength.WEAK -> 0.33f
            PasswordStrength.MEDIUM -> 0.66f
            PasswordStrength.STRONG -> 1f
        }},
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp),
        color = color
    )
    Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreenWithMenu(
    onLogout: () -> Unit,
    onHome: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize()) {
        MapScreen(modifier = Modifier.fillMaxSize())
        // Top app bar with menu icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 8.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu")
            }
        }
        // Modal bottom sheet menu
        if (showMenu) {
            ModalBottomSheet(
                onDismissRequest = { showMenu = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            showMenu = false
                            onHome()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDAA520) // Goldenrod
                        )
                    ) {
                        Text("Home")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            showMenu = false
                            onProfile()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDAA520) // Goldenrod
                        )
                    ) {
                        Text("Profile")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            showMenu = false
                            onLogout()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Logout")
                    }
                }
            }
        }
    }
}

@Composable
fun MapScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userLocation by AppLocationManager.location.collectAsState(initial = null)
    val rabbitMQService = remember { RabbitMQService() }
    var hasLocationPermission by remember {
        mutableStateOf(hasLocationPermission(context))
    }
    
    var isLocationEnabled by remember {
        mutableStateOf(isLocationEnabled(context))
    }
    
    var hasInternetAccess by remember {
        mutableStateOf(hasInternetAccess(context))
    }
    
    var showConnectivityDialog by remember {
        mutableStateOf(false)
    }
    
    var mapProperties by remember {
        mutableStateOf(
            MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapType = MapType.NORMAL
            )
        )
    }

    var cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            userLocation ?: LatLng(0.0, 0.0),
            15f
        )
    }

    // Request location updates if permission and services are enabled
    LaunchedEffect(hasLocationPermission, isLocationEnabled) {
        if (hasLocationPermission && isLocationEnabled) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                    .setMinUpdateIntervalMillis(2000)
                    .build()
                
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            result.lastLocation?.let { location ->
                                AppLocationManager.updateLocation(LatLng(location.latitude, location.longitude))
                                println("Location updated: ${location.latitude}, ${location.longitude}")
                            }
                        }
                        override fun onLocationAvailability(availability: LocationAvailability) {
                            if (!availability.isLocationAvailable) {
                                println("Location is not available")
                            }
                        }
                    },
                    Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                println("Location permission error: ${e.message}")
            } catch (e: Exception) {
                println("Location update error: ${e.message}")
            }
        }
    }

    // Update camera position when location changes
    LaunchedEffect(userLocation) {
        userLocation?.let { location ->
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLng(location),
                durationMs = 1000
            )
            
            // Send location update to RabbitMQ
            val locationMessage = """
                {
                    "latitude": ${location.latitude},
                    "longitude": ${location.longitude},
                    "timestamp": ${System.currentTimeMillis()}
                }
            """.trimIndent()
            rabbitMQService.sendMessage(locationMessage)
        }
    }

    // Update map properties when permission changes
    LaunchedEffect(hasLocationPermission) {
        mapProperties = mapProperties.copy(isMyLocationEnabled = hasLocationPermission)
    }
    
    // Periodic check for connectivity status
    LaunchedEffect(Unit) {
        while (true) {
            hasLocationPermission = hasLocationPermission(context)
            isLocationEnabled = isLocationEnabled(context)
            hasInternetAccess = hasInternetAccess(context)
            delay(2000) // Check every 2 seconds
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            properties = mapProperties,
            cameraPositionState = cameraPositionState,
            onMapLoaded = {
                // Map is ready
                println("✅ Google Maps loaded successfully!")
                println("🗺️ Map API Key Status: Working")
            }
        ) {
            // Test marker to verify map is working
            Marker(
                state = MarkerState(position = LatLng(0.0, 0.0)),
                title = "Test Marker",
                snippet = "If you see this, Maps API is working!"
            )
            
            if (hasLocationPermission && userLocation != null) {
                Marker(
                    state = MarkerState(position = userLocation!!),
                    title = "Your Location",
                    snippet = "Lat: ${userLocation!!.latitude}, Lng: ${userLocation!!.longitude}"
                )
            }
        }

        // Show location services disabled message
        if (!isLocationEnabled) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .offset(y = 120.dp), // Offset to avoid overlap with connectivity indicator
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF8DC) // Ivory background
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Location Services Disabled",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF000000) // Black text for better visibility
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Please enable location services to see your current position on the map.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF000000) // Black text for better visibility
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDAA520) // Mustard
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Enable Location Services",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        
        // Show connectivity status indicator at bottom
        ConnectivityStatusIndicator(
            hasLocationPermission = hasLocationPermission,
            isLocationEnabled = isLocationEnabled,
            hasInternetAccess = hasInternetAccess,
            userLocation = userLocation,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
        
        // Show connectivity check dialog
        if (showConnectivityDialog) {
            ConnectivityCheckDialog(
                hasLocationPermission = hasLocationPermission,
                isLocationEnabled = isLocationEnabled,
                hasInternetAccess = hasInternetAccess,
                onDismiss = { showConnectivityDialog = false },
                onRequestLocationPermission = {
                    // This will be handled by the MainActivity
                    showConnectivityDialog = false
                },
                onOpenLocationSettings = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    showConnectivityDialog = false
                },
                onOpenNetworkSettings = {
                    context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                    showConnectivityDialog = false
                }
            )
        }
        
        // Show permission denied message
        if (!hasLocationPermission) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .offset(y = 120.dp), // Offset to avoid overlap with connectivity indicator
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF8DC) // Ivory background
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Location Permission Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF000000) // Black text for better visibility
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Please grant location permission to see your current position on the map.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF000000) // Black text for better visibility
                    )
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(
    userName: String,
    onLocationPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLocationRequest by remember { mutableStateOf(true) }
    var showMap by remember { mutableStateOf(false) }
    var showWelcomeNote by remember { mutableStateOf(true) }
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isLocationEnabled by remember {
        mutableStateOf(
            (context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager)
                .isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        )
    }

    var hasInternetAccess by remember {
        mutableStateOf(hasInternetAccess(context))
    }

    // Auto-hide welcome note after 3 seconds
    LaunchedEffect(Unit) {
        delay(3000)
        showWelcomeNote = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Full screen map
        if (showMap || hasLocationPermission) {
            MapScreen(
                modifier = Modifier.fillMaxSize()
            )
        }

        // Welcome note overlay (shown for first 3 seconds)
        if (showWelcomeNote) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(24.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Welcome, $userName!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "We're excited to have you on board!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Show location request if permission not granted
                    if (!hasLocationPermission) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Location Access Required",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "To provide you with the best service, we need access to your location. This helps us find nearby towing jobs and provide accurate navigation.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showLocationRequest = false },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Not Now")
                                    }

                                    Button(
                                        onClick = {
                                            onLocationPermissionGranted()
                                            showLocationRequest = false
                                            hasLocationPermission = true
                                            showMap = true
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Allow Access")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Connectivity status indicator (always visible at top)
        ConnectivityStatusIndicator(
            hasLocationPermission = hasLocationPermission,
            isLocationEnabled = isLocationEnabled,
            hasInternetAccess = hasInternetAccess,
            userLocation = AppLocationManager.location.collectAsState(initial = null).value,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth()
        )

        // Show location services disabled message at bottom
        if (!isLocationEnabled && (showMap || hasLocationPermission)) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⚠️ Location Services Disabled",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Please enable location services in your device settings to see your current position on the map.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Enable Location Services")
                    }
                }
            }
        }
    }
}

@Composable
fun SignUpScreen(
    onSignUpClick: (String, String, String) -> Unit,
    onBackToSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var shouldAttemptSignUp by remember { mutableStateOf(false) }

    LaunchedEffect(shouldAttemptSignUp) {
        if (shouldAttemptSignUp) {
            isLoading = true
            delay(1500) // Simulate network delay
            onSignUpClick(name, email, password)
            isLoading = false
            shouldAttemptSignUp = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 32.dp, bottom = 8.dp)
        )

        Text(
            text = "Please fill in your details",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = name,
            onValueChange = { 
                name = it
                nameError = null
            },
            label = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth(),
            isError = nameError != null,
            supportingText = {
                if (nameError != null) {
                    Text(nameError!!)
                }
            }
        )

        OutlinedTextField(
            value = email,
            onValueChange = { 
                email = it
                emailError = null
            },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = emailError != null,
            supportingText = {
                if (emailError != null) {
                    Text(emailError!!)
                }
            }
        )

        OutlinedTextField(
            value = password,
            onValueChange = { 
                password = it
                passwordError = null
            },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            isError = passwordError != null,
            supportingText = {
                if (passwordError != null) {
                    Text(passwordError!!)
                }
            }
        )

        if (password.isNotEmpty()) {
            PasswordStrengthMeter(
                password = password,
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { 
                confirmPassword = it
                confirmPasswordError = null
            },
            label = { Text("Confirm Password") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            isError = confirmPasswordError != null,
            supportingText = {
                if (confirmPasswordError != null) {
                    Text(confirmPasswordError!!)
                }
            }
        )

        Button(
            onClick = {
                // Validate inputs
                var hasError = false
                
                if (name.isEmpty()) {
                    nameError = "Name is required"
                    hasError = true
                }
                
                if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailError = "Please enter a valid email"
                    hasError = true
                }
                
                if (password.isEmpty()) {
                    passwordError = "Password cannot be empty"
                    hasError = true
                } else if (password.length < 8) {
                    passwordError = "Password must be at least 8 characters"
                    hasError = true
                }
                
                if (confirmPassword != password) {
                    confirmPasswordError = "Passwords do not match"
                    hasError = true
                }
                
                if (!hasError) {
                    shouldAttemptSignUp = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Create Account")
            }
        }

        TextButton(onClick = onBackToSignIn) {
            Text("Already have an account? Sign In")
        }
    }
}

@Composable
fun SignInScreen(
    onSignInClick: (String, String) -> Unit,
    onSignUpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sign In",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { 
                email = it
                emailError = null
            },
            label = { Text("Email") },
            isError = emailError != null,
            supportingText = {
                if (emailError != null) {
                    Text(emailError!!)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { 
                password = it
                passwordError = null
            },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            isError = passwordError != null,
            supportingText = {
                if (passwordError != null) {
                    Text(passwordError!!)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                var hasError = false
                if (email.isEmpty()) {
                    emailError = "Email is required"
                    hasError = true
                }
                if (password.isEmpty()) {
                    passwordError = "Password is required"
                    hasError = true
                }
                if (!hasError) {
                    onSignInClick(email, password)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Sign In")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = onSignUpClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Don't have an account? Sign Up")
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    requestLocationPermission: () -> Unit,
    sharedPreferences: SharedPreferences
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Loading) }
    var userName by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        // Check if user is already logged in
        val savedEmail = sharedPreferences.getString("user_email", null)
        val savedPassword = sharedPreferences.getString("user_password", null)
        val savedName = sharedPreferences.getString("user_name", null)

        if (savedEmail != null && savedPassword != null && savedName != null) {
            // User is already logged in
            userName = savedName
            userEmail = savedEmail
            currentScreen = Screen.Welcome
        } else {
            // Show sign in screen
            currentScreen = Screen.SignIn
        }
    }

    when (currentScreen) {
        Screen.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        Screen.SignIn -> {
            SignInScreen(
                onSignInClick = { email, password ->
                    sharedPreferences.edit().apply {
                        putString("user_email", email)
                        putString("user_password", password)
                        putString("user_name", "Driver")
                        apply()
                    }
                    userName = "Driver"
                    userEmail = email
                    currentScreen = Screen.Welcome
                },
                onSignUpClick = {
                    currentScreen = Screen.SignUp
                },
                modifier = modifier
            )
        }
        Screen.SignUp -> {
            SignUpScreen(
                onSignUpClick = { name, email, password ->
                    sharedPreferences.edit().apply {
                        putString("user_email", email)
                        putString("user_password", password)
                        putString("user_name", name)
                        apply()
                    }
                    userName = name
                    userEmail = email
                    currentScreen = Screen.Welcome
                },
                onBackToSignIn = {
                    currentScreen = Screen.SignIn
                },
                modifier = modifier
            )
        }
        Screen.Welcome -> {
            HomeScreen(
                userName = userName,
                onNavigateToProfile = {
                    currentScreen = Screen.Profile
                },
                onNavigateToMap = {
                    currentScreen = Screen.Map
                },
                onLogout = {
                    sharedPreferences.edit().clear().apply()
                    currentScreen = Screen.SignIn
                },
                modifier = modifier
            )
        }
        Screen.Map -> {
            MapScreenWithMenu(
                onLogout = {
                    sharedPreferences.edit().clear().apply()
                    currentScreen = Screen.SignIn
                },
                onHome = {
                    currentScreen = Screen.Welcome
                },
                onProfile = {
                    currentScreen = Screen.Profile
                },
                modifier = modifier
            )
        }
        Screen.Profile -> {
            ProfileScreen(
                userName = userName,
                userEmail = userEmail,
                onBack = {
                    currentScreen = Screen.Welcome
                },
                onEditProfile = {
                    // TODO: Implement edit profile functionality
                },
                modifier = modifier
            )
        }
    }
}

enum class Screen {
    Loading, SignIn, SignUp, Welcome, Profile, Map
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    TowTruckDriverTheme {
        // Create a mock SharedPreferences for preview
        val mockSharedPreferences = object : SharedPreferences {
            override fun getAll(): MutableMap<String, Any> = mutableMapOf()
            override fun getString(key: String?, defValue: String?): String? = null
            override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
            override fun getInt(key: String?, defValue: Int): Int = defValue
            override fun getLong(key: String?, defValue: Long): Long = defValue
            override fun getFloat(key: String?, defValue: Float): Float = defValue
            override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
            override fun contains(key: String?): Boolean = false
            override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
                override fun putString(key: String?, value: String?): SharedPreferences.Editor = this
                override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
                override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
                override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
                override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
                override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
                override fun remove(key: String?): SharedPreferences.Editor = this
                override fun clear(): SharedPreferences.Editor = this
                override fun commit(): Boolean = true
                override fun apply() {}
            }
            override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
            override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        }

        MainScreen(
            requestLocationPermission = {},
            sharedPreferences = mockSharedPreferences
        )
    }
}

@Composable
fun ConnectivityCheckDialog(
    hasLocationPermission: Boolean,
    isLocationEnabled: Boolean,
    hasInternetAccess: Boolean,
    onDismiss: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenNetworkSettings: () -> Unit
) {
    val context = LocalContext.current
    val isConnected by NetworkConnectivityManager.isConnected.collectAsState()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Cannot Go Online",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Please ensure the following are enabled:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Internet connectivity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (hasInternetAccess && isConnected) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = "Internet Status",
                        tint = if (hasInternetAccess && isConnected) Color(0xFF006400) else Color.Red, // Dark green
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Internet Connection",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Location permission
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (hasLocationPermission) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = "Location Permission",
                        tint = if (hasLocationPermission) Color(0xFF006400) else Color.Red, // Dark green
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Location Permission",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Location services
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isLocationEnabled) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = "Location Services",
                        tint = if (isLocationEnabled) Color(0xFF006400) else Color.Red, // Dark green
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Location Services",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        !hasLocationPermission -> onRequestLocationPermission()
                        !isLocationEnabled -> onOpenLocationSettings()
                        !hasInternetAccess || !isConnected -> onOpenNetworkSettings()
                        else -> onDismiss()
                    }
                }
            ) {
                Text(
                    text = when {
                        !hasLocationPermission -> "Grant Permission"
                        !isLocationEnabled -> "Enable Location"
                        !hasInternetAccess || !isConnected -> "Network Settings"
                        else -> "OK"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ConnectivityStatusIndicator(
    hasLocationPermission: Boolean,
    isLocationEnabled: Boolean,
    hasInternetAccess: Boolean,
    userLocation: LatLng?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isConnected by NetworkConnectivityManager.isConnected.collectAsState()
    
    // Define ivory color scheme
    val ivoryColor = Color(0xFFFFF8DC) // Ivory background
    val darkIvoryColor = Color(0xFFF5F5DC) // Slightly darker ivory
    val textColor = Color(0xFF000000) // Black text for better visibility
    val accentColor = Color(0xFFDAA520) // Mustard accent
    val whiteSmokeColor = Color(0xFFF5F5F5) // White smoke for non-ivory areas
    
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = ivoryColor
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Internet connectivity status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (hasInternetAccess && isConnected) Icons.Default.SignalCellular4Bar else Icons.Default.SignalCellularOff,
                    contentDescription = "Internet Status",
                    tint = if (hasInternetAccess && isConnected) accentColor else Color(0xFFE57373),
                    modifier = Modifier.size(24.dp)
                )
                
                Text(
                    text = if (hasInternetAccess && isConnected) "Internet Connected" else "No Internet Access",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Location status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location Status",
                    tint = when {
                        !hasLocationPermission -> Color(0xFFE57373)
                        !isLocationEnabled -> Color(0xFFE57373)
                        userLocation == null -> Color(0xFF9E9E9E)
                        else -> accentColor
                    },
                    modifier = Modifier.size(24.dp)
                )
                
                Text(
                    text = when {
                        !hasLocationPermission -> "Location Permission Required"
                        !isLocationEnabled -> "Location Services Disabled"
                        userLocation == null -> "Getting Location..."
                        else -> "Location Active"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
            }
            
            // Overall status
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (canGoOnline(context)) accentColor else Color(0xFFE57373),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                
                Text(
                    text = if (canGoOnline(context)) "Ready to Go Online" else "Cannot Go Online",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

@Composable
fun ProfileScreen(
    userName: String,
    userEmail: String,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showImagePicker by remember { mutableStateOf(false) }
    var profileImage by remember { mutableStateOf<String?>(null) }
    
    // Mock data - in a real app, this would come from a database or API
    val driverData = remember {
        DriverProfileData(
            name = userName,
            email = userEmail,
            phone = "+1 (555) 123-4567",
            fleetName = "ABC Trucking Co.",
            driverId = "DRV-2024-001",
            licenseNumber = "CDL-123456789",
            licenseType = "CDL Class A",
            licenseExpiry = "2024-12-15",
            licenseImage = null,
            vehiclePlate = "ABC-1234",
            vehicleMake = "Freightliner",
            vehicleModel = "Cascadia 2022",
            vehicleVin = "1FUJA6CV12L123456",
            trailerPlate = "XYZ-5678",
            trailerType = "Flatbed"
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header with back button and title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = "Driver Profile",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onEditProfile) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Profile"
                )
            }
        }

        // Profile Header Section
        ProfileHeaderSection(
            driverData = driverData,
            profileImage = profileImage,
            onImageClick = { showImagePicker = true },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Personal Information Card
        PersonalInfoCard(
            driverData = driverData,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // License Information Card
        LicenseInfoCard(
            driverData = driverData,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Vehicle Details Card
        VehicleDetailsCard(
            driverData = driverData,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Image picker dialog (placeholder for future implementation)
    if (showImagePicker) {
        AlertDialog(
            onDismissRequest = { showImagePicker = false },
            title = { Text("Update Profile Photo") },
            text = { Text("Choose how you want to update your profile photo") },
            confirmButton = {
                TextButton(onClick = { showImagePicker = false }) {
                    Text("Camera")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImagePicker = false }) {
                    Text("Gallery")
                }
            }
        )
    }
}

@Composable
fun ProfileHeaderSection(
    driverData: DriverProfileData,
    profileImage: String?,
    onImageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Photo
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                    .clickable { onImageClick() },
                contentAlignment = Alignment.Center
            ) {
                if (profileImage != null) {
                    // TODO: Load actual image
                    Text(
                        text = driverData.name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile Photo",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Tap to add photo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Driver Name
            Text(
                text = driverData.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Professional Driver",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PersonalInfoCard(
    driverData: DriverProfileData,
    modifier: Modifier = Modifier
) {
    ProfileCard(
        title = "Personal Information",
        icon = Icons.Default.Person,
        modifier = modifier
    ) {
        ProfileInfoRow("Phone", driverData.phone)
        Spacer(modifier = Modifier.height(12.dp))
        ProfileInfoRow("Email", driverData.email)
        Spacer(modifier = Modifier.height(12.dp))
        ProfileInfoRow("Fleet", driverData.fleetName)
        Spacer(modifier = Modifier.height(12.dp))
        ProfileInfoRow("Driver ID", driverData.driverId)
    }
}

@Composable
fun LicenseInfoCard(
    driverData: DriverProfileData,
    modifier: Modifier = Modifier
) {
    val daysUntilExpiry = remember {
        // Calculate days until expiry (simplified for demo)
        val expiryDate = driverData.licenseExpiry
        // In a real app, you'd parse the date and calculate the difference
        45 // Mock value
    }

    ProfileCard(
        title = "License Information",
        icon = Icons.Default.Person,
        modifier = modifier
    ) {
        ProfileInfoRow("License Number", driverData.licenseNumber)
        Spacer(modifier = Modifier.height(12.dp))
        ProfileInfoRow("License Type", driverData.licenseType)
        Spacer(modifier = Modifier.height(12.dp))
        
        // Expiry date with color coding
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Expiry Date",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = driverData.licenseExpiry,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        daysUntilExpiry < 0 -> MaterialTheme.colorScheme.error
                        daysUntilExpiry < 30 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = FontWeight.Normal
                )
                if (daysUntilExpiry < 30) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Expiring Soon",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        // License Image Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "License Photo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            if (driverData.licenseImage != null) {
                // TODO: Show license image thumbnail
                Text(
                    text = "View",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { /* TODO: Show full image */ }
                )
            } else {
                Text(
                    text = "Add Photo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { /* TODO: Add license photo */ }
                )
            }
        }
    }
}

@Composable
fun VehicleDetailsCard(
    driverData: DriverProfileData,
    modifier: Modifier = Modifier
) {
    ProfileCard(
        title = "Vehicle Details",
        icon = Icons.Default.Person,
        modifier = modifier
    ) {
        // Truck Information
        Text(
            text = "Truck Information",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        ProfileInfoRow("License Plate", driverData.vehiclePlate)
        Spacer(modifier = Modifier.height(8.dp))
        ProfileInfoRow("Make & Model", "${driverData.vehicleMake} ${driverData.vehicleModel}")
        Spacer(modifier = Modifier.height(8.dp))
        ProfileInfoRow("VIN", driverData.vehicleVin)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Trailer Information
        Text(
            text = "Trailer Information",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        ProfileInfoRow("Trailer Plate", driverData.trailerPlate)
        Spacer(modifier = Modifier.height(8.dp))
        ProfileInfoRow("Trailer Type", driverData.trailerType)
    }
}

@Composable
fun ProfileCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Card Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Card Content
            content()
        }
    }
}

@Composable
fun ProfileInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

// Data class to hold driver profile information
data class DriverProfileData(
    val name: String,
    val email: String,
    val phone: String,
    val fleetName: String,
    val driverId: String,
    val licenseNumber: String,
    val licenseType: String,
    val licenseExpiry: String,
    val licenseImage: String?,
    val vehiclePlate: String,
    val vehicleMake: String,
    val vehicleModel: String,
    val vehicleVin: String,
    val trailerPlate: String,
    val trailerType: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userName: String,
    onNavigateToProfile: () -> Unit,
    onNavigateToMap: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTripTab by remember { mutableStateOf(TripTab.ACTIVE) }
    var showMenu by remember { mutableStateOf(false) }
    var showTransportRequest by remember { mutableStateOf(false) }
    // Add online status state here
    var isOnline by remember { mutableStateOf(false) }
    var selectedStatus by remember { mutableStateOf(DriverStatus.AVAILABLE) }
    var showJobCompletionDialog by remember { mutableStateOf(false) }
    // Add activeJob state
    var activeJob by remember { mutableStateOf<TripData?>(null) }
    var showLiveRouteMap by remember { mutableStateOf(false) }
    
    // Connectivity state
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var isLocationEnabled by remember { mutableStateOf(isLocationEnabled(context)) }
    var hasInternetAccess by remember { mutableStateOf(hasInternetAccess(context)) }
    var showConnectivityDialog by remember { mutableStateOf(false) }
    
    // Auto-switch to Active tab when there's an active job
    LaunchedEffect(activeJob) {
        if (activeJob != null) {
            selectedTripTab = TripTab.ACTIVE
        }
    }
    
    // Periodic check for connectivity status
    LaunchedEffect(Unit) {
        while (true) {
            hasLocationPermission = hasLocationPermission(context)
            isLocationEnabled = isLocationEnabled(context)
            hasInternetAccess = hasInternetAccess(context)
            delay(2000) // Check every 2 seconds
        }
    }
    
    // Mock tow truck request data
    val transportRequest = remember {
        TransportRequest(
            id = "TT-2024-001",
            customerName = "Sarah Johnson",
            customerRating = 4.6f,
            customerReviews = 85,
            customerPhone = "+1 (555) 987-6543",
            pickupAddress = "I-90 Exit 15, Chicago",
            pickupDistance = "1.8 miles away",
            dropoffAddress = "Shell Station, Main St",
            timeRequest = "URGENT - Stranded",
            scheduledTime = null,
            vehicleType = "Car",
            loadType = "Broken down vehicle",
            loadWeight = "3,200 lbs",
            specialNotes = "Battery Dead",
            pickupLatLng = LatLng(41.8781, -87.6298),
            dropoffLatLng = LatLng(41.8800, -87.6300),
            estimatedEarnings = 85.0
        )
    }
    
    // Mock data - in a real app, this would come from API/database
    val tripsData = remember {
        listOf(
            TripData(
                id = "1",
                pickup = "Miami, FL",
                dropoff = "Atlanta, GA",
                departureTime = "2024-01-10 10:00",
                estimatedArrival = "2024-01-11 16:00",
                truckInfo = "Freightliner #TRK-7890",
                estimatedEarnings = 650.0,
                status = TripStatus.COMPLETED,
                actualEarnings = 650.0
            ),
            TripData(
                id = "2",
                pickup = "Chicago, IL",
                dropoff = "Detroit, MI",
                departureTime = "2024-01-08 14:00",
                estimatedArrival = "2024-01-09 20:00",
                truckInfo = "Freightliner #TRK-7890",
                estimatedEarnings = 580.0,
                status = TripStatus.COMPLETED,
                actualEarnings = 580.0
            ),
            TripData(
                id = "3",
                pickup = "Seattle, WA",
                dropoff = "Portland, OR",
                departureTime = "2024-01-05 09:00",
                estimatedArrival = "2024-01-05 15:00",
                truckInfo = "Freightliner #TRK-7890",
                estimatedEarnings = 420.0,
                status = TripStatus.COMPLETED,
                actualEarnings = 420.0
            )
        )
    }
    
    var earningsData by remember {
        mutableStateOf(
            EarningsData(
                daily = 1250.0,
                weekly = 4850.0,
                monthly = 18200.0
            )
        )
    }

    // Sound notifier for tow requests
    val towRequestNotifier = remember { TowRequestNotifier(context) }
    
    // Simulate transport request after 3 seconds
    LaunchedEffect(Unit) {
        delay(3000)
        showTransportRequest = true
        // Play sound and vibrate when tow request appears
        towRequestNotifier.playTowRequestAlert()
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Section
            item {
                HomeHeader(
                    userName = userName,
                    onProfileClick = onNavigateToProfile,
                    onNotificationClick = { /* TODO: Show notifications */ },
                    onMenuClick = { showMenu = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Online Status Section
            item {
                OnlineStatusSection(
                    modifier = Modifier.fillMaxWidth(),
                    isOnline = isOnline,
                    onOnlineChange = { newOnlineStatus ->
                        if (newOnlineStatus) {
                            // Check if can go online
                            if (canGoOnline(context)) {
                                isOnline = true
                            } else {
                                // Show connectivity dialog
                                showConnectivityDialog = true
                            }
                        } else {
                            isOnline = false
                        }
                    },
                    selectedStatus = selectedStatus,
                    onStatusChange = { selectedStatus = it },
                    canGoOnline = canGoOnline(context)
                )
            }

            // Quick Actions
            item {
                QuickActionsSection(
                    onNavigateToMap = onNavigateToMap,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Earnings Summary
            item {
                EarningsSummaryCard(
                    earningsData = earningsData,
                    activeJob = activeJob,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Trips Section
            item {
                TripsSection(
                    trips = if (activeJob != null) listOf(activeJob!!) + tripsData else tripsData,
                    selectedTab = selectedTripTab,
                    onTabSelected = { selectedTripTab = it },
                    modifier = Modifier.fillMaxWidth(),
                    onCancelTrip = {
                        // Remove the earnings that were added when the job was accepted
                        activeJob?.let { job ->
                            earningsData = earningsData.copy(
                                daily = earningsData.daily - job.estimatedEarnings
                            )
                        }
                        activeJob = null
                        selectedStatus = DriverStatus.AVAILABLE
                    },
                    onViewRoute = {
                        showLiveRouteMap = true
                    }
                )
            }

            // Payment Methods
            item {
                PaymentMethodsCard(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Transport Request Overlay (only show if online)
        AnimatedVisibility(
            visible = showTransportRequest && isOnline,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            TransportRequestOverlay(
                request = transportRequest,
                onAccept = { negotiatedPrice ->
                    showTransportRequest = false
                    towRequestNotifier.stopAlert() // Stop the alert sound
                    selectedStatus = DriverStatus.BUSY
                    // Set activeJob with the negotiated price
                    activeJob = TripData(
                        id = transportRequest.id,
                        pickup = transportRequest.pickupAddress,
                        dropoff = transportRequest.dropoffAddress,
                        departureTime = "Now",
                        estimatedArrival = "TBD",
                        truckInfo = "Your Tow Truck",
                        estimatedEarnings = negotiatedPrice, // Use the negotiated price
                        status = TripStatus.ACTIVE
                    )
                    // Update earnings data to include the negotiated price
                    earningsData = earningsData.copy(
                        daily = earningsData.daily + negotiatedPrice
                    )
                    // Show live route map after a short delay to ensure location is available
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        kotlinx.coroutines.delay(500) // Small delay to ensure location is ready
                        showLiveRouteMap = true
                    }
                },
                onReject = {
                    showTransportRequest = false
                    towRequestNotifier.stopAlert() // Stop the alert sound
                    // TODO: Send rejection feedback
                },
                onTimeout = {
                    showTransportRequest = false
                    towRequestNotifier.stopAlert() // Stop the alert sound
                    // TODO: Auto-reject logic
                }
            )
        }

        // Live Route Map (Full Screen)
        AnimatedVisibility(
            visible = showLiveRouteMap,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            val userLocation by AppLocationManager.location.collectAsState(initial = null)
            val fallbackLocation = LatLng(41.8781, -87.6298) // Default location if user location not available
            
            LiveRouteMap(
                driverLocation = userLocation ?: fallbackLocation,
                customerLocation = transportRequest.pickupLatLng,
                negotiatedPrice = activeJob?.estimatedEarnings,
                onClose = { showLiveRouteMap = false },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Connectivity Check Dialog
        if (showConnectivityDialog) {
            ConnectivityCheckDialog(
                hasLocationPermission = hasLocationPermission,
                isLocationEnabled = isLocationEnabled,
                hasInternetAccess = hasInternetAccess,
                onDismiss = { showConnectivityDialog = false },
                onRequestLocationPermission = {
                    // This will be handled by the MainActivity
                    showConnectivityDialog = false
                },
                onOpenLocationSettings = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    showConnectivityDialog = false
                },
                onOpenNetworkSettings = {
                    context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                    showConnectivityDialog = false
                }
            )
        }
        
        // Job Completion Dialog
        if (showJobCompletionDialog) {
            AlertDialog(
                onDismissRequest = { showJobCompletionDialog = false },
                title = {
                    Text("Job Completed!")
                },
                text = {
                    Text("What would you like to do next?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // Move active job to completed trips with actual earnings
                            activeJob?.let { job ->
                                val completedJob = job.copy(
                                    status = TripStatus.COMPLETED,
                                    actualEarnings = job.estimatedEarnings
                                )
                                // In a real app, you would save this to a database
                                // For now, we'll just clear the active job
                            }
                            selectedStatus = DriverStatus.AVAILABLE
                            activeJob = null
                            showJobCompletionDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF006400) // Dark green
                        )
                    ) {
                        Text("Go Available")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            // Move active job to completed trips with actual earnings
                            activeJob?.let { job ->
                                val completedJob = job.copy(
                                    status = TripStatus.COMPLETED,
                                    actualEarnings = job.estimatedEarnings
                                )
                                // In a real app, you would save this to a database
                                // For now, we'll just clear the active job
                            }
                            isOnline = false
                            activeJob = null
                            showJobCompletionDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red
                        )
                    ) {
                        Text("Go Offline")
                    }
                }
            )
        }
    }

    // Menu Modal
    if (showMenu) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showMenu = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        showMenu = false
                        onNavigateToMap()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDAA520) // Goldenrod
                    )
                ) {
                    Text("Map View")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        showMenu = false
                        onNavigateToProfile()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDAA520) // Goldenrod
                    )
                ) {
                    Text("Profile")
                }
                // Show "Complete Job" button only when driver is busy
                if (selectedStatus == DriverStatus.BUSY) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            showMenu = false
                            showJobCompletionDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDAA520) // Goldenrod
                        )
                    ) {
                        Text("Complete Job")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        showMenu = false
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Logout")
                }
            }
        }
    }
}

@Composable
fun TransportRequestOverlay(
    request: TransportRequest,
    onAccept: (Double) -> Unit,
    onReject: () -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var timeRemaining by remember { mutableStateOf(30) }
    var isExpanded by remember { mutableStateOf(false) }
    var showNegotiateDialog by remember { mutableStateOf(false) }
    var negotiatedPrice by remember { mutableStateOf("") }
    var isNegotiating by remember { mutableStateOf(false) }
    var negotiationTimeRemaining by remember { mutableStateOf(30) }
    var negotiationResult by remember { mutableStateOf<NegotiationResult?>(null) }
    var finalPrice by remember { mutableStateOf(request.estimatedEarnings) }

    // Countdown timer
    LaunchedEffect(Unit) {
        while (timeRemaining > 0) {
            delay(1000)
            timeRemaining--
        }
        if (timeRemaining <= 0) {
            onTimeout()
        }
    }

    // Negotiation countdown timer with automatic response
    LaunchedEffect(isNegotiating) {
        if (isNegotiating) {
            // Simulate customer response after 5 seconds
            delay(5000)
            
            // Automatic acceptance/rejection logic
            val originalPrice = request.estimatedEarnings ?: 75.0
            val negotiatedPriceValue = negotiatedPrice.replace("$", "").toDoubleOrNull() ?: originalPrice
            
            // Customer accepts if the negotiated price is reasonable (within 20% of original)
            val isAccepted = negotiatedPriceValue <= originalPrice * 1.2 && negotiatedPriceValue >= originalPrice * 0.8
            
            if (isAccepted) {
                finalPrice = negotiatedPriceValue
                negotiationResult = NegotiationResult.ACCEPTED
                isNegotiating = false
                showNegotiateDialog = false
                // Accept with the negotiated price
                onAccept(negotiatedPriceValue)
            } else {
                negotiationResult = NegotiationResult.REJECTED
                isNegotiating = false
                showNegotiateDialog = false
                // Reject the request
                onReject()
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = { /* Prevent dismiss */ }) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .then(modifier),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFF8DC) // Ivory background
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Banner for Tow Request title
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFDAA520) // Mustard banner
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isNegotiating) "Negotiation" else "Tow Request",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White, // White text on mustard background
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isNegotiating) "${negotiationTimeRemaining}s" else "${timeRemaining}s",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if ((isNegotiating && negotiationTimeRemaining <= 10) || (!isNegotiating && timeRemaining <= 10)) Color.Red else Color.White // Red for urgent, white for normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isNegotiating) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF5F5F5) // White smoke background
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Your Offer: $${negotiatedPrice}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF000000) // Black text
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Waiting for customer response...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF000000) // Black text
                            )
                        }
                    }
                } else if (negotiationResult != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF5F5F5) // White smoke background
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (negotiationResult == NegotiationResult.ACCEPTED) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = "Negotiation Result",
                                tint = if (negotiationResult == NegotiationResult.ACCEPTED) Color(0xFF006400) else Color.Red, // Dark green
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (negotiationResult == NegotiationResult.ACCEPTED) "Offer Accepted!" else "Offer Rejected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (negotiationResult == NegotiationResult.ACCEPTED) Color(0xFF006400) else Color.Red // Dark green
                            )
                            if (negotiationResult == NegotiationResult.ACCEPTED) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Final Price: $${String.format("%.0f", finalPrice)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF000000) // Black text
                                )
                            }
                        }
                    }
                } else {
                    // Info blocks with proper geometry
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF5F5F5) // White smoke background
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Dropoff Location Block
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFFF8DC) // Ivory block
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = Color(0xFFDAA520), // Mustard icon
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Dropoff Location",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF000000) // Black text
                                        )
                                        Text(
                                            text = request.dropoffAddress,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFF000000) // Black text
                                        )
                                    }
                                }
                            }
                            
                            // Vehicle Type Block
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFFF8DC) // Ivory block
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color(0xFFDAA520), // Mustard icon
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Vehicle Type",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF000000) // Black text
                                        )
                                        Text(
                                            text = request.vehicleType,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFF000000) // Black text
                                        )
                                    }
                                }
                            }
                            
                            // Problem Block
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFFF8DC) // Ivory block
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color.Red, // Red icon for problem
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Problem",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Red // Red text for problem
                                        )
                                        Text(
                                            text = request.specialNotes,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFF000000) // Black text
                                        )
                                    }
                                }
                            }
                            
                            // Offer Amount Block
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFFF8DC) // Ivory block
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFDAA520), // Mustard icon
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (negotiationResult == NegotiationResult.ACCEPTED) "Final Price" else "Offer",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF000000) // Black text
                                        )
                                        Text(
                                            text = "$${String.format("%.0f", finalPrice)}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (negotiationResult == NegotiationResult.ACCEPTED) Color(0xFF006400) else Color(0xFFDAA520) // Dark green if accepted, mustard if not
                                        )
                                        if (negotiationResult == NegotiationResult.ACCEPTED) {
                                            Text(
                                                text = "Negotiated from $${String.format("%.0f", request.estimatedEarnings ?: 75.0)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF000000) // Black text
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Map snippet in a card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF5F5F5) // White smoke background
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Vehicle's Current Location",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF000000), // Black text
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(
                                    color = Color(0xFFF5F5F5), // White smoke background
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                )
                        ) {
                            val userLocation by AppLocationManager.location.collectAsState(initial = null)
                            GoogleMap(
                                modifier = Modifier.fillMaxSize(),
                                cameraPositionState = rememberCameraPositionState {
                                    position = CameraPosition.fromLatLngZoom(
                                        request.pickupLatLng,
                                        15f
                                    )
                                },
                                properties = MapProperties(
                                    mapType = MapType.NORMAL,
                                    isMyLocationEnabled = false
                                )
                            ) {
                                Marker(
                                    state = MarkerState(position = request.pickupLatLng),
                                    title = "Customer Location",
                                    snippet = request.pickupAddress,
                                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                                )
                                userLocation?.let { driverLocation ->
                                    Marker(
                                        state = MarkerState(position = driverLocation),
                                        title = "Your Location",
                                        snippet = "Driver",
                                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
                                    )
                                    Polyline(
                                        points = listOf(driverLocation, request.pickupLatLng),
                                        color = Color(0xFFDAA520), // Mustard color
                                        width = 8f
                                    )
                                }
                            }
                        }
                    }
                }

                                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons
                if (!isNegotiating) {
                Row(
                    modifier = Modifier
                            .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Red
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reject", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { onAccept(request.estimatedEarnings ?: 75.0) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF006400) // Dark green
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Accept", style = MaterialTheme.typography.bodySmall)
                    }
                }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { showNegotiateDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFDAA520) // Goldenrod
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Negotiate Price", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    AnimatedVisibility(
                        visible = isNegotiating,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF5F5F5) // White smoke background
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color(0xFFDAA520) // Mustard color
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Waiting for customer response...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF000000) // Black text
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Negotiate Price Dialog
    if (showNegotiateDialog) {
        AlertDialog(
            onDismissRequest = { showNegotiateDialog = false },
            title = {
                Text(
                    text = "Negotiate Price",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF000000) // Black text
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter your price offer:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF000000), // Black text
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Price guidance
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF5F5F5) // White smoke background
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Price Guidelines",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF000000) // Black text
                            )
                            Text(
                                text = "Original: $${String.format("%.0f", request.estimatedEarnings ?: 75.0)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF000000) // Black text
                            )
                            Text(
                                text = "Acceptable range: $${String.format("%.0f", (request.estimatedEarnings ?: 75.0) * 0.8)} - $${String.format("%.0f", (request.estimatedEarnings ?: 75.0) * 1.2)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF000000) // Black text
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = negotiatedPrice,
                        onValueChange = { negotiatedPrice = it },
                        label = { Text("Price (e.g., $90)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFDAA520), // Mustard
                            unfocusedBorderColor = Color(0xFFDAA520), // Mustard
                            focusedLabelColor = Color(0xFF000000), // Black
                            unfocusedLabelColor = Color(0xFF000000) // Black
                        )
                    )
                    
                    // Price validation
                    if (negotiatedPrice.isNotEmpty()) {
                        val originalPrice = request.estimatedEarnings ?: 75.0
                        val negotiatedPriceValue = negotiatedPrice.replace("$", "").toDoubleOrNull() ?: 0.0
                        val isValid = negotiatedPriceValue >= originalPrice * 0.8 && negotiatedPriceValue <= originalPrice * 1.2
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isValid) Icons.Default.Check else Icons.Default.Warning,
                                contentDescription = "Price Validation",
                                tint = if (isValid) Color(0xFF006400) else Color(0xFFFFA500), // Dark green
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isValid) "Price is reasonable" else "Price may be rejected",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isValid) Color(0xFF006400) else Color(0xFFFFA500) // Dark green
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (negotiatedPrice.isNotEmpty()) {
                            isNegotiating = true
                            negotiationTimeRemaining = 30
                            showNegotiateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDAA520) // Goldenrod
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text("Send Offer")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNegotiateDialog = false }
                ) {
                    Text("Cancel", color = Color(0xFF000000)) // Black text
                }
            }
        )
    }
}

@Composable
fun CustomerDetailsSection(
    request: TransportRequest,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Customer Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = request.customerName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${request.customerRating} (${request.customerReviews} reviews)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tap to call",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clickable { /* TODO: Make phone call */ }
                )
            }
        }
    }
}

@Composable
fun TripDetailsSection(
    request: TransportRequest,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Service Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Breakdown: ${request.pickupAddress}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "(${request.pickupDistance})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Destination: ${request.dropoffAddress}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Time: ${request.timeRequest}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun CargoDetailsSection(
    request: TransportRequest,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Vehicle Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Customer Vehicle: ${request.vehicleType}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Issue: ${request.loadType} (${request.loadWeight})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            
            if (request.specialNotes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Notes: ${request.specialNotes}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun MapPreviewSection(
    request: TransportRequest,
    modifier: Modifier = Modifier
) {
    var showNavigation by remember { mutableStateOf(false) }
    
    if (showNavigation) {
        NavigationScreen(
            request = request,
            onBackPressed = { showNavigation = false }
        )
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Breakdown Location",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Interactive map preview with navigation button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
                    .clickable { showNavigation = true },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap to Start Navigation",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Location: ${request.pickupAddress}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showNavigation = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Navigation")
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationScreen(
    request: TransportRequest,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentLocation by AppLocationManager.location.collectAsState()
    
    var routePolyline by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var estimatedDuration by remember { mutableStateOf("") }
    var estimatedDistance by remember { mutableStateOf("") }
    var trafficInfo by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    
    // Calculate route when location is available
    LaunchedEffect(currentLocation) {
        currentLocation?.let { driverLocation ->
            scope.launch {
                try {
                    // In a real app, you would use Google Directions API here
                    // For now, we'll create a simple route
                    val route = calculateRoute(driverLocation, request.pickupLatLng)
                    routePolyline = route
                    
                    // Mock traffic and duration data
                    estimatedDuration = "15 min"
                    estimatedDistance = "8.2 km"
                    trafficInfo = "Moderate traffic"
                    isLoading = false
                } catch (e: Exception) {
                    // Handle error
                    isLoading = false
                }
            }
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Google Maps with navigation
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(
                    currentLocation ?: request.pickupLatLng,
                    15f
                )
            },
            properties = MapProperties(
                isMyLocationEnabled = true,
                mapType = MapType.NORMAL
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = true,
                compassEnabled = true
            )
        ) {
            // Driver's current location marker
            currentLocation?.let { location ->
                Marker(
                    state = MarkerState(position = location),
                    title = "Your Location",
                    snippet = "Driver's current position"
                )
            }
            
            // Pickup location marker
            Marker(
                state = MarkerState(position = request.pickupLatLng),
                title = "Pickup Location",
                snippet = request.pickupAddress,
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            )
            
            // Route polyline
            if (routePolyline.isNotEmpty()) {
                Polyline(
                    points = routePolyline,
                    color = MaterialTheme.colorScheme.primary,
                    width = 8f
                )
            }
        }
        
        // Top navigation bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackPressed) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Navigation to Pickup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isLoading) {
                        Text(
                            text = "Calculating route...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "$estimatedDuration • $estimatedDistance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                IconButton(
                    onClick = {
                        // Launch Google Maps app for turn-by-turn navigation
                        val gmmIntentUri = android.net.Uri.parse(
                            "google.navigation:q=${request.pickupLatLng.latitude},${request.pickupLatLng.longitude}"
                        )
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        if (mapIntent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(mapIntent)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Open in Maps",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        // Trip details overlay at bottom
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Traffic and route info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Estimated Arrival",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = estimatedDuration,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Distance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = estimatedDistance,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Traffic info
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = trafficInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Customer info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = request.customerName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = request.pickupAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            // Make phone call to customer
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = android.net.Uri.parse("tel:${request.customerPhone}")
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Call Customer",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// Helper function to calculate route (simplified for demo)
private suspend fun calculateRoute(origin: LatLng, destination: LatLng): List<LatLng> {
    // In a real app, you would use Google Directions API
    // For now, return a simple straight line route
    return listOf(origin, destination)
}

@Composable
fun HomeHeader(
    userName: String,
    onProfileClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile Section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onProfileClick() }
        ) {
            // Profile Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Greeting and Name
            Column {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Action Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Notifications
            Box {
                IconButton(onClick = onNotificationClick) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                // Notification badge
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.error,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "3",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            }
            
            // Menu
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu"
                )
            }
        }
    }
}

@Composable
fun QuickActionsSection(
    onNavigateToMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.clickable { isExpanded = !isExpanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Compact header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Quick Actions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickActionButton(
                    icon = Icons.Default.Place,
                    label = "Map View",
                    onClick = onNavigateToMap
                )
                QuickActionButton(
                    icon = Icons.Default.Person,
                    label = "Schedule",
                    onClick = { /* TODO: Navigate to schedule */ }
                )
                QuickActionButton(
                    icon = Icons.Default.Person,
                    label = "History",
                    onClick = { /* TODO: Navigate to history */ }
                )
                }
            }
        }
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        }
    }
}

@Composable
fun EarningsSummaryCard(
    earningsData: EarningsData,
    activeJob: TripData? = null,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.clickable { isExpanded = !isExpanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Compact header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                        text = "Earnings Summary",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF000000) // Black text for better visibility
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                                            Text(
                        text = "$${String.format("%.0f", earningsData.daily)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF000000) // Black text for better visibility
                    )
                        // Show active job indicator
                        if (activeJob != null && activeJob.status == TripStatus.ACTIVE) {
                            Text(
                                text = "Active: $${String.format("%.0f", activeJob.estimatedEarnings)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF006400), // Dark green
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EarningsItem("Today", earningsData.daily)
                EarningsItem("This Week", earningsData.weekly)
                EarningsItem("This Month", earningsData.monthly)
                }
            }
        }
    }
}

@Composable
fun EarningsItem(
    period: String,
    amount: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$${String.format("%.0f", amount)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = period,
            style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
        }
    }
}

@Composable
fun TripsSection(
    trips: List<TripData>,
    selectedTab: TripTab,
    onTabSelected: (TripTab) -> Unit,
    modifier: Modifier = Modifier,
    onCancelTrip: (() -> Unit)? = null,
    onViewRoute: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.clickable { isExpanded = !isExpanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Compact header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                        Text(
                text = "Trips",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF000000) // Black text for better visibility
            )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeTripsCount = trips.filter { it.status == TripStatus.ACTIVE }.size
                    val hasActiveTrips = activeTripsCount > 0
                    
                    if (hasActiveTrips) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    
                    Text(
                        text = "${trips.filter { it.status == selectedTab.status }.size} ${selectedTab.title.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF000000) // Black text for better visibility
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Spacer(modifier = Modifier.height(12.dp))
            
            // Tab Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TripTab.values().forEach { tab ->
                    TripTabButton(
                        tab = tab,
                        isSelected = selectedTab == tab,
                        onClick = { onTabSelected(tab) }
                    )
                }
            }
            
                Spacer(modifier = Modifier.height(12.dp))
            
            // Trip Cards
            val filteredTrips = trips.filter { it.status == selectedTab.status }
            if (filteredTrips.isNotEmpty()) {
                Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredTrips.forEach { trip ->
                            TripCard(
                                trip = trip,
                                onCancel = if (trip.status == TripStatus.ACTIVE) onCancelTrip else null,
                                onViewRoute = if (trip.status == TripStatus.ACTIVE) onViewRoute else null
                            )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                            .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                                            Text(
                            text = "No ${selectedTab.title.lowercase()} trips",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF000000) // Black text for better visibility
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TripTabButton(
    tab: TripTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
        )
    ) {
        Text(
            text = tab.title,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun TripCard(
    trip: TripData,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    onViewRoute: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (trip.status == TripStatus.ACTIVE) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Status indicator for active trips
            if (trip.status == TripStatus.ACTIVE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "In Progress",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Route
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${trip.pickup} → ${trip.dropoff}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Date/Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                            text = if (trip.status == TripStatus.ACTIVE) "Started: ${trip.departureTime}" else "Departure: ${trip.departureTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF000000) // Black text for better visibility
                        )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Estimated arrival for active trips
            if (trip.status == TripStatus.ACTIVE && trip.estimatedArrival != "TBD") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                            text = "ETA: ${trip.estimatedArrival}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF000000) // Black text for better visibility
                        )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            // Truck Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                            text = trip.truckInfo,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF000000) // Black text for better visibility
                        )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Earnings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                                            Text(
                            text = if (trip.status == TripStatus.COMPLETED) "Paid:" else "Estimated:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF000000) // Black text for better visibility
                        )
                    // Show negotiated indicator for active trips
                    if (trip.status == TripStatus.ACTIVE && trip.estimatedEarnings != 85.0) {
                        Text(
                            text = "Negotiated Price",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF006400), // Dark green
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Text(
                    text = "$${String.format("%.0f", trip.actualEarnings ?: trip.estimatedEarnings)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // Action buttons for active trips
            if (trip.status == TripStatus.ACTIVE) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // View Route button
                    if (onViewRoute != null) {
                        Button(
                            onClick = onViewRoute,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDAA520) // Goldenrod
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View Route")
                        }
                    }
                    
                    // Cancel button
                    if (onCancel != null) {
                        Button(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cancel Trip")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentMethodsCard(
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.clickable { isExpanded = !isExpanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Compact header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                                Text(
                    text = "Payment Methods",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF000000) // Black text for better visibility
                )
                }
                
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            // Expanded content
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
            
            PaymentMethodItem(
                icon = Icons.Default.Person,
                title = "Direct Deposit",
                subtitle = "Chase Bank ****1234",
                isDefault = true
            )
            
                Spacer(modifier = Modifier.height(8.dp))
            
            PaymentMethodItem(
                icon = Icons.Default.Person,
                title = "PayPal",
                subtitle = "john.doe@email.com",
                isDefault = false
            )
            
                Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = { /* TODO: Add payment method */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Payment Method")
                }
            }
        }
    }
}

@Composable
fun PaymentMethodItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDefault: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (isDefault) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "Default",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// Data class for transport request
data class TransportRequest(
    val id: String,
    val customerName: String,
    val customerRating: Float,
    val customerReviews: Int,
    val customerPhone: String,
    val pickupAddress: String,
    val pickupDistance: String,
    val dropoffAddress: String,
    val timeRequest: String,
    val scheduledTime: String?,
    val vehicleType: String,
    val loadType: String,
    val loadWeight: String,
    val specialNotes: String,
    val pickupLatLng: LatLng,
    val dropoffLatLng: LatLng,
    val estimatedEarnings: Double = 75.0
)

// Data classes and enums
data class TripData(
    val id: String,
    val pickup: String,
    val dropoff: String,
    val departureTime: String,
    val estimatedArrival: String,
    val truckInfo: String,
    val estimatedEarnings: Double,
    val status: TripStatus,
    val actualEarnings: Double? = null
)

data class EarningsData(
    val daily: Double,
    val weekly: Double,
    val monthly: Double
)

enum class TripTab(val title: String, val status: TripStatus) {
    ACTIVE("Active", TripStatus.ACTIVE),
    COMPLETED("Completed", TripStatus.COMPLETED)
}

enum class TripStatus {
    UPCOMING, ACTIVE, COMPLETED
}

@Composable
fun OnlineStatusSection(
    modifier: Modifier = Modifier,
    isOnline: Boolean,
    onOnlineChange: (Boolean) -> Unit,
    selectedStatus: DriverStatus,
    onStatusChange: (DriverStatus) -> Unit,
    canGoOnline: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.clickable { isExpanded = !isExpanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Compact header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Driver Status",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isOnline) "Online" else "Offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF000000) // Black text for better visibility
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                // Connectivity status indicator
                if (!canGoOnline) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Connectivity Issue",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Check Requirements",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF000000) // Black text for better visibility
                        )
                    }
                }
            }
            
            // Expanded content
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Connectivity Status Message
                if (!canGoOnline) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Connectivity Issue",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cannot Go Online",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF000000) // Black text for better visibility
                                )
                                Text(
                                    text = "Internet and location services required",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF000000) // Black text for better visibility
                                )
                            }
                            TextButton(
                                onClick = { onOnlineChange(true) }
                            ) {
                                Text(
                                    text = "Check",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Online/Offline Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isOnline) "Online" else "Offline",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF000000) // Black text for better visibility
                        )
                        Text(
                            text = if (isOnline) "Receiving requests" else "Not available",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF000000) // Black text for better visibility
                        )
                    }
                    Switch(
                        checked = isOnline,
                        onCheckedChange = onOnlineChange,
                        enabled = canGoOnline || !isOnline, // Disable when online but no connectivity
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            disabledCheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledCheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
                
                // Status Options (only visible when online)
                if (isOnline) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Availability",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF000000), // Black text for better visibility
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    DriverStatus.values().forEach { status ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStatusChange(status) }
                                .padding(vertical = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedStatus == status) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedStatus == status,
                                    onClick = { onStatusChange(status) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = status.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selectedStatus == status) 
                                        MaterialTheme.colorScheme.onPrimaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class DriverStatus(val displayName: String) {
    AVAILABLE("Available for Immediate Dispatch"),
    BUSY("On Another Job (Visible but Busy)")
}

enum class NegotiationResult {
    ACCEPTED, REJECTED
}

@Composable
fun LiveRouteMap(
    driverLocation: LatLng,
    customerLocation: LatLng,
    negotiatedPrice: Double? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mapLoaded by remember { mutableStateOf(false) }
    
    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(
                    driverLocation,
                    12f
                )
            },
            properties = MapProperties(
                mapType = MapType.NORMAL,
                isMyLocationEnabled = false
            ),
            onMapLoaded = {
                mapLoaded = true
            }
        ) {
            // Driver location marker
            Marker(
                state = MarkerState(position = driverLocation),
                title = "Your Location",
                snippet = "Driver",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
            )
            
            // Customer location marker
            Marker(
                state = MarkerState(position = customerLocation),
                title = "Customer Location",
                snippet = "Pickup Point",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            )
            
            // Route polyline
            Polyline(
                points = listOf(driverLocation, customerLocation),
                color = MaterialTheme.colorScheme.primary,
                width = 8f
            )
        }
        
        // Close button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(
                    color = Color(0xFFF5F5F5), // White smoke background
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color(0xFF000000) // Black icon for better visibility
            )
        }
        
        // Route info card
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF5F5F5) // White smoke background
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "🚗 Live Route to Customer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF000000) // Black text for better visibility
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Distance",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF000000) // Black text for better visibility
                        )
                        Text(
                            text = "1.8 miles",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF000000) // Black text for better visibility
                        )
                    }
                    
                    Column {
                        Text(
                            text = "Est. Time",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF000000) // Black text for better visibility
                        )
                        Text(
                            text = "5 min",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF000000) // Black text for better visibility
                        )
                    }
                    
                    Column {
                        Text(
                            text = "Earnings",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF000000) // Black text for better visibility
                        )
                        Text(
                            text = "$${String.format("%.0f", negotiatedPrice ?: 85.0)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (negotiatedPrice != null && negotiatedPrice != 85.0) Color(0xFF006400) else MaterialTheme.colorScheme.primary // Dark green
                        )
                        // Show negotiated indicator if price was negotiated
                        if (negotiatedPrice != null && negotiatedPrice != 85.0) {
                            Text(
                                text = "Negotiated",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF006400), // Dark green
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        
        // Loading overlay if map not loaded
        if (!mapLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5).copy(alpha = 0.9f)), // White smoke background
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading map...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF000000) // Black text for better visibility
                    )
                }
            }
        }
    }
}