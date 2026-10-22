# KeliBoard

> **HeliBoard, refined.**

KeliBoard is a modern, actively maintained [HeliBoard](https://github.com/HeliBorg/HeliBoard) fork focused on stability, usability, and first-class language support.
It keeps the privacy-conscious, offline, and deeply customizable foundation of HeliBoard while fixing long-standing bugs and shipping practical improvements faster.

Korean support is a flagship feature, not the limit of the project. KeliBoard is built for everyone who wants a dependable, configurable Android keyboard.

[Download the latest APK](https://github.com/GPLaider/KeliBoard/releases/latest)

## Why KeliBoard?

- Active downstream maintenance of public HeliBoard issues
- Android 16 stability fixes and real-device regression testing
- UI and theme fixes that improve visibility and consistency
- Better punctuation, suggestion, popup-key, and emoji-search behavior
- A cleaner default look: flat rounded keys in your system colors, a tidy toolbar, and a visible language key
- First-class Korean support, including a Samsung-style Cheonjiin layout
- Works fully offline; the internet is only used by opt-in voice input with your own speech-to-text API key (OpenAI, Groq, Deepgram, AssemblyAI or any OpenAI-compatible server), with optional AI cleanup
- The customization and privacy model inherited from HeliBoard

## First-class Korean support

KeliBoard treats Korean as a complete input experience rather than a layout checkbox:

- Samsung-style four-column Cheonjiin layout
- Complete modern Cheonjiin vowel composition
- Correct consonant cycling, separators, compound finals, and syllable splitting
- Bundled Korean dictionary with corrected Hangul lookup
- Reliable Hangul composition after moving the cursor
- Korean-friendly punctuation behavior
- Double consonants prioritized on Dubeolsik long press
- Normal English QWERTY with Shift and Caps Lock on the lock screen
- Key labels sized and arranged for familiar Korean phone use

## A cleaner default look

New installs start with a calmer design inspired by [Fossify Keyboard](https://github.com/FossifyOrg/Keyboard):

- Flat rounded keys with no bevel; function keys match the letter keys and Enter uses a saturated accent with a white icon
- Dynamic (Material You) colors on Android 12 and later, light and dark themes on older versions
- A toolbar with only voice, clipboard and settings pinned next to the suggestions; the expand arrow opens just the editing tools (undo, redo, select word, copy, paste)
- A language key next to the space bar whenever more than one language is enabled (long-pressing space still opens the language list)
- A simpler clipboard view with a close button, title and trash can, and roomier clip cards

Every part stays customizable under Settings → Appearance and Toolbar: other styles and color themes, key borders, your own colors, key size and spacing, and toolbar keys.

## Voice input with your own API key

Voice input is off by default and is the only feature that uses the internet.

1. Open Settings → Voice input and turn it on (KeliBoard asks for microphone permission).
2. Pick a speech-to-text service and enter your API key. Supported: OpenAI, Groq, Deepgram, AssemblyAI, or any OpenAI-compatible server (for example a self-hosted Whisper).
3. Tap the microphone in the toolbar to start, then tap it or the status text to finish. The X button cancels.

Optionally, an AI model (OpenAI, Anthropic Claude, Google Gemini, Groq, or an OpenAI-compatible server) can clean up punctuation and filler words before the text is inserted. If the cleanup fails, the raw transcription is kept.

Recordings go only to the services you configure. API keys stay on the device and are excluded from settings backups, Android cloud backup and device transfer. Server URL and model can be changed per service.

## Core features

- Dictionaries for suggestions and spell checking
- Custom keyboard layouts, functional rows, symbols, and number layouts
- Theme, color, icon, and background-image customization
- Emoji search and inline emoji suggestions
- Multilingual typing
- Optional glide typing with a separately supplied compatible library
- Clipboard history
- Opt-in voice input with your own speech-to-text API key
- One-handed, split, floating, numpad, and D-pad modes
- Settings and learned-data backup and restore

See [layouts.md](layouts.md) for custom layout documentation. The upstream [HeliBoard wiki](https://github.com/HeliBorg/HeliBoard/wiki) remains useful for inherited features.

## Installation

Download the current build from [GitHub Releases](https://github.com/GPLaider/KeliBoard/releases/latest).

KeliBoard 4.0-ko-preview5 and later use the independent `io.github.gplaider.keliboard` application ID. They can coexist with official HeliBoard and earlier KeliBoard previews.

Android does not migrate app data between application IDs. Before switching, back up settings in the earlier app and restore that backup in KeliBoard.

## Reporting issues

Report KeliBoard bugs and feature requests in [KeliBoard Issues](https://github.com/GPLaider/KeliBoard/issues).

Please include:

- Android version and device model
- KeliBoard version
- Active language and layout
- Exact reproduction steps
- Expected and actual behavior
- A screenshot or screen recording when the problem is visual

One issue per problem keeps fixes reviewable and easier to verify.

## Project relationship

KeliBoard is a downstream fork of HeliBoard, which is based on OpenBoard and the AOSP LatinIME keyboard. Upstream history, licenses, and attribution are preserved.

Fixes suitable for the wider ecosystem may be contributed upstream, but KeliBoard does not wait for upstream release cadence before shipping verified user-facing fixes.

## Contributing

Code, translations, layouts, testing, and reproducible bug reports are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for the inherited technical guide; KeliBoard-specific contribution guidance will replace it as the project matures.

## License

KeliBoard is licensed under the [GNU General Public License v3.0](LICENSE). The repository also preserves the applicable [Apache 2.0](LICENSE-Apache-2.0) and [CC BY-SA 4.0](LICENSE-CC-BY-SA-4.0) notices inherited from its sources and artwork.

## Credits

- [HeliBoard](https://github.com/HeliBorg/HeliBoard) and its contributors
- [OpenBoard](https://github.com/openboard-team/openboard)
- [AOSP LatinIME](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/)
- LineageOS, Simple Keyboard, Indic Keyboard, and FlorisBoard contributors
- [Fossify Keyboard](https://github.com/FossifyOrg/Keyboard), which inspired the default key design
