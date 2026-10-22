// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class VoiceRequestsTest {
    private val wav = AudioRecorder.wav(ByteArray(3200))

    private fun stt(provider: SttProvider, key: String = "k", language: String? = "ko") =
        SttConfig(provider, provider.defaultBaseUrl.ifEmpty { "http://local/v1/" }, provider.defaultModel, key, language)

    @Test fun wavHeaderDescribes16kMonoPcm() {
        assertEquals(44 + 3200, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals(100, AudioRecorder.durationMillis(wav))
    }

    @Test fun openAiRequestIsMultipartWithLanguage() {
        val request = SpeechToText.openAiRequest(stt(SttProvider.OPENAI), wav)
        assertEquals("https://api.openai.com/v1/audio/transcriptions", request.url)
        assertEquals("Bearer k", request.headers["Authorization"])
        val body = String(request.body!!, Charsets.ISO_8859_1)
        assertTrue(body.contains("name=\"model\"\r\n\r\ngpt-transcribe\r\n"))
        assertTrue(body.contains("name=\"languages[]\"\r\n\r\nko\r\n"))
        assertFalse(body.contains("name=\"response_format\""))
        val whisper = String(SpeechToText.openAiRequest(stt(SttProvider.GROQ), wav).body!!, Charsets.ISO_8859_1)
        assertTrue(whisper.contains("name=\"language\"\r\n\r\nko\r\n"))
        assertTrue(body.contains("filename=\"audio.wav\""))
    }

    @Test fun keylessCustomServerSendsNoAuthAndNoLanguageWhenDetecting() {
        val request = SpeechToText.openAiRequest(stt(SttProvider.CUSTOM_OPENAI, key = "", language = null), wav)
        assertEquals("http://local/v1/audio/transcriptions", request.url)
        assertFalse(request.headers.containsKey("Authorization"))
        assertFalse(String(request.body!!, Charsets.ISO_8859_1).contains("name=\"language\""))
    }

    @Test fun deepgramRequestAndResponse() {
        val request = SpeechToText.deepgramRequest(stt(SttProvider.DEEPGRAM), wav)
        assertTrue(request.url.startsWith("https://api.deepgram.com/v1/listen?"))
        assertTrue(request.url.contains("model=nova-3") && request.url.contains("language=ko"))
        assertEquals("Token k", request.headers["Authorization"])
        val detect = SpeechToText.deepgramRequest(stt(SttProvider.DEEPGRAM, language = null), wav)
        assertTrue(detect.url.contains("detect_language=true"))
        val json = JSONObject("""{"results":{"channels":[{"alternatives":[{"transcript":"안녕하세요"}]}]}}""")
        assertEquals("안녕하세요", SpeechToText.parseDeepgram(json))
    }

    @Test fun assemblyAiJobAndPolling() {
        val body = JSONObject(String(SpeechToText.assemblyAiTranscriptRequest(stt(SttProvider.ASSEMBLYAI), "u").body!!))
        assertEquals("u", body.getString("audio_url"))
        assertTrue(body.getBoolean("language_detection")) // default model list detects the language
        val fixed = JSONObject(String(SpeechToText.assemblyAiTranscriptRequest(
            SttConfig(SttProvider.ASSEMBLYAI, "u", "universal-2", "k", "ko"), "u").body!!))
        assertEquals("ko", fixed.getString("language_code"))
        assertEquals("""["universal-3-5-pro","universal-2"]""", body.getJSONArray("speech_models").toString())
        assertNull(SpeechToText.parseAssemblyAi(JSONObject("""{"status":"processing"}""")))
        assertEquals("hi", SpeechToText.parseAssemblyAi(JSONObject("""{"status":"completed","text":"hi"}""")))
        assertEquals("", SpeechToText.parseAssemblyAi(JSONObject("""{"status":"completed","text":null}""")))
        assertFailsWith<VoiceInputException> { SpeechToText.parseAssemblyAi(JSONObject("""{"status":"error","error":"bad"}""")) }
    }

    @Test fun openAiChatRequestAndResponse() {
        val config = LlmConfig(LlmProvider.OPENAI, LlmProvider.OPENAI.defaultBaseUrl, "m", "k", "p")
        val request = TextPolisher.openAiRequest(config, "text")
        assertEquals("https://api.openai.com/v1/chat/completions", request.url)
        val body = JSONObject(String(request.body!!))
        assertEquals("p", body.getJSONArray("messages").getJSONObject(0).getString("content"))
        assertEquals("text", body.getJSONArray("messages").getJSONObject(1).getString("content"))
        assertFalse(body.has("temperature"))
        assertFalse(body.has("reasoning_effort"))
        val luna = JSONObject(String(TextPolisher.openAiRequest(LlmConfig(LlmProvider.OPENAI, "u", "gpt-6-luna", "k", "p"), "t").body!!))
        assertEquals("none", luna.getString("reasoning_effort"))
        val oss = JSONObject(String(TextPolisher.openAiRequest(LlmConfig(LlmProvider.GROQ, "u", "openai/gpt-oss-20b", "k", "p"), "t").body!!))
        assertFalse(oss.getBoolean("include_reasoning"))
        assertEquals("ok", TextPolisher.parseOpenAi(JSONObject("""{"choices":[{"message":{"content":"ok"}}]}""")))
    }

    @Test fun anthropicRequestAndResponse() {
        val config = LlmConfig(LlmProvider.ANTHROPIC, LlmProvider.ANTHROPIC.defaultBaseUrl, "claude-opus-5-5", "k", "p")
        val request = TextPolisher.anthropicRequest(config, "text")
        assertEquals("https://api.anthropic.com/v1/messages", request.url)
        assertEquals("k", request.headers["x-api-key"])
        assertEquals("2023-06-01", request.headers["anthropic-version"])
        val body = JSONObject(String(request.body!!))
        assertEquals("p", body.getString("system"))
        assertEquals("default", body.getString("fallbacks"))
        val haiku = TextPolisher.anthropicRequest(LlmConfig(LlmProvider.ANTHROPIC, "u", "claude-haiku-5-5", "k", "p"), "t")
        assertFalse(JSONObject(String(haiku.body!!)).has("fallbacks")) // Haiku has no server-side fallback
        assertFalse(haiku.headers.containsKey("anthropic-beta"))
        // thinking and fallback blocks are skipped, only text is used
        val response = JSONObject("""{"stop_reason":"end_turn","content":[{"type":"thinking","thinking":""},{"type":"text","text":"Hello."}]}""")
        assertEquals("Hello.", TextPolisher.parseAnthropic(response))
        assertFailsWith<VoiceInputException> { TextPolisher.parseAnthropic(JSONObject("""{"stop_reason":"refusal","content":[]}""")) }
    }

    @Test fun errorMessagesAreExtracted() {
        assertEquals("Invalid key", errorMessage("""{"error":{"message":"Invalid key"}}"""))
        assertEquals("bad", errorMessage("""{"err_msg":"bad"}"""))
        assertEquals("plain", errorMessage("plain"))
    }
}
