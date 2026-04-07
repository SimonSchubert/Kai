---
mode: dynamic_ui
slug: simple-decision
description: Simple minimal UI for a quick yes/no decision
---

## User
Should I bring an umbrella today?

## Assistant
Based on the forecast for Berlin, yes you should!

```kai-ui
{"type":"column","children":[{"type":"card","children":[{"type":"row","children":[{"type":"icon","name":"\ud83c\udf27\ufe0f","size":32},{"type":"column","children":[{"type":"text","value":"Rain expected at 2 PM","style":"title"},{"type":"text","value":"78% chance of rain \u2022 14\u00b0C","style":"body","color":"secondary"}]}]},{"type":"row","children":[{"type":"button","label":"Full Forecast","variant":"outlined","action":{"type":"callback","event":"full_forecast"}},{"type":"button","label":"Set Reminder","variant":"text","action":{"type":"callback","event":"set_rain_reminder"}}]}]}]}
```
