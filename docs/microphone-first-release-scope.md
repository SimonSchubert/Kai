# Kai microphone-first release scope

Status: implementation target for review; not approved for merge or release.

## Product goal

Finish the existing Android speech-input path in Kai 3.3.0. Do not replace the application or restart the repository. Preserve existing working chat, model, and composer behavior.

## First-release behavior

1. The user taps the microphone in the existing chat composer.
2. Kai requests Android microphone permission when needed.
3. Kai records a bounded, record-then-transcribe utterance. The user taps Stop to finish capture.
4. Kai transcribes locally with the existing Moonshine tiny English model through sherpa-onnx. The first installation may download the model; recognition itself is offline.
5. In review mode, the recognized transcript is inserted into the existing composer at the current selection. The user can edit it and press Send.
6. An optional persistent setting, **Automatically send speech transcripts**, allows the recognized transcript to be sent to Kai without a second tap. It is OFF by default.
7. Automatic sending is deliberately limited to a clean composer: if the composer already contains text, has attached files, or Kai is already responding, the transcript is inserted for review instead. Empty transcripts and failed recognition are never sent.
8. Only one voice session may own the microphone. Leaving the foreground or disposing the control stops capture and cancels the operation. Captured PCM is cleared after use.

## UI constraints

- Preserve the existing chat composer and overall Kai layout.
- Paperclip remains on the left; microphone remains the rightmost composer control.
- Do not add a fourth composer button or status text beside the microphone.
- Keep the existing purple response-mode selector behavior unchanged.
- Keep the approved AI-service selector in the chat top bar, between speech output and Settings, with its existing dropdown and selection behavior.
- Keep the service selector in Interactive UI where that separate flow already uses it.

## Out of scope for this release

- Live/streaming word-by-word transcription.
- Always-on or background listening.
- Wake-word detection, continuous voice conversation, speaker identification, diarization, or advanced voice-agent orchestration.
- Replacing Moonshine/sherpa-onnx with another speech engine without evidence that the current engine cannot meet the requirements.
- Changes to unrelated Kai features.

## Privacy and licensing

- Keep audio capture and transcription on-device after the model is installed.
- Do not introduce a paid speech API, subscription, hidden cloud speech dependency, or telemetry of raw audio.
- Preserve upstream licenses and attribution for the speech runtime and model.
- Do not persist raw microphone PCM; clear in-memory samples after recognition.

## Review and release gates

- Source changes must be isolated on a dedicated branch and remain unmerged.
- Run relevant unit tests and the Android debug build.
- Inspect the resulting APK and test on the Samsung Galaxy A36.
- Verify first-run permission/model setup, record/stop/transcribe, transcript editing, default review mode, opt-in auto-send, safe fallback when drafts/files exist, cancellation, and failure handling.
- Do not call the work complete until build and device checks pass and the user has reviewed and authorized the result.
