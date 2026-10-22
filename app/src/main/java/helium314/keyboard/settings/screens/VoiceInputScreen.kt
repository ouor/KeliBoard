// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.permissions.PermissionsUtil
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.voice.LlmProvider
import helium314.keyboard.latin.voice.SttProvider
import helium314.keyboard.latin.voice.TextPolisher
import helium314.keyboard.latin.voice.VoiceSettings
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.TextInputDialog
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.SwitchPreference

@Composable
fun VoiceInputScreen(
    onClickBack: () -> Unit,
) {
    val prefs = LocalContext.current.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val enabled = prefs.getBoolean(Settings.PREF_VOICE_INPUT, Defaults.PREF_VOICE_INPUT)
    val polish = enabled && prefs.getBoolean(Settings.PREF_VOICE_POLISH, Defaults.PREF_VOICE_POLISH)
    val items = listOf(Settings.PREF_VOICE_INPUT) + (if (enabled) listOf(
        Settings.PREF_VOICE_STT_PROVIDER,
        Settings.PREF_VOICE_STT_API_KEY,
        Settings.PREF_VOICE_STT_BASE_URL,
        Settings.PREF_VOICE_STT_MODEL,
        Settings.PREF_VOICE_USE_KEYBOARD_LANGUAGE,
        Settings.PREF_VOICE_POLISH,
    ) else emptyList()) + (if (polish) listOf(
        Settings.PREF_VOICE_LLM_PROVIDER,
        Settings.PREF_VOICE_LLM_API_KEY,
        Settings.PREF_VOICE_LLM_BASE_URL,
        Settings.PREF_VOICE_LLM_MODEL,
        Settings.PREF_VOICE_LLM_PROMPT,
    ) else emptyList())
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_voice_input),
        settings = items
    )
}

fun createVoiceInputSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_VOICE_INPUT, R.string.voice_input_enable, R.string.voice_input_enable_summary) { setting ->
        val activity = LocalContext.current.getActivity() ?: return@Setting
        var granted by remember { mutableStateOf(PermissionsUtil.checkAllPermissionsGranted(activity, Manifest.permission.RECORD_AUDIO)) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted = it
            if (granted) {
                activity.prefs().edit { putBoolean(setting.key, true) }
                KeyboardSwitcher.getInstance().setThemeNeedsReload() // show the mic key
            }
        }
        SwitchPreference(setting, Defaults.PREF_VOICE_INPUT,
            allowCheckedChange = {
                if (it && !granted) {
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                    false
                } else true
            }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_VOICE_STT_PROVIDER, R.string.voice_input_stt_provider) { setting ->
        val custom = stringResource(R.string.voice_input_custom_provider)
        val items = SttProvider.entries.map { (if (it == SttProvider.CUSTOM_OPENAI) custom else it.displayName) to it.name }
        ListPreference(setting, items, Defaults.PREF_VOICE_STT_PROVIDER)
    },
    Setting(context, Settings.PREF_VOICE_STT_API_KEY, R.string.voice_input_api_key) { setting ->
        ApiKeyPreference(setting, VoiceSettings.sttProvider(LocalContext.current.prefs()))
    },
    Setting(context, Settings.PREF_VOICE_STT_BASE_URL, R.string.voice_input_base_url) { setting ->
        val provider = VoiceSettings.sttProvider(LocalContext.current.prefs())
        PerProviderTextPreference(setting, provider, provider.defaultBaseUrl, ::isValidUrl)
    },
    Setting(context, Settings.PREF_VOICE_STT_MODEL, R.string.voice_input_model) { setting ->
        val provider = VoiceSettings.sttProvider(LocalContext.current.prefs())
        PerProviderTextPreference(setting, provider, provider.defaultModel)
    },
    Setting(context, Settings.PREF_VOICE_USE_KEYBOARD_LANGUAGE,
        R.string.voice_input_use_keyboard_language, R.string.voice_input_use_keyboard_language_summary) {
        SwitchPreference(it, Defaults.PREF_VOICE_USE_KEYBOARD_LANGUAGE)
    },
    Setting(context, Settings.PREF_VOICE_POLISH, R.string.voice_input_polish, R.string.voice_input_polish_summary) {
        SwitchPreference(it, Defaults.PREF_VOICE_POLISH)
    },
    Setting(context, Settings.PREF_VOICE_LLM_PROVIDER, R.string.voice_input_llm_provider) { setting ->
        val custom = stringResource(R.string.voice_input_custom_provider)
        val items = LlmProvider.entries.map { (if (it == LlmProvider.CUSTOM_OPENAI) custom else it.displayName) to it.name }
        ListPreference(setting, items, Defaults.PREF_VOICE_LLM_PROVIDER)
    },
    Setting(context, Settings.PREF_VOICE_LLM_API_KEY, R.string.voice_input_api_key) { setting ->
        ApiKeyPreference(setting, VoiceSettings.llmProvider(LocalContext.current.prefs()))
    },
    Setting(context, Settings.PREF_VOICE_LLM_BASE_URL, R.string.voice_input_base_url) { setting ->
        val provider = VoiceSettings.llmProvider(LocalContext.current.prefs())
        PerProviderTextPreference(setting, provider, provider.defaultBaseUrl, ::isValidUrl)
    },
    Setting(context, Settings.PREF_VOICE_LLM_MODEL, R.string.voice_input_model) { setting ->
        val provider = VoiceSettings.llmProvider(LocalContext.current.prefs())
        PerProviderTextPreference(setting, provider, provider.defaultModel)
    },
    Setting(context, Settings.PREF_VOICE_LLM_PROMPT, R.string.voice_input_llm_prompt) { setting ->
        TextPreference(setting, setting.key, TextPolisher.DEFAULT_PROMPT, singleLine = false, showValue = false)
    },
)

private fun isValidUrl(url: String) = url.isBlank() || url.startsWith("https://") || url.startsWith("http://")

/** Base URL and model are stored per provider, so switching providers keeps each one's values. */
@Composable
private fun PerProviderTextPreference(setting: Setting, provider: Enum<*>, default: String, checkTextValid: (String) -> Boolean = { true }) =
    TextPreference(setting, VoiceSettings.perProvider(setting.key, provider), default, checkTextValid = checkTextValid)

@Composable
private fun TextPreference(
    setting: Setting,
    key: String,
    default: String,
    singleLine: Boolean = true,
    showValue: Boolean = true,
    checkTextValid: (String) -> Boolean = { true },
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val prefs = LocalContext.current.prefs()
    val value = prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: default
    Preference(
        name = setting.title,
        onClick = { showDialog = true },
        description = if (showValue) value.ifEmpty { null } else null
    )
    if (showDialog) {
        TextInputDialog(
            onDismissRequest = { showDialog = false },
            onConfirmed = { prefs.edit { if (it.isBlank() || it == default) remove(key) else putString(key, it.trim()) } },
            initialText = value,
            title = { Text(setting.title) },
            singleLine = singleLine,
            checkTextValid = checkTextValid,
            onNeutral = { prefs.edit { remove(key) }; showDialog = false },
            neutralButtonText = stringResource(R.string.button_default)
        )
    }
}

@Composable
private fun ApiKeyPreference(setting: Setting, provider: Enum<*>) {
    val ctx = LocalContext.current
    var showDialog by rememberSaveable { mutableStateOf(false) }
    // keys are in their own prefs file, so changes don't trigger recomposition by themselves
    var apiKey by remember(provider) { mutableStateOf(VoiceSettings.getApiKey(ctx, setting.key, provider)) }
    Preference(
        name = setting.title,
        onClick = { showDialog = true },
        description = if (apiKey.isEmpty()) stringResource(R.string.voice_input_api_key_not_set)
            else "••••" + apiKey.takeLast(4)
    )
    if (showDialog) {
        TextInputDialog(
            onDismissRequest = { showDialog = false },
            onConfirmed = {
                VoiceSettings.setApiKey(ctx, setting.key, provider, it)
                apiKey = it.trim()
            },
            title = { Text(setting.title) },
            description = { Text(stringResource(R.string.voice_input_api_key_info)) },
            checkTextValid = { true }, // empty removes the key
        )
    }
}
