// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

/** Speech-to-text services. Users bring their own API key for each. */
enum class SttProvider(val displayName: String, val defaultBaseUrl: String, val defaultModel: String, val keyRequired: Boolean = true) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-transcribe"),
    GROQ("Groq", "https://api.groq.com/openai/v1", "whisper-large-v3-turbo"),
    DEEPGRAM("Deepgram", "https://api.deepgram.com/v1", "nova-3"),
    // empty model sends AssemblyAI's best model with a fallback that covers more languages (e.g. Korean)
    ASSEMBLYAI("AssemblyAI", "https://api.assemblyai.com/v2", ""),
    // any server implementing OpenAI's /audio/transcriptions, e.g. a self-hosted Whisper
    CUSTOM_OPENAI("", "", "whisper-1", keyRequired = false);

    /** Uses the OpenAI-compatible request format */
    val isOpenAiCompatible get() = this == OPENAI || this == GROQ || this == CUSTOM_OPENAI
}

/** Services for the optional text cleanup after transcription. */
enum class LlmProvider(val displayName: String, val defaultBaseUrl: String, val defaultModel: String, val keyRequired: Boolean = true) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-6-luna"),
    ANTHROPIC("Anthropic (Claude)", "https://api.anthropic.com/v1", "claude-haiku-5-5"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.5-flash-lite"),
    GROQ("Groq", "https://api.groq.com/openai/v1", "openai/gpt-oss-20b"),
    // any server implementing OpenAI's /chat/completions, e.g. OpenRouter
    CUSTOM_OPENAI("", "", "", keyRequired = false);
}
