# Mistral Fine-Tuning for Kai Dynamic UI

Fine-tune a Mistral model that excels at generating Kai's `kai-ui` JSON DSL — the 29-component system for interactive dynamic UI screens.

## Overview

Kai's dynamic UI lets the AI generate interactive screens (forms, dashboards, quizzes, etc.) using a custom JSON format wrapped in ` ```kai-ui ` code fences. Different LLM providers vary in how well they handle this format. Fine-tuning teaches a Mistral model the exact component schema, action system, and layout rules.

## Prerequisites

- A Mistral API key with fine-tuning access: [console.mistral.ai](https://console.mistral.ai)
- The project builds: `./gradlew composeApp:compileKotlinDesktop`

## Workflow

### Step 1: Prepare Training Data

Golden examples are hand-crafted ideal responses in `golden/*.md`. Each file contains:
- A frontmatter block with `mode` (dynamic_ui or interactive)
- A `## User` section with the prompt
- A `## Assistant` section with the ideal response containing valid kai-ui JSON

**To add more examples**, create a new `.md` file following the same format. The more diverse examples you have, the better the model will learn.

Generate the JSONL training data:

```bash
./gradlew :composeApp:desktopTest --tests "*GenerateTrainingData*" --info
```

This produces:
- `output/training_data.jsonl` — 90% of examples (for training)
- `output/validation_data.jsonl` — 10% of examples (for evaluation)

**Optional: Include curated LLM responses.** If you've run the integration test with API keys, successful responses from strong models (GPT-4o, Claude, etc.) are automatically included as additional training data:

```bash
KAI_INTEGRATION=1 KAI_OPENAI_KEY=sk-... KAI_ANTHROPIC_KEY=sk-ant-... \
  ./gradlew :composeApp:desktopTest --tests "*KaiUiValidationTest.validate*" --info
```

Then re-run `GenerateTrainingData` to pick up the saved responses.

### Step 2: Run Fine-Tuning

```bash
MISTRAL_API_KEY=your-key-here \
  ./gradlew :composeApp:desktopTest --tests "*RunFineTuning*" --info
```

The script will:
1. Upload training and validation JSONL files to Mistral
2. Create a fine-tuning job
3. Poll every 30 seconds, printing status and training metrics
4. Print the resulting model ID on completion

**Configuration via environment variables:**

| Variable               | Default         | Description              |
|------------------------|-----------------|--------------------------|
| `MISTRAL_API_KEY`      | (required)      | Your Mistral API key     |
| `MISTRAL_BASE_MODEL`   | open-mistral-7b | Base model to fine-tune  |
| `MISTRAL_TRAINING_STEPS` | 100           | Number of training steps |
| `MISTRAL_LEARNING_RATE`  | 0.0001        | Learning rate            |
| `MISTRAL_POLL_INTERVAL`  | 30            | Seconds between polls    |

### Step 3: Validate the Model

Run the integration test battery against both the base and fine-tuned models:

```bash
KAI_INTEGRATION=1 \
  KAI_MISTRAL_KEY=your-key \
  KAI_MISTRAL_FT_KEY=your-key \
  KAI_MISTRAL_FT_MODEL=ft:open-mistral-7b:your-id:date:hash \
  ./gradlew :composeApp:desktopTest --tests "*KaiUiValidationTest.validate*" --info
```

Compare the success rates in the generated report at `build/reports/kaiui-integration/report.md`.

### Step 4: Use in Kai

The fine-tuned model automatically appears in Kai's model dropdown:

1. Open Kai Settings > Services
2. Add or expand the Mistral service
3. Enter your API key
4. Select the fine-tuned model from the dropdown (it will have an `ft:` prefix)

No code changes needed — Kai fetches available models from the Mistral API.

## Training Data Format

Each JSONL line contains a conversation with system/user/assistant messages:

```json
{"messages":[{"role":"system","content":"...kai-ui system prompt..."},{"role":"user","content":"Show me a form..."},{"role":"assistant","content":"Here's the form:\n\n```kai-ui\n{\"type\":\"column\",...}\n```"}]}
```

The system prompt matches what Kai sends to the AI at runtime (the kai-ui component definitions, action types, and layout rules).

## Directory Structure

```
tools/finetuning/
├── README.md           ← You are here
├── golden/             ← Hand-crafted training examples
│   ├── 01-simple-form.md
│   ├── 02-quiz-buttons.md
│   └── ...
└── output/             ← Generated JSONL (gitignored)
    ├── training_data.jsonl
    └── validation_data.jsonl
```

## Tips

- **More examples = better results.** Aim for 200+ examples. The golden examples provide ~30, and curated integration test responses can add more.
- **Cover all components.** Ensure training data includes all 29 component types and both modes (dynamic UI and interactive).
- **Multi-turn conversations.** Include examples where the user sends a callback event and the assistant responds with the next screen.
- **Iterate.** After fine-tuning, run the validation test, review failures, add golden examples that address the failures, and fine-tune again.

## Cost

Mistral fine-tuning pricing depends on the base model and number of training tokens. Check [Mistral's pricing page](https://mistral.ai/technology/#pricing) for current rates. A typical training run with 200 examples and 100 steps costs a few dollars.
