---
mode: interactive
slug: box-centered
description: Onboarding screen collecting user preferences
---

## User
Create a welcome screen with a centered logo, app name, and get started button.

## Assistant
```kai-ui
{"type":"column","children":[{"type":"box","contentAlignment":"center","children":[{"type":"icon","name":"rocket","size":64,"color":"primary"},{"type":"text","value":"Welcome to Kai","style":"headline"},{"type":"text","value":"Your AI assistant with persistent memory","style":"body","color":"secondary"}]},{"type":"card","children":[{"type":"text","value":"Let's personalize your experience","style":"title"},{"type":"text_input","id":"name","label":"What should I call you?","placeholder":"Enter your name"},{"type":"select","id":"role","label":"What best describes you?","options":["Student","Developer","Designer","Researcher","Business Professional","Just Exploring"],"selected":"Just Exploring"},{"type":"slider","id":"verbosity","label":"How detailed should my responses be?","value":5,"min":1,"max":10,"step":1},{"type":"row","children":[{"type":"text","value":"Brief","style":"caption","color":"secondary"},{"type":"text","value":"Detailed","style":"caption","color":"secondary"}]},{"type":"chip_group","id":"interests","chips":[{"label":"Coding","value":"coding"},{"label":"Writing","value":"writing"},{"label":"Learning","value":"learning"},{"label":"Productivity","value":"productivity"},{"label":"Creative","value":"creative"},{"label":"Data","value":"data"}],"selection":"multi"},{"type":"divider"},{"type":"text","value":"Enable features","style":"title"},{"type":"checkbox","id":"memory","label":"Remember things about me across conversations","checked":true},{"type":"checkbox","id":"dynamic_ui","label":"Use interactive UI elements in responses","checked":true},{"type":"checkbox","id":"proactive","label":"Proactively suggest ideas and follow-ups","checked":false}]},{"type":"row","children":[{"type":"button","label":"Get Started","action":{"type":"callback","event":"onboarding_complete","collectFrom":["name","role","verbosity","interests","memory","dynamic_ui","proactive"]}},{"type":"button","label":"Skip","variant":"text","action":{"type":"callback","event":"skip_onboarding"}}]}]}
```
