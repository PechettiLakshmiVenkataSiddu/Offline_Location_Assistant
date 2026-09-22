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
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            OfflineBadge()
        }
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
            VoiceAssistantContent(closestAddress = closestAddress)
        }
    }
}

@Composable
fun OfflineBadge() {
    Surface(
        color = Color(0xFF1B5E20),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            text = "\uD83D\uDD12 100% Offline — No Internet Used",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
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
        Text(text = "GPS Location", style = MaterialTheme.typography.titleLarge)
        Text(text = locationInfo, style = MaterialTheme.typography.bodyLarge)
        closestAddressInfo?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Closest Address in DB:", style = MaterialTheme.typography.titleMedium)
            Text(text = it, style = MaterialTheme.typography.bodyMedium)
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

/**
 * Splits a running text buffer into "ready to speak" sentences plus
 * whatever incomplete fragment remains. Lets us feed TTS sentence-by-sentence
 * as the LLM streams tokens, instead of waiting for the whole response.
 */
private fun extractCompleteSentences(buffer: String): Pair<List<String>, String> {
    val sentenceEndRegex = Regex("(?<=[.!?])\\s+")
    val parts = buffer.split(sentenceEndRegex)
    if (parts.size <= 1) return Pair(emptyList(), buffer)
    val complete = parts.dropLast(1).filter { it.isNotBlank() }
    val remainder = parts.last()
    return Pair(complete, remainder)
}

@Composable
fun VoiceAssistantContent(closestAddress: Address? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val llmHelper = remember { LlmInferenceHelper(context) }
    val currentAddress by rememberUpdatedState(closestAddress)

    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    // Bumped every time we start a new generation. Any stream emission whose
    // generation id no longer matches "current" is stale (superseded by a
    // barge-in) and gets dropped — our stand-in for real stream cancellation.
    var currentGenerationId by remember { mutableStateOf<String?>(null) }

    // Rolling chat history so replies stay conversational across turns.
    // Trimmed to keep prompts from growing unbounded.
    val conversationHistory = remember { mutableListOf<Pair<String, String>>() } // (role, text)

    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance?.let { instance ->
                    val indianLocale = Locale("en", "IN")
                    val result = instance.setLanguage(indianLocale)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        instance.language = Locale.US
                    }
                    // Prefer the best-quality installed voice for this locale
                    // rather than whatever the engine defaults to.
                    val bestVoice = instance.voices
                        ?.filter { it.locale == instance.language && !it.isNetworkConnectionRequired }
                        ?.maxByOrNull { it.quality }
                    bestVoice?.let { instance.voice = it }

                    instance.setPitch(1.0f)
                    instance.setSpeechRate(1.05f)
                    ttsReady = true
                }
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
    var isSpeaking by remember { mutableStateOf(false) }

    var isListening by remember { mutableStateOf(false) }
    // Continuous-listening toggle: when on, we auto-restart recognition
    // after every result/silence instead of requiring a tap each time.
    var continuousMode by remember { mutableStateOf(false) }

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
        onResult = { isGranted -> audioPermissionGranted = isGranted }
    )

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    fun startListening() {
        if (!audioPermissionGranted) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer.startListening(intent)
    }

    /**
     * Barge-in: called the moment the user starts speaking again. Stops any
     * in-progress TTS immediately and invalidates the current generation so
     * late-arriving stream chunks from the superseded request are ignored.
     */
    fun interruptAssistant() {
        ttsInstance?.stop()
        isSpeaking = false
        currentGenerationId = null
    }

    fun speakSentence(sentence: String, generationId: String, utteranceId: String) {
        if (sentence.isBlank()) return
        if (currentGenerationId != generationId) return // superseded, drop it
        ttsInstance?.speak(sentence, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    // Track TTS start/stop so we know when it's safe to auto-resume listening
    // in continuous mode, and so barge-in has something to stop.
    DisposableEffect(ttsInstance, ttsReady) {
        val instance = ttsInstance
        if (instance != null && ttsReady) {
            instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                }
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    if (continuousMode && !isListening) {
                        startListening()
                    }
                }
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                }
            })
        }
        onDispose { }
    }

    fun runInferenceStreaming(inputPrompt: String, addToHistory: Boolean = true) {
        if (modelPath.isBlank()) return
        val generationId = UUID.randomUUID().toString()
        currentGenerationId = generationId

        coroutineScope.launch {
            isLoading = true
            response = ""
            var buffer = ""
            var sentenceIndex = 0
            try {
                llmHelper.init(modelPath)
                llmHelper.generateResponseStream(inputPrompt).collect { chunk ->
                    if (currentGenerationId != generationId) return@collect // barge-in happened
                    buffer += chunk
                    response += chunk
                    val (completeSentences, remainder) = extractCompleteSentences(buffer)
                    if (completeSentences.isNotEmpty()) {
                        completeSentences.forEach { sentence ->
                            speakSentence(
                                sentence.trim(),
                                generationId,
                                "utt_${generationId}_${sentenceIndex++}"
                            )
                        }
                        buffer = remainder
                    }
                }
                // Speak whatever's left after the stream closes.
                if (currentGenerationId == generationId && buffer.isNotBlank()) {
                    speakSentence(buffer.trim(), generationId, "utt_${generationId}_${sentenceIndex++}")
                }
                if (addToHistory && currentGenerationId == generationId) {
                    conversationHistory.add("user" to inputPrompt)
                    conversationHistory.add("assistant" to response)
                    while (conversationHistory.size > 12) conversationHistory.removeAt(0)
                }
            } catch (e: Exception) {
                response = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun buildPromptWithHistory(address: Address?, userText: String): String {
        val systemContext = if (address != null) {
            "You are a friendly, natural-sounding Indian voice assistant, like a helpful local friend. " +
                    "The user's current location is: ${address.street}, ${address.colony}, ${address.city}. " +
                    "Reply in one or two short, natural, conversational sentences — warm and clear, not robotic. " +
                    "Avoid repeating coordinates. Just talk like a real person would."
        } else {
            "You are a friendly, natural-sounding Indian voice assistant. Reply in one or two short, " +
                    "conversational sentences."
        }
        val historyText = conversationHistory.takeLast(6).joinToString("\n") { (role, text) ->
            "${if (role == "user") "User" else "Assistant"}: $text"
        }
        return buildString {
            appendLine(systemContext)
            if (historyText.isNotBlank()) {
                appendLine("Recent conversation:")
                appendLine(historyText)
            }
            append("User: $userText")
        }
    }

    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }
            override fun onBeginningOfSpeech() {
                // User started talking — if the assistant is mid-reply, cut it off.
                if (isSpeaking || isLoading) {
                    interruptAssistant()
                }
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(error: Int) {
                isListening = false
                // In continuous mode, silence/no-match errors are normal —
                // just listen again rather than surfacing an error state.
                if (continuousMode) {
                    startListening()
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches.isNullOrEmpty()) {
                    if (continuousMode) startListening()
                    return
                }
                val recognizedText = matches[0]
                val address = currentAddress
                prompt = recognizedText

                if (recognizedText.contains("emergency", ignoreCase = true) ||
                    recognizedText.contains("send my location", ignoreCase = true)
                ) {
                    val message = if (address != null) {
                        "EMERGENCY: I am near ${address.street}, ${address.colony}, ${address.city}."
                    } else {
                        "EMERGENCY: Location unavailable."
                    }
                    val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("smsto:8309036268")
                        putExtra("sms_body", message)
                    }
                    context.startActivity(smsIntent)
                    response = "Opening emergency SMS..."
                    if (continuousMode) startListening()
                    return
                }

                if (recognizedText.contains("how far", ignoreCase = true) ||
                    recognizedText.contains("distance to", ignoreCase = true)
                ) {
                    coroutineScope.launch {
                        val db = AppDatabase.getDatabase(context)
                        val targetName = recognizedText
                            .replace("how far is", "", ignoreCase = true)
                            .replace("how far to", "", ignoreCase = true)
                            .replace("distance to", "", ignoreCase = true)
                            .trim()
                        val target = db.addressDao().findByName(targetName)
                        val current = address
                        val distText = if (target != null && current != null) {
                            val dist = haversineDistance(current.lat, current.lng, target.lat, target.lng)
                            "You are approximately %.1f kilometers from %s.".format(dist, target.colony)
                        } else {
                            "Sorry, I couldn't find that location in my data."
                        }
                        response = distText
                        val genId = UUID.randomUUID().toString()
                        currentGenerationId = genId
                        speakSentence(distText, genId, "utt_$genId")
                        if (continuousMode) startListening()
                    }
                    return
                }

                if (address == null) {
                    response = "Location is not yet available."
                    val genId = UUID.randomUUID().toString()
                    currentGenerationId = genId
                    speakSentence("Location is not yet available.", genId, "utt_$genId")
                    if (continuousMode) startListening()
                } else {
                    val fullPrompt = buildPromptWithHistory(address, recognizedText)
                    runInferenceStreaming(fullPrompt)
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
            val greetingPrompt = "You are a friendly, natural-sounding Indian voice assistant, like a " +
                    "helpful local friend. The user's current location is: ${address.street}, " +
                    "${address.colony}, ${address.city}. Greet them warmly and let them know where they " +
                    "are, in one or two natural conversational sentences. Do not ask questions. " +
                    "Do not repeat coordinates."
            runInferenceStreaming(greetingPrompt, addToHistory = false)
        }
    }

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Voice Assistant (Gemma 2B, streaming)", style = MaterialTheme.typography.titleLarge)
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
                    if (isListening) {
                        speechRecognizer.stopListening()
                    } else if (audioPermissionGranted) {
                        startListening()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Input",
                    tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val address = currentAddress
                    val fullPrompt = if (address != null) buildPromptWithHistory(address, prompt) else prompt
                    runInferenceStreaming(fullPrompt)
                },
                enabled = !isLoading && modelPath.isNotBlank()
            ) {
                Text(if (isLoading) "Running..." else "Run Prompt")
            }
            Button(
                onClick = {
                    continuousMode = !continuousMode
                    if (continuousMode && !isListening && !isSpeaking) {
                        startListening()
                    }
                }
            ) {
                Text(if (continuousMode) "Stop Conversation Mode" else "Start Conversation Mode")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Response:", style = MaterialTheme.typography.titleMedium)
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
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { onLocationUpdated(it) }
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

fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return R * c
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        MainScreen()
    }
}