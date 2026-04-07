---
mode: interactive
slug: flashcard
description: Flashcard study with toggle to reveal answer and self-rating
---

## User
Quiz me on Kotlin concepts with flashcards.

## Assistant
```kai-ui
{"type":"column","children":[{"type":"row","children":[{"type":"text","value":"Kotlin Flashcards","style":"headline"},{"type":"badge","value":"Card 7 of 20","color":"secondary"}]},{"type":"progress","value":0.35,"label":"7 of 20 reviewed"},{"type":"card","children":[{"type":"text","value":"Question","style":"caption","color":"secondary"},{"type":"text","value":"What is the difference between val and var in Kotlin?","style":"title"},{"type":"divider"},{"type":"button","label":"Show Answer","variant":"outlined","action":{"type":"toggle","targetId":"answer"}},{"type":"column","id":"answer","children":[{"type":"text","value":"Answer","style":"caption","color":"secondary"},{"type":"text","value":"val declares a read-only (immutable) reference \u2014 it cannot be reassigned after initialization. var declares a mutable reference that can be reassigned.","style":"body"},{"type":"code","code":"val name = \"Kai\"    // cannot reassign\nvar count = 0       // can reassign\ncount = 1           // OK\n// name = \"other\"  // compile error","language":"kotlin"},{"type":"alert","message":"Note: val only makes the reference immutable, not the object itself. A val List can still have its contents modified if it's a MutableList.","severity":"info"}]}]},{"type":"text","value":"How well did you know this?","style":"body","color":"secondary"},{"type":"row","children":[{"type":"button","label":"\ud83d\ude2c Again","variant":"outlined","action":{"type":"callback","event":"rate","data":{"rating":"again"}}},{"type":"button","label":"\ud83e\udd14 Hard","variant":"outlined","action":{"type":"callback","event":"rate","data":{"rating":"hard"}}},{"type":"button","label":"\ud83d\ude42 Good","variant":"tonal","action":{"type":"callback","event":"rate","data":{"rating":"good"}}},{"type":"button","label":"\ud83d\ude0e Easy","variant":"outlined","action":{"type":"callback","event":"rate","data":{"rating":"easy"}}}]}]}
```
