// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.settings.SettingsDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Mic key -> record -> speech-to-text with the user's own API key -> optional AI cleanup -> commit. */
class VoiceInputController(private val ime: LatinIME) {
    private enum class State { IDLE, RECORDING, PROCESSING }

    private var state = State.IDLE
    private val recorder = AudioRecorder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var job: Job? = null
    private val hideStatus = Runnable { ime.showVoiceInputStatus(null) }

    /** Whether the mic key should do voice input here instead of switching to another IME. */
    val isEnabled get() = VoiceSettings.isEnabled(ime)

    fun onVoiceKey() {
        when (state) {
            State.IDLE -> start()
            State.RECORDING -> finishRecording()
            State.PROCESSING -> { } // wait for the result
        }
    }

    /** Drops any recording or pending request, e.g. when the input field goes away. */
    fun cancel() {
        if (state == State.IDLE) return
        job?.cancel()
        job = null
        recorder.release()
        state = State.IDLE
        showStatus(null)
    }

    fun onDestroy() {
        cancel()
        handler.removeCallbacksAndMessages(null)
    }

    private fun start() {
        if (ContextCompat.checkSelfPermission(ime, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(ime, R.string.voice_input_permission_needed, Toast.LENGTH_LONG).show()
            ime.launchSettings(SettingsDestination.VoiceInput)
            return
        }
        val language = RichInputMethodManager.getInstance().currentSubtypeLocale.language
        var missing = 0
        VoiceSettings.sttConfig(ime, language) { missing = it } ?: run {
            Toast.makeText(ime, ime.getString(R.string.voice_input_not_configured, ime.getString(missing)), Toast.LENGTH_LONG).show()
            ime.launchSettings(SettingsDestination.VoiceInput)
            return
        }
        try {
            recorder.start { handler.post { if (state == State.RECORDING) finishRecording() } }
        } catch (e: Exception) {
            recorder.release()
            showError(e.message ?: e.javaClass.simpleName)
            return
        }
        state = State.RECORDING
        showStatus(R.string.voice_input_listening)
    }

    private fun finishRecording() {
        val wav = recorder.stop()
        if (AudioRecorder.durationMillis(wav) < MIN_DURATION_MILLIS) {
            state = State.IDLE
            showStatus(null)
            return
        }
        // read config again: settings may have changed while recording
        val language = RichInputMethodManager.getInstance().currentSubtypeLocale.language
        val sttConfig = VoiceSettings.sttConfig(ime, language) { }
        if (sttConfig == null) {
            state = State.IDLE
            showStatus(null)
            return
        }
        val llmConfig = VoiceSettings.llmConfig(ime)
        state = State.PROCESSING
        showStatus(R.string.voice_input_transcribing)
        job = scope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { SpeechToText.transcribe(sttConfig, wav) }
                var text = raw
                var polishError: String? = null
                if (llmConfig != null && raw.isNotEmpty()) {
                    showStatus(R.string.voice_input_polishing)
                    try {
                        text = withContext(Dispatchers.IO) { TextPolisher.polish(llmConfig, raw) }.ifEmpty { raw }
                    } catch (e: VoiceInputException) {
                        // never lose the dictation because the optional cleanup failed
                        polishError = e.message
                    }
                }
                state = State.IDLE
                if (text.isNotEmpty()) commit(text)
                if (polishError != null) showError(ime.getString(R.string.voice_input_polish_failed, polishError))
                else showStatus(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "voice input failed", e)
                state = State.IDLE
                showError(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun commit(text: String) {
        val before = ime.currentInputConnection?.getTextBeforeCursor(1, 0)
        val needsSpace = Settings.getValues().mSpacingAndPunctuations.mCurrentLanguageHasSpaces
                && !before.isNullOrEmpty() && !before.last().isWhitespace() && text.first().isLetterOrDigit()
        ime.onTextInput(if (needsSpace) " $text" else text)
    }

    private fun showError(message: String) {
        // no showStatus(null) first: hiding posts a suggestion refresh that would cover the error
        handler.removeCallbacks(hideStatus)
        ime.showVoiceInputStatus(statusView(ime.getString(R.string.voice_input_error, message)))
        handler.postDelayed(hideStatus, ERROR_DISPLAY_MILLIS)
    }

    private fun showStatus(textId: Int?) {
        handler.removeCallbacks(hideStatus)
        ime.showVoiceInputStatus(textId?.let { statusView(ime.getString(it)) })
    }

    // always a single line, ellipsized, so the strip never grows or looks crammed
    private fun statusView(text: String): View = TextView(ime).apply {
        this.text = text
        // fill the strip height, otherwise the wrapper pins the text to the top
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        gravity = Gravity.CENTER_VERTICAL
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(Settings.getValues().mColors.get(ColorType.SUGGESTED_WORD))
        val padding = (12 * resources.displayMetrics.density).toInt()
        setPadding(padding, 0, padding, 0)
        // tapping the status works even when the mic key isn't visible
        setOnClickListener { if (state == State.RECORDING) finishRecording() }
    }

    companion object {
        private const val TAG = "VoiceInputController"
        private const val MIN_DURATION_MILLIS = 300L
        private const val ERROR_DISPLAY_MILLIS = 5000L
    }
}
