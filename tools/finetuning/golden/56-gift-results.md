---
mode: dynamic_ui
slug: gift-results
description: AI presents gift recommendations after collecting preferences
---

## User
Pressed: find_gifts
Responded with: age: 50-60, interests: cooking, gardening, budget: 50

## Assistant
Here are some ideas she might love:

```kai-ui
{"type":"column","children":[{"type":"card","children":[{"type":"row","children":[{"type":"icon","name":"\ud83c\udf3f","size":28},{"type":"column","children":[{"type":"text","value":"Indoor Herb Garden Kit","style":"title","bold":true},{"type":"text","value":"\u20ac35 \u2022 Combines cooking + gardening","style":"body","color":"secondary"}]}]},{"type":"text","value":"A self-watering planter with basil, mint, rosemary, and thyme seeds. Fresh herbs for cooking straight from the windowsill.","style":"body"},{"type":"row","children":[{"type":"button","label":"Love It","variant":"tonal","action":{"type":"callback","event":"select_gift","data":{"gift":"herb_garden"}}},{"type":"button","label":"More Like This","variant":"text","action":{"type":"callback","event":"similar","data":{"gift":"herb_garden"}}}]}]},{"type":"card","children":[{"type":"row","children":[{"type":"icon","name":"\ud83d\udcd6","size":28},{"type":"column","children":[{"type":"text","value":"Ottolenghi Cookbook: Comfort","style":"title","bold":true},{"type":"text","value":"\u20ac32 \u2022 Beautiful recipes with garden ingredients","style":"body","color":"secondary"}]}]},{"type":"text","value":"His newest cookbook focuses on simple comfort food. Many recipes feature fresh herbs and seasonal vegetables from the garden.","style":"body"},{"type":"row","children":[{"type":"button","label":"Love It","variant":"tonal","action":{"type":"callback","event":"select_gift","data":{"gift":"cookbook"}}},{"type":"button","label":"More Like This","variant":"text","action":{"type":"callback","event":"similar","data":{"gift":"cookbook"}}}]}]},{"type":"card","children":[{"type":"row","children":[{"type":"icon","name":"\u2702\ufe0f","size":28},{"type":"column","children":[{"type":"text","value":"Japanese Pruning Shears Set","style":"title","bold":true},{"type":"text","value":"\u20ac48 \u2022 Premium gardening tools","style":"body","color":"secondary"}]}]},{"type":"text","value":"Hand-forged carbon steel shears in a linen pouch. Perfect for precision pruning and herb harvesting.","style":"body"},{"type":"row","children":[{"type":"button","label":"Love It","variant":"tonal","action":{"type":"callback","event":"select_gift","data":{"gift":"shears"}}},{"type":"button","label":"More Like This","variant":"text","action":{"type":"callback","event":"similar","data":{"gift":"shears"}}}]}]},{"type":"row","children":[{"type":"button","label":"Different Ideas","variant":"outlined","action":{"type":"callback","event":"regenerate_gifts"}},{"type":"button","label":"Change Preferences","variant":"text","action":{"type":"callback","event":"change_preferences"}}]}]}
```

All of these are under your \u20ac50 budget and connect her love of cooking with gardening. Want me to find where to buy any of these?
