---
mode: dynamic_ui
slug: emoji-icons
description: Weather forecast using emoji icons with location and actions
---

## User
Show me a weather forecast for the week using emoji icons.

## Assistant
Here's this week's forecast:

```kai-ui
{"type":"column","children":[{"type":"text","value":"Weekly Forecast","style":"headline"},{"type":"row","children":[{"type":"icon","name":"location","size":20,"color":"secondary"},{"type":"text","value":"Berlin, Germany","style":"body","color":"secondary"},{"type":"button","label":"Change","variant":"text","action":{"type":"callback","event":"change_location"}}]},{"type":"card","children":[{"type":"text","value":"Monday","style":"title"},{"type":"row","children":[{"type":"icon","name":"\u2600\ufe0f","size":28},{"type":"text","value":"Sunny, 24\u00b0C / 15\u00b0C","style":"body"}]}]},{"type":"card","children":[{"type":"text","value":"Tuesday","style":"title"},{"type":"row","children":[{"type":"icon","name":"\u26c5","size":28},{"type":"text","value":"Partly Cloudy, 21\u00b0C / 13\u00b0C","style":"body"}]}]},{"type":"card","children":[{"type":"text","value":"Wednesday","style":"title"},{"type":"row","children":[{"type":"icon","name":"\ud83c\udf27\ufe0f","size":28},{"type":"text","value":"Rain, 18\u00b0C / 11\u00b0C","style":"body"},{"type":"badge","value":"\ud83c\udf02 80%","color":"secondary"}]}]},{"type":"card","children":[{"type":"text","value":"Thursday","style":"title"},{"type":"row","children":[{"type":"icon","name":"\u26c8\ufe0f","size":28},{"type":"text","value":"Thunderstorms, 16\u00b0C / 10\u00b0C","style":"body"},{"type":"badge","value":"\ud83c\udf02 95%","color":"error"}]}]},{"type":"card","children":[{"type":"text","value":"Friday","style":"title"},{"type":"row","children":[{"type":"icon","name":"\u2600\ufe0f","size":28},{"type":"text","value":"Sunny, 25\u00b0C / 16\u00b0C","style":"body"}]}]},{"type":"alert","title":"Heads up","message":"Heavy rain expected Wednesday through Thursday. Consider bringing an umbrella.","severity":"warning"},{"type":"row","children":[{"type":"button","label":"Full Forecast","variant":"filled","action":{"type":"callback","event":"full_forecast"}},{"type":"button","label":"Next Week","variant":"outlined","action":{"type":"callback","event":"next_week"}}]}]}
```
