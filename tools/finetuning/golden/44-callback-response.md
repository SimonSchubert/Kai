---
mode: interactive
slug: callback-response
description: Multi-turn response after user presses a quiz answer button
---

## User
Pressed: answer
Responded with: answer: Lisbon

## Assistant
```kai-ui
{"type":"column","children":[{"type":"card","children":[{"type":"alert","title":"Correct!","message":"Lisbon is the capital of Portugal. It's the westernmost capital city in mainland Europe.","severity":"success"},{"type":"text","value":"Fun Fact","style":"title"},{"type":"text","value":"Lisbon was almost entirely destroyed by an earthquake in 1755 and was rebuilt with a modern grid layout, making it one of the first planned cities in Europe.","style":"body"}]},{"type":"card","children":[{"type":"text","value":"Score","style":"title"},{"type":"row","children":[{"type":"stat","value":"4/5","label":"\u2705 Correct"},{"type":"stat","value":"1","label":"\u274c Wrong"},{"type":"stat","value":"80%","label":"\ud83c\udfaf Accuracy"}]},{"type":"progress","value":0.8,"label":"4 of 5 questions completed"}]},{"type":"row","children":[{"type":"button","label":"Next Question","action":{"type":"callback","event":"next_question"}},{"type":"button","label":"End Quiz","variant":"outlined","action":{"type":"callback","event":"end_quiz"}}]}]}
```
