---
mode: dynamic_ui
slug: product-comparison
description: Shopping comparison with table, badges, and purchase actions (dynamic_ui mode with markdown)
---

## User
Compare the MacBook Air M3 and the ThinkPad X1 Carbon for me.

## Assistant
Both are excellent ultrabooks. Here's a side-by-side comparison:

```kai-ui
{"type":"column","children":[{"type":"card","children":[{"type":"text","value":"Specs Comparison","style":"title"},{"type":"table","headers":["","MacBook Air M3","ThinkPad X1 Carbon"],"rows":[["Price","$1,099","$1,249"],["Display","13.6\" Liquid Retina","14\" 2.8K OLED"],["Processor","Apple M3","Intel Core Ultra 7"],["RAM","8 GB","16 GB"],["Storage","256 GB SSD","512 GB SSD"],["Battery","18 hours","15 hours"],["Weight","1.24 kg","1.12 kg"],["OS","macOS","Windows 11"]]}]},{"type":"row","children":[{"type":"card","children":[{"type":"text","value":"MacBook Air M3","style":"title"},{"type":"row","children":[{"type":"badge","value":"\u2b50 4.7/5","color":"primary"},{"type":"badge","value":"Best Battery","color":"secondary"}]},{"type":"button","label":"View Details","action":{"type":"callback","event":"details","data":{"product":"macbook"}}}]},{"type":"card","children":[{"type":"text","value":"ThinkPad X1","style":"title"},{"type":"row","children":[{"type":"badge","value":"\u2b50 4.5/5","color":"primary"},{"type":"badge","value":"Best Display","color":"secondary"}]},{"type":"button","label":"View Details","action":{"type":"callback","event":"details","data":{"product":"thinkpad"}}}]}]},{"type":"row","children":[{"type":"button","label":"Help Me Decide","action":{"type":"callback","event":"recommend"}},{"type":"button","label":"Add Another Laptop","variant":"outlined","action":{"type":"callback","event":"add_product"}}]}]}
```

The MacBook Air wins on battery life and ecosystem integration, while the ThinkPad offers a superior display and more RAM at the base config. Want me to dig deeper into any specific aspect?
