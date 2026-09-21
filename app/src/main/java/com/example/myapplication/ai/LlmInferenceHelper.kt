package com.example.myapplication.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Helper class for MediaPipe LLM Inference.
 */
class LlmInferenceHelper(private val context: Context) : AutoCloseable {

    private var llmInference: LlmInference? = null

    /**
     * Initializes the LLM engine with the provided .task model path.
     * @param modelPath The absolute path to the .task model file.
     */
    fun init(modelPath: String) {
        val options = LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .build()

        llmInference = LlmInference.createFromOptions(context, options)
    }

    /**
     * Runs a text prompt synchronously.
     * Should be called from a background thread.
     */
    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val inference = llmInference ?: throw IllegalStateException("LlmInference not initialized")
        return@withContext try {
            inference.generateResponse(prompt)
        } catch (e: Exception) {
            "Error generating response: ${e.message}"
        }
    }

    override fun close() {
        llmInference?.close()
        llmInference = null
    }
}
