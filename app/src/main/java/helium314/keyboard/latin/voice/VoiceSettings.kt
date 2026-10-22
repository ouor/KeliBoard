// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs

object VoiceSettings {
    // API keys live in their own file in credential-encrypted storage, so they are neither part of
    // the settings backup nor readable before the device is first unlocked
    private const val SECRETS_FILE = "voice_api_keys"

    private fun secrets(context: Context): SharedPreferences? = try {
        // same storage as protectedPrefs()
        context.applicationContext.getSharedPreferences(SECRETS_FILE, Context.MODE_PRIVATE)
    } catch (_: IllegalStateException) {
        null // user is locked
    }

    fun perProvider(baseKey: String, provider: Enum<*>) = "${baseKey}_${provider.name}"

    fun getApiKey(context: Context, baseKey: String, provider: Enum<*>): String =
        secrets(context)?.getString(perProvider(baseKey, provider), null) ?: ""

    fun setApiKey(context: Context, baseKey: String, provider: Enum<*>, key: String) {
        secrets(context)?.edit {
            if (key.isBlank()) remove(perProvider(baseKey, provider))
            else putString(perProvider(baseKey, provider), key.trim())
        }
    }

    fun isEnabled(context: Context) = context.prefs().getBoolean(Settings.PREF_VOICE_INPUT, Defaults.PREF_VOICE_INPUT)

    fun sttProvider(prefs: SharedPreferences) =
        enumOrDefault(prefs.getString(Settings.PREF_VOICE_STT_PROVIDER, null), SttProvider.valueOf(Defaults.PREF_VOICE_STT_PROVIDER))

    fun llmProvider(prefs: SharedPreferences) =
        enumOrDefault(prefs.getString(Settings.PREF_VOICE_LLM_PROVIDER, null), LlmProvider.valueOf(Defaults.PREF_VOICE_LLM_PROVIDER))

    fun sttBaseUrl(prefs: SharedPreferences, provider: SttProvider) =
        prefs.getString(perProvider(Settings.PREF_VOICE_STT_BASE_URL, provider), null)?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl

    fun sttModel(prefs: SharedPreferences, provider: SttProvider) =
        prefs.getString(perProvider(Settings.PREF_VOICE_STT_MODEL, provider), null)?.takeIf { it.isNotBlank() } ?: provider.defaultModel

    fun llmBaseUrl(prefs: SharedPreferences, provider: LlmProvider) =
        prefs.getString(perProvider(Settings.PREF_VOICE_LLM_BASE_URL, provider), null)?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl

    fun llmModel(prefs: SharedPreferences, provider: LlmProvider) =
        prefs.getString(perProvider(Settings.PREF_VOICE_LLM_MODEL, provider), null)?.takeIf { it.isNotBlank() } ?: provider.defaultModel

    /** Returns null if something required is missing; [missing] receives the title of the missing setting. */
    fun sttConfig(context: Context, keyboardLanguage: String, missing: (Int) -> Unit): SttConfig? {
        val prefs = context.prefs()
        val provider = sttProvider(prefs)
        val apiKey = getApiKey(context, Settings.PREF_VOICE_STT_API_KEY, provider)
        val baseUrl = sttBaseUrl(prefs, provider)
        val model = sttModel(prefs, provider)
        when {
            provider.keyRequired && apiKey.isEmpty() -> missing(R.string.voice_input_api_key)
            baseUrl.isEmpty() -> missing(R.string.voice_input_base_url)
            provider.isOpenAiCompatible && model.isEmpty() -> missing(R.string.voice_input_model)
            else -> {
                val useKeyboardLanguage = prefs.getBoolean(Settings.PREF_VOICE_USE_KEYBOARD_LANGUAGE, Defaults.PREF_VOICE_USE_KEYBOARD_LANGUAGE)
                return SttConfig(provider, baseUrl, model, apiKey, keyboardLanguage.takeIf { useKeyboardLanguage && it.isNotEmpty() })
            }
        }
        return null
    }

    /** Returns null if cleanup is disabled or not fully configured. */
    fun llmConfig(context: Context): LlmConfig? {
        val prefs = context.prefs()
        if (!prefs.getBoolean(Settings.PREF_VOICE_POLISH, Defaults.PREF_VOICE_POLISH)) return null
        val provider = llmProvider(prefs)
        val apiKey = getApiKey(context, Settings.PREF_VOICE_LLM_API_KEY, provider)
        val baseUrl = llmBaseUrl(prefs, provider)
        val model = llmModel(prefs, provider)
        if ((provider.keyRequired && apiKey.isEmpty()) || baseUrl.isEmpty() || model.isEmpty()) return null
        val prompt = prefs.getString(Settings.PREF_VOICE_LLM_PROMPT, null)?.takeIf { it.isNotBlank() } ?: TextPolisher.DEFAULT_PROMPT
        return LlmConfig(provider, baseUrl, model, apiKey, prompt)
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
