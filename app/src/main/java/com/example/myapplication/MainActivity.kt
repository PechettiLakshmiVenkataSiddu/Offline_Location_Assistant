package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myapplication.ai.LlmInferenceHelper
import com.example.myapplication.db.Address
import com.example.myapplication.db.AppDatabase
import com.example.myapplication.db.DatabaseInitializer
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var closestAddress by remember { mutableStateOf<Address?>(null) }

    LaunchedEffect(Unit) {
        DatabaseInitializer.initialize(context)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            LocationScreenContent(
                onLocationUpdated = { location ->
                    currentLocation = location
                },
                onClosestAddressFound = { address ->
                    closestAddress = address
                }
            )
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        }
        item {
            LlmTestContent(closestAddress = closestAddress)
        }
    }
}

@Composable
fun LocationScreenContent(
    onLocationUpdated: (Location) -> Unit = {},
    onClosestAddressFound: (Address?) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var locationInfo by remember { mutableStateOf("Fetching location...") }
    var closestAddressInfo by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        }
    )

    if (hasPermission) {
        LocationUpdates(fusedLocationClient) { location ->
            locationInfo = "Lat: ${location.latitude}, Long: ${location.longitude}"
            onLocationUpdated(location)

            // Query closest address
            coroutineScope.launch {
                val db = AppDatabase.getDatabase(context)
                val address = db.addressDao().getClosestAddress(location.latitude, location.longitude)
                closestAddressInfo = address?.let { "${it.street}, ${it.colony}, ${it.city}" }
                onClosestAddressFound(address)
            }
        }
    }

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "GPS Location",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = locationInfo,
            style = MaterialTheme.typography.bodyLarge
        )
        closestAddressInfo?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Closest Address in DB:",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (!hasPermission) {
            Button(
                onClick = {
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Grant Location Permission")
            }
        }
    }
}

@Composable
fun LlmTestContent(closestAddress: Address? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val llmHelper = remember { LlmInferenceHelper(context) }
    
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    
    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance?.language = Locale.US
            }
        }
        ttsInstance = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    var modelPath by remember { mutableStateOf("/data/local/tmp/model.task") }
    var prompt by remember { mutableStateOf("Hello, how are you?") }
    var response by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    var isListening by remember { mutableStateOf(false) }
    var audioPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            audioPermissionGranted = isGranted
        }
    )

    val runInference = {
        if (modelPath.isNotBlank() && !isLoading) {
            coroutineScope.launch {
                isLoading = true
                response = "Asking local guide..."
                try {
                    llmHelper.init(modelPath)
                    val result = llmHelper.generateResponse(prompt)
                    response = result
                    // Speak the response aloud
                    ttsInstance?.speak(result, TextToSpeech.QUEUE_FLUSH, null, null)
                } catch (e: Exception) {
                    response = "Error: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                response = "Listening..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(error: Int) {
                isListening = false
                response = "Speech error: $error"
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    prompt = matches[0]
                    runInference()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    LaunchedEffect(speechRecognizer) {
        speechRecognizer.setRecognitionListener(recognitionListener)
    }

    DisposableEffect(llmHelper, speechRecognizer) {
        onDispose {
            llmHelper.close()
            speechRecognizer.destroy()
        }
    }

    LaunchedEffect(closestAddress) {
        closestAddress?.let { address ->
            prompt = "You are a location assistant. The user's current location is: ${address.street}, ${address.colony}, ${address.city}. Write one short, friendly sentence confirming their location. Do not ask questions. Example format: You are near [location], a [description]. Now generate a similar sentence for the given location."
            runInference()
        }
    }

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MediaPipe LLM Test",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = modelPath,
            onValueChange = { modelPath = it },
            label = { Text("Model Path (.task)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Prompt") },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    if (audioPermissionGranted) {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        }
                        speechRecognizer.startListening(intent)
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Input",
                    tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { runInference() },
            enabled = !isLoading && modelPath.isNotBlank()
        ) {
            Text(if (isLoading) "Running..." else "Run Prompt")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Response:",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = response,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
fun LocationUpdates(
    fusedLocationClient: FusedLocationProviderClient,
    onLocationUpdated: (Location) -> Unit
) {
    val context = LocalContext.current
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    onLocationUpdated(it)
                }
            }
        }
    }

    DisposableEffect(fusedLocationClient) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // Handle case where permission was revoked
        }

        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        MainScreen()
    }
}
