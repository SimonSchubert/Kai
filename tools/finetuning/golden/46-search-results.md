---
mode: dynamic_ui
slug: search-results
description: Search results with cards, ratings, and filter actions
---

## User
Find me a good Italian restaurant nearby.

## Assistant
Here are the top-rated Italian restaurants in your area:

```kai-ui
{"type":"column","children":[{"type":"card","children":[{"type":"row","children":[{"type":"icon","name":"\ud83c\udf55","size":28},{"type":"column","children":[{"type":"text","value":"Trattoria da Luigi","style":"title","bold":true},{"type":"text","value":"0.4 km \u2022 $$$ \u2022 Closes at 22:00","style":"caption","color":"secondary"}]}]},{"type":"row","children":[{"type":"badge","value":"\u2b50 4.8","color":"primary"},{"type":"badge","value":"Traditional","color":"secondary"},{"type":"badge","value":"Outdoor Seating","color":"secondary"}]},{"type":"text","value":"Authentic Roman cuisine with handmade pasta. Known for their cacio e pepe and tiramisu.","style":"body"},{"type":"row","children":[{"type":"button","label":"Reserve","action":{"type":"callback","event":"reserve","data":{"restaurant":"luigi"}}},{"type":"button","label":"Menu","variant":"outlined","action":{"type":"callback","event":"menu","data":{"restaurant":"luigi"}}}]}]},{"type":"card","children":[{"type":"row","children":[{"type":"icon","name":"\ud83c\udf5d","size":28},{"type":"column","children":[{"type":"text","value":"Bella Napoli","style":"title","bold":true},{"type":"text","value":"1.2 km \u2022 $$ \u2022 Closes at 23:00","style":"caption","color":"secondary"}]}]},{"type":"row","children":[{"type":"badge","value":"\u2b50 4.6","color":"primary"},{"type":"badge","value":"Neapolitan Pizza","color":"secondary"},{"type":"badge","value":"Family Friendly","color":"secondary"}]},{"type":"text","value":"Best wood-fired pizza in town. Casual atmosphere, generous portions, and great value.","style":"body"},{"type":"row","children":[{"type":"button","label":"Reserve","action":{"type":"callback","event":"reserve","data":{"restaurant":"napoli"}}},{"type":"button","label":"Menu","variant":"outlined","action":{"type":"callback","event":"menu","data":{"restaurant":"napoli"}}}]}]},{"type":"card","children":[{"type":"row","children":[{"type":"icon","name":"\ud83e\uddc0","size":28},{"type":"column","children":[{"type":"text","value":"Osteria Verde","style":"title","bold":true},{"type":"text","value":"2.0 km \u2022 $$ \u2022 Closes at 21:30","style":"caption","color":"secondary"}]}]},{"type":"row","children":[{"type":"badge","value":"\u2b50 4.5","color":"primary"},{"type":"badge","value":"Vegetarian Options","color":"secondary"},{"type":"badge","value":"Wine Bar","color":"secondary"}]},{"type":"text","value":"Modern Italian with a focus on seasonal ingredients. Excellent wine selection and cozy interior.","style":"body"},{"type":"row","children":[{"type":"button","label":"Reserve","action":{"type":"callback","event":"reserve","data":{"restaurant":"verde"}}},{"type":"button","label":"Menu","variant":"outlined","action":{"type":"callback","event":"menu","data":{"restaurant":"verde"}}}]}]},{"type":"button","label":"Show More Results","variant":"text","action":{"type":"callback","event":"more_results"}}]}
```

All three have excellent reviews. Trattoria da Luigi is the closest and highest rated — want me to make a reservation?
