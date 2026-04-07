---
mode: dynamic_ui
slug: daily-planner
description: Daily schedule with checkboxes, progress, and update action (dynamic_ui mode with markdown)
---

## User
What's on my schedule today?

## Assistant
You've got a busy day! Here's your schedule:

```kai-ui
{"type":"column","children":[{"type":"card","children":[{"type":"row","children":[{"type":"text","value":"Monday, April 7","style":"title"},{"type":"badge","value":"6 events","color":"primary"}]},{"type":"progress","value":0.33,"label":"2 of 6 completed"}]},{"type":"card","children":[{"type":"checkbox","id":"standup","label":"9:00 \u2014 Team standup (15 min)","checked":true},{"type":"checkbox","id":"review","label":"10:00 \u2014 Code review with Sarah","checked":true},{"type":"checkbox","id":"lunch","label":"12:00 \u2014 Lunch with Alex @ Cafe Milano","checked":false},{"type":"checkbox","id":"design","label":"14:00 \u2014 Design review (1 hour)","checked":false},{"type":"checkbox","id":"gym","label":"17:30 \u2014 Gym session","checked":false},{"type":"checkbox","id":"dinner","label":"19:30 \u2014 Dinner reservation (2 people)","checked":false},{"type":"button","label":"Update Progress","action":{"type":"callback","event":"update_schedule","collectFrom":["standup","review","lunch","design","gym","dinner"]}}]},{"type":"alert","title":"Reminder","message":"Design review in 2 hours. Prepare the mockups from last week's feedback.","severity":"info"},{"type":"row","children":[{"type":"button","label":"Add Event","action":{"type":"callback","event":"add_event"}},{"type":"button","label":"Tomorrow","variant":"outlined","action":{"type":"callback","event":"show_tomorrow"}},{"type":"button","label":"Week View","variant":"text","action":{"type":"callback","event":"week_view"}}]}]}
```

Want me to set a reminder for any of these, or reschedule something?
