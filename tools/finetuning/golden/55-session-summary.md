---
mode: interactive
slug: session-summary
description: End-of-session summary after completing a quiz or study flow
---

## User
Pressed: end_quiz

## Assistant
```kai-ui
{"type":"column","children":[{"type":"box","contentAlignment":"center","children":[{"type":"icon","name":"trophy","size":56,"color":"primary"},{"type":"text","value":"Session Complete!","style":"headline"}]},{"type":"card","children":[{"type":"row","children":[{"type":"stat","value":"8/10","label":"\u2705 Correct"},{"type":"stat","value":"2","label":"\u274c Missed"},{"type":"stat","value":"80%","label":"\ud83c\udfaf Score"}]},{"type":"divider"},{"type":"row","children":[{"type":"stat","value":"4:32","label":"\u23f1 Total Time"},{"type":"stat","value":"27s","label":"\u26a1 Avg/Question"},{"type":"stat","value":"12","label":"\ud83d\udd25 Day Streak"}]}]},{"type":"card","children":[{"type":"text","value":"Areas to Review","style":"title"},{"type":"alert","message":"You missed questions about time complexity and binary trees. Consider reviewing these topics.","severity":"warning"},{"type":"list","items":["Big-O notation for nested loops","Binary tree traversal orders","Space complexity of recursive algorithms"],"ordered":false}]},{"type":"card","children":[{"type":"text","value":"Progress This Week","style":"title"},{"type":"progress","value":0.6,"label":"3 of 5 daily sessions completed"},{"type":"text","value":"Keep it up! Two more sessions to hit your weekly goal.","style":"body","color":"secondary"}]},{"type":"row","children":[{"type":"button","label":"New Session","action":{"type":"callback","event":"new_session"}},{"type":"button","label":"Review Mistakes","variant":"outlined","action":{"type":"callback","event":"review_mistakes"}},{"type":"button","label":"Share Score","variant":"text","action":{"type":"callback","event":"share_score"}}]}]}
```
