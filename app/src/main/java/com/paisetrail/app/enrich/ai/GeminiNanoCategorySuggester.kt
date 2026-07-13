package com.paisetrail.app.enrich.ai

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.collect

/**
 * On-device Gemini Nano via Android's AICore system service (ML Kit GenAI Prompt API — beta as of
 * this writing). Only usable on phones where AICore both exists AND has this feature enabled;
 * [ensureReady] is what [AiCategoryTagger] checks before ever calling [suggestCategory], and every
 * failure mode here (unsupported device, model not yet downloaded, a malformed response) just
 * returns null/false so the caller falls back to [LocalCategorySuggester] instead of crashing.
 */
@Singleton
class GeminiNanoCategorySuggester @Inject constructor() : CategorySuggester {
    private val model by lazy { Generation.getClient() }
    private var lastStatusDescription: String = "Not checked yet"

    override suspend fun ensureReady(): Boolean {
        return try {
            when (val status = model.checkStatus()) {
                FeatureStatus.AVAILABLE -> {
                    lastStatusDescription = "Gemini Nano is available on this device"
                    true
                }
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                    model.download().collect { }
                    val readyNow = model.checkStatus() == FeatureStatus.AVAILABLE
                    lastStatusDescription = if (readyNow) {
                        "Gemini Nano became available after downloading the model"
                    } else {
                        "Gemini Nano's model download did not complete"
                    }
                    readyNow
                }
                else -> {
                    lastStatusDescription = "Gemini Nano is not available on this device " +
                        "(AICore status: ${featureStatusName(status)})"
                    false
                }
            }
        } catch (e: Exception) {
            lastStatusDescription = "Gemini Nano check failed: ${e.message ?: e.javaClass.simpleName}"
            Log.w(TAG, "AICore not usable on this device", e)
            false
        }
    }

    override fun statusDescription(): String = lastStatusDescription

    private fun featureStatusName(status: Int): String = when (status) {
        FeatureStatus.AVAILABLE -> "AVAILABLE"
        FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE"
        FeatureStatus.DOWNLOADING -> "DOWNLOADING"
        FeatureStatus.UNAVAILABLE -> "UNAVAILABLE"
        else -> "UNKNOWN($status)"
    }

    override suspend fun suggestCategory(payeeName: String?, vpa: String?, categoryNames: List<String>): String? {
        val merchantText = listOfNotNull(payeeName, vpa).joinToString(" ").trim()
        if (merchantText.isBlank() || categoryNames.isEmpty()) return null

        return try {
            val response = model.generateContent(buildPrompt(merchantText, categoryNames))
            val answer = response.candidates.firstOrNull()?.text?.trim()
            if (answer == null) {
                lastStatusDescription = "Gemini Nano returned an empty response"
                return null
            }
            // The model is asked to answer with only a category name, but it's still a language
            // model, not a strict classifier — match loosely rather than requiring an exact echo.
            val match = categoryNames.firstOrNull { answer.contains(it, ignoreCase = true) }
            if (match == null) {
                lastStatusDescription = "Gemini Nano's answer (\"$answer\") didn't name a known category"
            }
            match
        } catch (e: GenAiException) {
            lastStatusDescription = "Gemini Nano request failed (code ${e.errorCode})"
            Log.w(TAG, "Gemini Nano request failed (code ${e.errorCode})", e)
            null
        } catch (e: Exception) {
            lastStatusDescription = "Gemini Nano request failed: ${e.message ?: e.javaClass.simpleName}"
            Log.w(TAG, "Gemini Nano request failed", e)
            null
        }
    }

    private fun buildPrompt(merchantText: String, categoryNames: List<String>): String =
        "You are categorizing a personal expense. Categories: ${categoryNames.joinToString(", ")}. " +
            "Transaction: \"$merchantText\". " +
            "Reply with exactly one category name from that list and nothing else."

    companion object {
        private const val TAG = "GeminiNanoCategorySuggester"
    }
}
