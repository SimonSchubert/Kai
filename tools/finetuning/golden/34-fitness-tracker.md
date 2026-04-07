---
mode: interactive
slug: fitness-tracker
description: Fitness dashboard with progress, stats, countdown, and workout log
---

## User
Show me my fitness dashboard for today.

## Assistant
```kai-ui
{"type":"column","children":[{"type":"text","value":"Today's Fitness","style":"headline"},{"type":"row","children":[{"type":"badge","value":"\ud83d\udd25 5-day streak","color":"primary"},{"type":"badge","value":"April 7","color":"secondary"}]},{"type":"card","children":[{"type":"row","children":[{"type":"stat","value":"6,842","label":"\ud83d\udeb6 Steps"},{"type":"stat","value":"428","label":"\ud83d\udd25 Calories"},{"type":"stat","value":"3.2 km","label":"\ud83d\udccd Distance"},{"type":"stat","value":"47 min","label":"\u23f1 Active"}]},{"type":"divider"},{"type":"text","value":"Daily Goals","style":"title"},{"type":"progress","value":0.68,"label":"Steps: 6,842 / 10,000"},{"type":"progress","value":0.71,"label":"Calories: 428 / 600"},{"type":"progress","value":0.47,"label":"Water: 1.4L / 3.0L"}]},{"type":"card","children":[{"type":"text","value":"Quick Workout","style":"title"},{"type":"text","value":"Upper Body Strength \u2022 25 minutes","style":"body","color":"secondary"},{"type":"list","items":["Push-ups: 3 sets x 15 reps","Dumbbell rows: 3 sets x 12 reps","Shoulder press: 3 sets x 10 reps","Plank hold: 3 sets x 45 seconds"],"ordered":true},{"type":"countdown","seconds":1500,"label":"Workout timer","action":{"type":"callback","event":"workout_complete"}},{"type":"row","children":[{"type":"button","label":"Start Workout","action":{"type":"callback","event":"start_workout"}},{"type":"button","label":"Change Workout","variant":"outlined","action":{"type":"callback","event":"change_workout"}}]}]},{"type":"card","children":[{"type":"text","value":"Recent Activity","style":"title"},{"type":"table","headers":["Activity","Duration","Calories"],"rows":[["Morning Run","32 min","285"],["Yoga Session","20 min","95"],["Evening Walk","15 min","68"]]},{"type":"button","label":"View Full History","variant":"text","action":{"type":"callback","event":"view_history"}}]}]}
```
