# Offline speech input

## Scope

The first speech-input implementation is Android-only and English-only. It is a deliberate, foreground-only workflow:

1. The user taps the microphone.
2. On first use, Kai requests microphone permission and downloads the offline speech-recognition and speech-detection models. The mic control shows an indeterminate progress indicator while permission/model preparation or transcription is in progress; it is intentionally disabled during those operations.
3. After first-use model installation completes, the control returns to its ready microphone state. The user taps it again to start recording, then taps Stop.
4. Kai transcribes locally and inserts the result at the composer's current selection by default.
5. The user reviews and edits the text before using Kai's existing Send control.

An optional Android Settings toggle, **Automatically send speech transcripts**, is OFF by default. When explicitly enabled, Kai sends a non-empty transcript automatically only if the composer is empty, no files are attached, and Kai is not already responding. If a draft, attachment, or active response exists, the transcript is inserted for review instead. Failed or empty recognition is never sent.

Speech input remains foreground-only and does not keep the microphone open between recordings. Audio is held in memory for a maximum of 60 seconds and is not written to persistent storage or uploaded.

The first-use model download requires an internet connection. After installation, recognition itself is offline.

## Runtime and model

- Runtime: [sherpa-onnx 2.0.1](https://github.com/k2-fsa/sherpa-onnx), Apache-2.0.
- Model: Moonshine v2 Tiny, English, quantized, [MIT license](https://github.com/moonshine-ai/moonshine).
- Model archive: `sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27.tar.bz2`.
- Archive size: 29,858,559 bytes.
- Pinned SHA-256: `9ec31b342d8fa3240c3b81b8f82e1cf7e3ac467c93ca5a999b741d5887164f8d`.
- Extracted model files: approximately 43 MiB.

The model is downloaded to app-private storage, checked against its pinned SHA-256 before extraction, and installed from a staging directory. Only the encoder, merged decoder, token vocabulary, and upstream model license are retained. The recognizer is created after recording and released after each transcription so its native model memory is not kept resident beside Kai's local language model.

The sherpa-onnx Android AAR is a multi-ABI package (approximately 47.2 MB in the upstream v2.0.1 release). This is a meaningful application-size cost and should be reconsidered if Kai later adopts a smaller, verified runtime package.

## Voice activity detection

- Detector: Silero VAD ONNX, distributed by sherpa-onnx.
- Model file: `silero_vad.onnx` (643,854 bytes).
- Model choice: retain the standard 643,854-byte export. The smaller 208 KB int8 export is not substituted unless A36 testing proves it preserves quiet-speech detection.
- Model license: MIT; the full Silero Team notice is included at [`docs/licenses/SILERO-VAD-MIT.txt`](licenses/SILERO-VAD-MIT.txt).
- Pinned SHA-256: `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6`.
- Upstream release: [sherpa-onnx asr-models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models).
- Kotlin API: `Vad`, `VadModelConfig`, and `SileroVadModelConfig` from the existing sherpa-onnx 2.0.1 dependency.
- Processing: 16 kHz mono float PCM, 512-sample VAD windows, 0.5 threshold, 250 ms minimum speech duration, 300 ms minimum silence duration, and 120/180 ms pre/post-roll.
- Maximum speech horizon: aligned with the 60-second capture limit so Sherpa-ONNX does not raise its internal detection threshold partway through an allowed recording.

The VAD stage is a speech-presence gate, not a guarantee that every detected region contains speech or that quiet speech is always detected. The actual A36 noise/quiet-speech acceptance criteria must be confirmed on-device. No fixed amplitude/RMS cutoff is used as the sole speech test. If no VAD segment is returned, Moonshine is not invoked and no text is inserted.

## Accuracy and limitations

The integrated archive is the English **non-streaming Tiny** model (26 million parameters), not Tiny Streaming. Moonshine's current model table reports 12.66% WER for English Tiny and 12.00% for the separate 34-million-parameter Tiny Streaming model. The 12.00% streaming result must not be attributed to this integration. The published table is measured on the floating-point reference; the quantized archive used here may score differently. These are benchmark references, not a promise of accuracy in noisy rooms, overlapping speech, spontaneous conversation, names, or every accent. Device-specific accuracy must be measured on representative Galaxy A36 recordings.

This first version is a bounded, stop-to-transcribe workflow. It uses Silero VAD (through the existing sherpa-onnx runtime) before Moonshine decoding. The VAD model is downloaded to app-private storage, pinned by byte count and SHA-256, and remains on-device. If no speech region is detected, Kai returns no transcript and leaves the composer unchanged. The initial VAD settings use the upstream 0.5 speech threshold and 250 ms minimum speech duration, with 120 ms pre-roll and 180 ms post-roll to protect word onsets and endings. These are starting values requiring real-device validation against silence, fan noise, quiet speech, and clipped starts. Live partial results, automatic language detection, and a second-pass verifier remain out of scope. Those features should be added only after device testing shows a clear benefit.
