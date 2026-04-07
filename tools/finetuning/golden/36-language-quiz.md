---
mode: interactive
slug: language-quiz
description: Language learning quiz with radio group, progress, avatar, badges, and streak stats
---

## User
Quiz me on my Spanish vocabulary.

## Assistant
```kai-ui
{"type":"column","children":[{"type":"row","children":[{"type":"icon","name":"language","size":28,"color":"primary"},{"type":"text","value":"Spanish Vocabulary","style":"headline"}]},{"type":"row","children":[{"type":"badge","value":"\ud83c\uddf2\ud83c\uddfd Espa\u00f1ol","color":"primary"},{"type":"badge","value":"\ud83d\udd25 12-day streak","color":"secondary"},{"type":"badge","value":"B1 Level","color":"secondary"}]},{"type":"progress","value":0.4,"label":"Question 4 of 10"},{"type":"card","children":[{"type":"text","value":"Translate to English:","style":"body","color":"secondary"},{"type":"text","value":"\u00bfD\u00f3nde est\u00e1 la biblioteca m\u00e1s cercana?","style":"title"},{"type":"radio_group","id":"answer","label":"Choose the correct translation","options":["Where is the nearest hospital?","Where is the nearest library?","Where is the nearest restaurant?","Where is the nearest park?"]},{"type":"button","label":"Check Answer","action":{"type":"callback","event":"check_answer","collectFrom":["answer"]}}]},{"type":"card","children":[{"type":"text","value":"Session Stats","style":"title"},{"type":"row","children":[{"type":"stat","value":"3/3","label":"\u2705 Correct"},{"type":"stat","value":"0","label":"\u274c Wrong"},{"type":"stat","value":"2:45","label":"\u23f1 Avg Time"}]},{"type":"quote","text":"The limits of my language mean the limits of my world.","source":"Ludwig Wittgenstein"}]},{"type":"row","children":[{"type":"button","label":"Skip Question","variant":"outlined","action":{"type":"callback","event":"skip"}},{"type":"button","label":"End Session","variant":"text","action":{"type":"callback","event":"end_session"}}]}]}
```
