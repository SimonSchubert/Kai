# LiteRT knowledge update log

## 2026-09-07

* **Pin check**: all four existing pins attested against the HuggingFace tree API at their pinned commits.
* **Bump — `gemma-4-12b-it`**: upstream replaced the weight file on 2026-09-03 (`161028364f39`), size 6547589312 → 6883278368. Pinned to `7a0b1ce0ea821bcd01c5f72af84155e02191152f`, sha256 `58fd31b7…`. The new build adds vision + audio modalities and Multi-Token Prediction, and requires **litert-lm ≥ 0.17**; Android/JVM moved to 0.17.0 on 2026-09-06, so the requirement is met. The iOS bridge (LiteRT-LM 0.16.1) cannot load it — accepted, since the file is 6.9 GB.
* **Added — `lfm2.5-1.2b-instruct`**: `LFM2.5-1.2B-Instruct_int4_gpu.litertlm` at `f45d8d8abe93bff4026efee20fa483150ce8e687`, 736220768 bytes. Chosen over Qwen3.5 because its bundle carries a real tool-calling chat template (the Qwen3.5 conversions strip theirs). `_int4_gpu` rather than `_int4` because only that build lowers fully for the GPU delegate while still running on CPU.
* **Runtime API**: wired litert-lm 0.17's expanded `Capabilities` — declared function-calling support now gates whether an on-device model is handed tools at all, and each model's declared sampler defaults replace the hardcoded top-k 40 / top-p 0.95 / temperature 0.8.
* **iOS**: LiteRT-LM Swift package 0.15.0 → 0.16.1 (the newest tag; 0.17.0 was never tagged). The Swift sources for `Engine`, `Config`, `Conversation` and `Capabilities` are byte-identical between the two tags, so only the prebuilt xcframework changes.

## 2026-08-12

* **Update**: Live pin check via `process:update-litert-models` against HuggingFace models + tree APIs.
  * **Attested** — all four pins (`gemma-4-e2b-it`, `gemma-4-e4b-it`, `gemma-4-12b-it`, `qwen3-0.6b`) match `.lfs.oid` and `.lfs.size` at the pinned commit.
  * **`main` moved** — each repo `sha` is newer than the pin; the `.litertlm` LFS oid and size on `main` are identical to the pin. No bump (bytes unchanged; policy forbids silent pin moves).
  * **Kotlin** — unchanged.
* **Initialization**: Created OKF litert bundle (pin policy + verify/bump playbook). Runtime source of truth remains `LocalModelCatalog.kt`.
