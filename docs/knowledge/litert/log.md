# LiteRT knowledge update log

## 2026-08-12

* **Update**: Live pin check via `process:update-litert-models` against HuggingFace models + tree APIs.
  * **Attested** — all four pins (`gemma-4-e2b-it`, `gemma-4-e4b-it`, `gemma-4-12b-it`, `qwen3-0.6b`) match `.lfs.oid` and `.lfs.size` at the pinned commit.
  * **`main` moved** — each repo `sha` is newer than the pin; the `.litertlm` LFS oid and size on `main` are identical to the pin. No bump (bytes unchanged; policy forbids silent pin moves).
  * **Kotlin** — unchanged.
* **Initialization**: Created OKF litert bundle (pin policy + verify/bump playbook). Runtime source of truth remains `LocalModelCatalog.kt`.
