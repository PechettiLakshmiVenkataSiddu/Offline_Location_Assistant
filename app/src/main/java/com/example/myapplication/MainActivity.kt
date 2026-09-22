package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    color = Color(0xFF09090B),
                    modifier = Modifier.fillMaxSize()
                ) {
                    MainScreen()
                }
            }
        }
    }
}

enum class AssistantState {
    IDLE, LISTENING, THINKING, SPEAKING
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var closestAddress by remember { mutableStateOf<Address?>(null) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        }
    )

    LaunchedEffect(Unit) {
        DatabaseInitializer.initialize(context)
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    if (hasLocationPermission) {
        LocationUpdates(fusedLocationClient) { location ->
            currentLocation = location
            coroutineScope.launch {
                val db = AppDatabase.getDatabase(context)
                val address = db.addressDao().getClosestAddress(location.latitude, location.longitude)
                closestAddress = address
            }
        }
    }

    VoiceAssistantScreen(
        closestAddress = closestAddress,
        modifier = modifier
    )
}

/**
 * Splits a running text buffer into "ready to speak" sentences plus
 * whatever incomplete fragment remains.
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
fun OrbView(
    state: AssistantState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbTransition")

    val durationMs = when (state) {
        AssistantState.IDLE -> 2400
        AssistantState.LISTENING -> 800
        AssistantState.THINKING -> 400
        AssistantState.SPEAKING -> 600
    }

    val (minScale, maxScale) = when (state) {
        AssistantState.IDLE -> 0.95f to 1.05f
        AssistantState.LISTENING -> 0.90f to 1.10f
        AssistantState.THINKING -> 0.85f to 1.15f
        AssistantState.SPEAKING -> 0.92f to 1.12f
    }

    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "OrbScale"
    )

    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "OrbAlpha"
    )

    val colors = when (state) {
        AssistantState.IDLE -> listOf(
            Color(0xFF818CF8), // Soft Indigo
            Color(0xFF3B82F6), // Blue
            Color(0xFF1E1B4B)  // Deep Violet
        )
        AssistantState.LISTENING -> listOf(
            Color(0xFF22D3EE), // Bright Cyan
            Color(0xFF3B82F6), // Blue
            Color(0xFF083344)  // Dark Cyan
        )
        AssistantState.THINKING -> listOf(
            Color(0xFFC084FC), // Light Purple
            Color(0xFFE879F9), // Pink
            Color(0xFF3B0764)  // Deep Magenta
        )
        AssistantState.SPEAKING -> listOf(
            Color(0xFF38BDF8), // Sky Blue
            Color(0xFFA855F7), // Purple
            Color(0xFF0284C7)  // Deep Sky
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(180.dp)
    ) {
        // Outer glowing aura
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale * 1.25f)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors[0].copy(alpha = 0.35f * alphaPulse),
                        colors[1].copy(alpha = 0.15f * alphaPulse),
                        Color.Transparent
                    )
                )
            )
        }

        // Inner glowing core orb
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors[0].copy(alpha = alphaPulse),
                        colors[1],
                        colors[2]
                    )
                )
            )
        }
    }
}

@Composable
fun VoiceAssistantScreen(
    closestAddress: Address? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val llmHelper = remember { LlmInferenceHelper(context) }
    val currentAddress by rememberUpdatedState(closestAddress)

    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    var currentGenerationId by remember { mutableStateOf<String?>(null) }
    val conversationHistory = remember { mutableListOf<Pair<String, String>>() }

    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance?.let { instance ->
                    val indianLocale = Locale("en", "IN")
                    val result = instance.setLanguage(indianLocale)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        instance.language = Locale.US
                    }
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

    // Hidden from UI, kept working in code
    val modelPath = remember { "/data/local/tmp/model.task" }
    var prompt by remember { mutableStateOf("") }
    var typedInput by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
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

    fun interruptAssistant() {
        ttsInstance?.stop()
        isSpeaking = false
        currentGenerationId = null
    }

    fun speakSentence(sentence: String, generationId: String, utteranceId: String) {
        if (sentence.isBlank()) return
        if (currentGenerationId != generationId) return
        ttsInstance?.speak(sentence, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    DisposableEffect(ttsInstance, ttsReady) {
        val instance = ttsInstance
        if (instance != null && ttsReady) {
            instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                }
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                }
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                }
            })
        }
        onDispose { }
    }

    fun runInferenceStreaming(inputPrompt: String, addToHistory: Boolean = true) {
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
                    if (currentGenerationId != generationId) return@collect
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
                if (currentGenerationId == generationId && buffer.isNotBlank()) {
                    speakSentence(buffer.trim(), generationId, "utt_${generationId}_${sentenceIndex++}")
                }
                if (addToHistory && currentGenerationId == generationId) {
                    conversationHistory.add("user" to inputPrompt)
                    conversationHistory.add("assistant" to response)
                    while (conversationHistory.size > 12) conversationHistory.removeAt(0)
                }
            } catch (_: Exception) {
                response = "Unable to process request right now."
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

    fun handleUserQuery(userText: String) {
        if (userText.isBlank()) return

        if (userText.contains("emergency", ignoreCase = true) ||
            userText.contains("send my location", ignoreCase = true)
        ) {
            val address = currentAddress
            val message = if (address != null) {
                "EMERGENCY: I am near ${address.street}, ${address.colony}, ${address.city}."
            } else {
                "EMERGENCY: Location unavailable."
            }
            val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:8309036268")
                putExtra("sms_body", message)
            }
            try {
                context.startActivity(smsIntent)
            } catch (_: Exception) {}
            response = "Opening emergency SMS..."
            return
        }

        if (userText.contains("how far", ignoreCase = true) ||
            userText.contains("distance to", ignoreCase = true)
        ) {
            coroutineScope.launch {
                val db = AppDatabase.getDatabase(context)
                val targetName = userText
                    .replace("how far is", "", ignoreCase = true)
                    .replace("how far to", "", ignoreCase = true)
                    .replace("distance to", "", ignoreCase = true)
                    .trim()
                val target = db.addressDao().findByName(targetName)
                val current = currentAddress
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
            }
            return
        }

        val address = currentAddress
        if (address == null) {
            response = "Location is not yet available."
            val genId = UUID.randomUUID().toString()
            currentGenerationId = genId
            speakSentence("Location is not yet available.", genId, "utt_$genId")
        } else {
            val fullPrompt = buildPromptWithHistory(address, userText)
            runInferenceStreaming(fullPrompt)
        }
    }

    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }
            override fun onBeginningOfSpeech() {
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
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    prompt = recognizedText
                    handleUserQuery(recognizedText)
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

    val assistantState = when {
        isListening -> AssistantState.LISTENING
        isLoading -> AssistantState.THINKING
        isSpeaking -> AssistantState.SPEAKING
        else -> AssistantState.IDLE
    }

    val locationText = remember(closestAddress) {
        closestAddress?.let {
            if (it.colony.isNotBlank()) "${it.colony}, ${it.city}" else "${it.street}, ${it.city}"
        } ?: "Locating..."
    }

    val statusText = when {
        isListening -> "Listening..."
        isLoading -> "Thinking..."
        response.isNotBlank() -> response
        else -> "Tap to speak"
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF09090B))
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Top Location Bar
        Text(
            text = locationText,
            color = Color(0xFFA1A1AA),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        )

        // Center Content: Orb + Status/Response
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 120.dp)
        ) {
            OrbView(state = assistantState)

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .verticalScroll(scrollState),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = statusText,
                    color = if (assistantState == AssistantState.IDLE && response.isBlank()) Color(0xFFA1A1AA) else Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Bottom Bar Area: Text Fallback + Mic Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            // Typing Fallback Text Field
            Surface(
                color = Color(0xFF18181B),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    TextField(
                        value = typedInput,
                        onValueChange = { typedInput = it },
                        placeholder = {
                            Text(
                                "Type a message...",
                                color = Color(0xFFA1A1AA)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (typedInput.isNotBlank()) {
                                    val text = typedInput
                                    typedInput = ""
                                    handleUserQuery(text)
                                }
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            if (typedInput.isNotBlank()) {
                                val text = typedInput
                                typedInput = ""
                                handleUserQuery(text)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }
            }

            // Bottom Mic Pill
            Surface(
                color = Color(0xFF18181B),
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Surface(
                        color = Color.White,
                        shape = CircleShape,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .size(64.dp)
                            .scale(if (isListening) 1.05f else 1.0f)
                    ) {
                        IconButton(
                            onClick = {
                                if (isListening) {
                                    speechRecognizer.stopListening()
                                    isListening = false
                                } else {
                                    if (isSpeaking || isLoading) {
                                        interruptAssistant()
                                    }
                                    startListening()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Input",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
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
        } catch (_: SecurityException) {
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
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        MainScreen()
    }
}
