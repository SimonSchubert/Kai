---
mode: dynamic_ui
slug: poll-results
description: Survey with voting and results display (dynamic_ui mode with markdown)
---

## User
Create a poll about favorite programming languages.

## Assistant
Here's a quick poll! Vote for your favorite and see the current results:

```kai-ui
{"type":"column","children":[{"type":"card","children":[{"type":"text","value":"What's your favorite programming language?","style":"title"},{"type":"radio_group","id":"vote","label":"Cast your vote","options":["Kotlin","Python","TypeScript","Rust","Go","Swift"]},{"type":"button","label":"Vote","action":{"type":"callback","event":"submit_vote","collectFrom":["vote"]}}]},{"type":"card","children":[{"type":"text","value":"Results so far","style":"title"},{"type":"text","value":"1,247 votes","style":"caption","color":"secondary"},{"type":"text","value":"Python","style":"body"},{"type":"progress","value":0.32,"label":"32% \u2022 399 votes"},{"type":"text","value":"Kotlin","style":"body"},{"type":"progress","value":0.24,"label":"24% \u2022 299 votes"},{"type":"text","value":"TypeScript","style":"body"},{"type":"progress","value":0.19,"label":"19% \u2022 237 votes"},{"type":"text","value":"Rust","style":"body"},{"type":"progress","value":0.14,"label":"14% \u2022 175 votes"},{"type":"text","value":"Go","style":"body"},{"type":"progress","value":0.07,"label":"7% \u2022 87 votes"},{"type":"text","value":"Swift","style":"body"},{"type":"progress","value":0.04,"label":"4% \u2022 50 votes"}]},{"type":"row","children":[{"type":"button","label":"Share Poll","variant":"outlined","action":{"type":"callback","event":"share_poll"}},{"type":"button","label":"Create New Poll","variant":"text","action":{"type":"callback","event":"new_poll"}}]}]}
```

Python is leading but Kotlin is close behind! Cast your vote to shake things up.
