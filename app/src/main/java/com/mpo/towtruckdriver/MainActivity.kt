package com.mpo.towtruckdriver

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import android.location.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted
            }
            else -> {
                // No location access granted
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("TowTruckDriverPrefs", Context.MODE_PRIVATE)
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

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // Show permission rationale
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
}

@Composable
fun AppBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Tow Truck Driver",
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
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
        PasswordStrength.STRONG -> Color.Green
    }

    val text = when (strength) {
        PasswordStrength.WEAK -> "Weak"
        PasswordStrength.MEDIUM -> "Medium"
        PasswordStrength.STRONG -> "Strong"
    }

    Column(modifier = modifier) {
        LinearProgressIndicator(
            progress = when (strength) {
                PasswordStrength.WEAK -> 0.33f
                PasswordStrength.MEDIUM -> 0.66f
                PasswordStrength.STRONG -> 1f
            },
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

@Composable
fun MapScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var mapProperties by remember {
        mutableStateOf(
            MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapType = MapType.NORMAL
            )
        )
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val location = fusedLocationClient.lastLocation.await()
                location?.let {
                    userLocation = LatLng(it.latitude, it.longitude)
                }
            } catch (e: Exception) {
                // Handle location error
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            properties = mapProperties,
            cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(
                    userLocation ?: LatLng(0.0, 0.0),
                    15f
                )
            }
        ) {
            if (hasLocationPermission) {
                userLocation?.let { location ->
                    Marker(
                        state = MarkerState(position = location),
                        title = "Your Location"
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
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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

        Spacer(modifier = Modifier.height(32.dp))

        if (showLocationRequest && !hasLocationPermission) {
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

        if (showMap && hasLocationPermission) {
            MapScreen(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
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

    LaunchedEffect(Unit) {
        // Check if user is already logged in
        val savedEmail = sharedPreferences.getString("user_email", null)
        val savedPassword = sharedPreferences.getString("user_password", null)
        val savedName = sharedPreferences.getString("user_name", null)

        if (savedEmail != null && savedPassword != null && savedName != null) {
            // User is already logged in
            userName = savedName
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
                    // In a real app, you would validate credentials against a backend
                    // For demo, we'll just store the credentials
                    sharedPreferences.edit().apply {
                        putString("user_email", email)
                        putString("user_password", password)
                        putString("user_name", "Driver") // You might want to get this from the backend
                        apply()
                    }
                    userName = "Driver"
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
                    // In a real app, you would create an account on the backend
                    // For demo, we'll just store the credentials
                    sharedPreferences.edit().apply {
                        putString("user_email", email)
                        putString("user_password", password)
                        putString("user_name", name)
                        apply()
                    }
                    userName = name
                    currentScreen = Screen.Welcome
                },
                onBackToSignIn = {
                    currentScreen = Screen.SignIn
                },
                modifier = modifier
            )
        }
        Screen.Welcome -> {
            WelcomeScreen(
                userName = userName,
                onLocationPermissionGranted = requestLocationPermission,
                modifier = modifier
            )
        }
    }
}

enum class Screen {
    Loading, SignIn, SignUp, Welcome
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