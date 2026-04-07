---
mode: interactive
slug: smart-home
description: Smart home dashboard with switches, tonal buttons, alerts, ordered list, single-select chips, tabs
---

## User
Show me my smart home dashboard.

## Assistant
```kai-ui
{"type":"column","children":[{"type":"text","value":"Smart Home","style":"headline"},{"type":"alert","title":"All systems online","message":"5 devices connected, 3 active.","severity":"success"},{"type":"tabs","tabs":[{"label":"Devices","children":[{"type":"card","children":[{"type":"switch","id":"lights","label":"Ceiling Lights","checked":true},{"type":"switch","id":"fan","label":"Fan","checked":false},{"type":"switch","id":"speaker","label":"Smart Speaker","checked":true},{"type":"switch","id":"thermostat","label":"Thermostat","checked":true},{"type":"switch","id":"camera","label":"Security Camera","checked":false}]},{"type":"card","children":[{"type":"row","children":[{"type":"stat","value":"22\u00b0C","label":"\ud83c\udf21\ufe0f Temp"},{"type":"stat","value":"45%","label":"\ud83d\udca7 Humidity"},{"type":"stat","value":"3.2 kWh","label":"\u26a1 Energy"}]},{"type":"slider","id":"target_temp","label":"Target Temperature (\u00b0C)","value":22,"min":16,"max":30,"step":1}]},{"type":"button","label":"Apply Changes","variant":"tonal","action":{"type":"callback","event":"apply_devices","collectFrom":["lights","fan","speaker","thermostat","camera","target_temp"]}},{"type":"alert","title":"Garage door open","message":"The garage door has been open for 45 minutes.","severity":"error"}]},{"label":"Routines","children":[{"type":"card","children":[{"type":"text","value":"Evening Routine","style":"title"},{"type":"list","items":["Dim living room lights to 40%","Turn off kitchen appliances","Set thermostat to 20\u00b0C","Lock front door","Arm security camera"],"ordered":true},{"type":"row","children":[{"type":"button","label":"Run Routine","variant":"tonal","action":{"type":"callback","event":"run_routine"}},{"type":"button","label":"Edit","variant":"text","action":{"type":"callback","event":"edit_routine"}}]}]}]}],"selectedIndex":0},{"type":"row","children":[{"type":"button","label":"Close Garage","variant":"filled","action":{"type":"callback","event":"close_garage"}},{"type":"button","label":"Add Device","variant":"outlined","action":{"type":"callback","event":"add_device"}},{"type":"button","label":"Settings","variant":"tonal","action":{"type":"callback","event":"settings"}}]}]}
```
