// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

class SttConfig(
    val provider: SttProvider,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    /** ISO 639-1 code, or null to let the service detect the language */
    val language: String?,
)

object SpeechToText {
    // tried in order: universal-3-5-pro doesn't support Korean, universal-2 does
    private val ASSEMBLYAI_DEFAULT_MODELS = listOf("universal-3-5-pro", "universal-2")

    /** Transcribes 16 kHz mono WAV audio. Blocking, call off the main thread. */
    fun transcribe(config: SttConfig, wav: ByteArray): String {
        val text = when (config.provider) {
            SttProvider.DEEPGRAM -> parseDeepgram(deepgramRequest(config, wav).execute())
            SttProvider.ASSEMBLYAI -> transcribeAssemblyAi(config, wav)
            else -> parseOpenAi(openAiRequest(config, wav).execute())
        }
        return text.trim()
    }

    internal fun openAiRequest(config: SttConfig, wav: ByteArray): VoiceRequest {
        val gptTranscribe = config.model.startsWith("gpt-transcribe")
        val fields = linkedMapOf("model" to config.model)
        // gpt-transcribe documents no response_format values (it returns json), and takes a list of languages
        if (!gptTranscribe) fields["response_format"] = "json"
        config.language?.let { fields[if (gptTranscribe) "languages[]" else "language"] = it }
        val (contentType, body) = multipart(fields, "file", "audio.wav", "audio/wav", wav)
        val headers = mutableMapOf("Content-Type" to contentType)
        if (config.apiKey.isNotEmpty()) headers["Authorization"] = "Bearer ${config.apiKey}"
        return VoiceRequest("${config.baseUrl.trimEnd('/')}/audio/transcriptions", headers, body)
    }

    internal fun parseOpenAi(json: JSONObject) = json.getString("text")

    internal fun deepgramRequest(config: SttConfig, wav: ByteArray): VoiceRequest {
        val url = Uri.parse("${config.baseUrl.trimEnd('/')}/listen").buildUpon()
            .appendQueryParameter("smart_format", "true")
            .apply { if (config.model.isNotEmpty()) appendQueryParameter("model", config.model) }
            .apply {
                if (config.language == null) appendQueryParameter("detect_language", "true")
                else appendQueryParameter("language", config.language)
            }
            .build().toString()
        val headers = mapOf("Content-Type" to "audio/wav", "Authorization" to "Token ${config.apiKey}")
        return VoiceRequest(url, headers, wav)
    }

    internal fun parseDeepgram(json: JSONObject): String =
        json.getJSONObject("results").getJSONArray("channels").getJSONObject(0)
            .getJSONArray("alternatives").getJSONObject(0).getString("transcript")

    // AssemblyAI has no synchronous endpoint: upload, create a transcript job, then poll it
    private fun transcribeAssemblyAi(config: SttConfig, wav: ByteArray): String {
        val base = config.baseUrl.trimEnd('/')
        val auth = mapOf("Authorization" to config.apiKey)
        val uploadUrl = VoiceRequest("$base/upload", auth + ("Content-Type" to "application/octet-stream"), wav)
            .execute().getString("upload_url")
        val id = assemblyAiTranscriptRequest(config, uploadUrl).execute().getString("id")
        val deadline = System.currentTimeMillis() + 120_000
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(700)
            val json = VoiceRequest("$base/transcript/$id", auth, null).execute()
            parseAssemblyAi(json)?.let { return it }
        }
        throw VoiceInputException("AssemblyAI: timed out waiting for transcript")
    }

    internal fun assemblyAiTranscriptRequest(config: SttConfig, audioUrl: String): VoiceRequest {
        val body = JSONObject().put("audio_url", audioUrl)
        val models = if (config.model.isNotEmpty()) listOf(config.model) else ASSEMBLYAI_DEFAULT_MODELS
        body.put("speech_models", JSONArray(models))
        // a fixed language_code isn't documented to fall back between the default models (universal-3-5-pro lacks
        // e.g. Korean), so the default list detects the language, as AssemblyAI's docs show for that case
        if (config.language == null || config.model.isEmpty()) body.put("language_detection", true)
        else body.put("language_code", config.language)
        val headers = mapOf("Authorization" to config.apiKey, "Content-Type" to "application/json")
        return VoiceRequest("${config.baseUrl.trimEnd('/')}/transcript", headers, body.toString().toByteArray())
    }

    /** Returns the text once the job completed, null while it is still running. */
    internal fun parseAssemblyAi(json: JSONObject): String? = when (json.getString("status")) {
        "completed" -> if (json.isNull("text")) "" else json.getString("text")
        "error" -> throw VoiceInputException("AssemblyAI: ${json.optString("error")}")
        else -> null
    }
}
