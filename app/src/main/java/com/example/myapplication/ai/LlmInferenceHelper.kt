package com.example.myapplication.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Wraps MediaPipe's LlmInference for a Gemma 2B .task model.
 *
 * Key change from a blocking helper: generateResponseStream() emits partial
 * text chunks as the model produces them (via LlmInferenceSession's async
 * generation callback), instead of blocking until the full response is done.
 * This is what lets the UI start speaking (TTS) before generation finishes.
 */
class LlmInferenceHelper(private val context: Context) {

    private var llmInference: LlmInference? = null
    private var session: LlmInferenceSession? = null
    private var initializedModelPath: String? = null

    /**
     * Initializes (or reuses) the engine for the given model path.
     * Safe to call before every request; it's a no-op if already initialized
     * with the same model path.
     */
    @Synchronized
    fun init(modelPath: String) {
        if (llmInference != null && initializedModelPath == modelPath) return

        close()

        val engineOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .build()

        val inference = LlmInference.createFromOptions(context, engineOptions)

        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(40)
            .setTopP(0.9f)
            .setTemperature(0.8f)
            .build()

        llmInference = inference
        session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
        initializedModelPath = modelPath
    }

    /**
     * Blocking, whole-response generation. Kept for callers that don't need
     * streaming (e.g. quick one-off queries).
     */
    fun generateResponse(prompt: String): String {
        val activeSession = session ?: throw IllegalStateException("Call init() first")
        activeSession.addQueryChunk(prompt)
        return activeSession.generateResponse()
    }

    /**
     * Streaming generation. Emits each partial-result chunk as MediaPipe
     * produces it, then closes the flow when generation is complete.
     *
     * Usage:
     *   llmHelper.generateResponseStream(prompt).collect { chunk -> ... }
     */
    fun generateResponseStream(prompt: String): Flow<String> = callbackFlow {
        val activeSession = session
            ?: throw IllegalStateException("Call init() before generating")

        activeSession.addQueryChunk(prompt)

        // MediaPipe's async API delivers (partialResult, done) callbacks on
        // a background thread as tokens are produced.
        activeSession.generateResponseAsync { partialResult, done ->
            trySend(partialResult)
            if (done) {
                close()
            }
        }

        awaitClose {
            // Nothing to cancel explicitly here; MediaPipe finishes the
            // in-flight generation. If you add a cancel() API on the
            // session in a future MediaPipe version, call it here.
        }
    }

    /**
     * Interrupts an in-flight generation. Call this on barge-in (user starts
     * speaking again while the model is still generating/speaking).
     *
     * NOTE: as of current MediaPipe LLM Inference APIs there is no first-class
     * "cancel generation" call on LlmInferenceSession. The practical approach
     * is: (1) immediately stop TTS playback so the user stops hearing it,
     * and (2) drop/ignore any further emissions from the current stream by
     * tracking a "generation id" in the caller and discarding stale ones.
     * If a future MediaPipe release adds session.cancel(), wire it in here.
     */
    fun close() {
        session?.close()
        llmInference?.close()
        session = null
        llmInference = null
        initializedModelPath = null
    }
}