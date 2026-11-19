# Deferred: Kai speech-engine evaluation plan

Status: deferred and out of scope for the microphone-first release. This document is retained as historical planning only. Do not download, integrate, benchmark, or switch to a challenger model as part of the current work. Keep the existing quantized Moonshine v2 Tiny English integration as the only speech engine until the microphone workflow is stable and the user separately authorizes a model evaluation.

## Decision to make

Keep the current quantized Moonshine v2 Tiny English integration as the functional baseline. Evaluate a sherpa-onnx Zipformer GigaSpeech English model as a challenger before changing the default engine. Do not select a winner from headline WER figures measured on different datasets, runtimes, devices, or model variants.

## Current baseline

- Android integration: sherpa-onnx 2.0.1.
- Model: quantized Moonshine v2 Tiny English.
- Archive: `sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27.tar.bz2`.
- Archive size: 29,858,559 bytes.
- Pinned SHA-256: `9ec31b342d8fa3240c3b81b8f82e1cf7e3ac467c93ca5a999b741d5887164f8d`.
- App behavior: bounded 60-second capture, Stop-to-transcribe, transcript inserted as an editable draft; no automatic submission.
- Real-device status: two successful user-reported exact transcriptions on Samsung Galaxy A36 (SM-S366V). This is functional smoke-test evidence, not a comparative accuracy benchmark.

## Candidate

- Model family: sherpa-onnx Zipformer, GigaSpeech-trained English offline transducer.
- Upstream model identifier: `sherpa-onnx-zipformer-gigaspeech-2023-12-12`.
- Upstream catalogue: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/zipformer-transducer-models.html
- The upstream package listing shows an int8 encoder around 68 MB and a substantially larger unquantized encoder. Do not infer total installed footprint or runtime memory from the encoder file alone.
- Before integration, verify model files, archive size, checksums, license, runtime API compatibility with sherpa-onnx 2.0.1, and whether an ORT-compatible variant is available for Android.

## Test corpus

Use the same fixed, documented clip manifest and reference transcripts for every model.

1. Earnings-22 test clips: financial calls, varied accents, spontaneous speech, teleconference audio, and domain vocabulary.
2. TED-LIUM 3 test clips: prepared public talks and longer-form speech.
3. LibriSpeech test-clean: clean read-speech sanity/reproducibility check, not the deciding real-world metric.
4. Optional follow-up: AMI meeting clips to test conversational turn-taking and multi-speaker conditions. Treat overlap and diarization separately; ordinary ASR WER alone does not measure speaker attribution.

Do not use a model's training corpus as its independent test set. In particular, because the challenger is GigaSpeech-trained, do not use GigaSpeech test clips as the primary independent comparison set.

Dataset background and published split/licensing information:
- Open ASR Leaderboard dataset overview: https://huggingface.co/datasets/hf-audio/open-asr-leaderboard
- Long-form evaluation set overview: https://huggingface.co/datasets/hf-audio/asr-leaderboard-longform

## Required controls

- Run every model on the same exact audio bytes, sample rate, channel conversion, clip boundaries, and reference transcript.
- Use the same text-normalization and WER implementation. Publish whether punctuation, casing, number normalization, and disfluencies are normalized.
- Disable VAD for already segmented benchmark clips unless VAD is explicitly the feature under test. Evaluate continuous-audio segmentation separately.
- Keep model decoding settings documented and fixed. Do not compare confidence scores as if they were calibrated.
- Record exact app build, Android version, device model/RAM configuration, sherpa-onnx version, model revision, and thread settings.
- Run cold-load and warm inference separately. Repeat timing runs and report median plus range.
- Do not run two large models simultaneously on the phone. Release the first recognizer and confirm cleanup before loading the next.

## Measurements

For each model, report:

- Corpus-level WER and per-dataset WER (substitutions, deletions, insertions).
- Real-time factor (inference duration divided by audio duration), plus end-to-end stop-to-transcript latency.
- Cold model-load time and first-use setup/download time, separately.
- Download archive size and installed model footprint.
- Peak process/native memory where measurable; record baseline and peak, not just a single unexplained number.
- Failure rate, crashes, empty/partial output, and timeout count.
- Thermal state and battery impact as qualitative observations if reliable instrumentation is unavailable.

A model is not a winner if a lower WER comes with unacceptable memory pressure, load time, crashes, or app instability on the actual device.

## Decision gate

1. Preserve Moonshine as the known-working default while evaluation is underway.
2. Validate the candidate's model files, licensing, and sherpa-onnx 2.0.1 compatibility without changing the production/default path.
3. Run the controlled corpus on the Galaxy A36.
4. Compare accuracy, latency, memory, footprint, and failures.
5. Choose the model only if the measured trade-off is materially better for Kai's intended use.
6. If results are inconclusive, retain Moonshine and document the uncertainty.

## Published results: context, not Kai results

Moonshine's current model documentation reports quantized Tiny Streaming WER of 4.83% on LibriSpeech test-clean and 12.00% on its eight-dataset Open ASR average. These are different metrics over different evaluation sets and are not Kai-device measurements:
- https://github.com/moonshine-ai/moonshine/blob/main/docs/models/available-models.md
- https://github.com/moonshine-ai/moonshine/blob/main/docs/models/accuracy.md

An independent Android benchmark repository reports results for several sherpa Zipformer variants on a Snapdragon 690 device. Its reported numbers vary by model variant; they are not measurements of Kai's exact candidate on the Galaxy A36. Use them only as motivation to benchmark locally:
- https://github.com/imedwei/android-asr-benchmark

## Deferred next action

No benchmark harness or challenger-model integration is part of the current release. Revisit this plan only after the microphone-first workflow has passed device testing and the user explicitly authorizes a separate speech-engine evaluation.
