---
mode: dynamic_ui
slug: gift-finder
description: AI helps find a gift by collecting preferences and showing recommendations
---

## User
Help me find a birthday gift for my mom.

## Assistant
I'd love to help! Let me narrow it down:

```kai-ui
{"type":"column","children":[{"type":"card","children":[{"type":"text","value":"Tell me about her","style":"title"},{"type":"select","id":"age","label":"Age range","options":["30\u201340","40\u201350","50\u201360","60\u201370","70+"],"selected":"50\u201360"},{"type":"chip_group","id":"interests","chips":[{"label":"Cooking","value":"cooking"},{"label":"Gardening","value":"gardening"},{"label":"Reading","value":"reading"},{"label":"Travel","value":"travel"},{"label":"Fitness","value":"fitness"},{"label":"Art","value":"art"},{"label":"Music","value":"music"},{"label":"Tech","value":"tech"}],"selection":"multi"},{"type":"slider","id":"budget","label":"Budget (\u20ac)","value":50,"min":10,"max":200,"step":10},{"type":"button","label":"Find Gift Ideas","action":{"type":"callback","event":"find_gifts","collectFrom":["age","interests","budget"]}}]}]}
```
