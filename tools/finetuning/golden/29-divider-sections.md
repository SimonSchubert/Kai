---
mode: dynamic_ui
slug: divider-sections
description: Recipe card with badges, accordion steps, and input
---

## User
Show me a recipe for pancakes with ingredients and steps separated clearly.

## Assistant
Here's a classic pancake recipe:

```kai-ui
{"type":"column","children":[{"type":"card","children":[{"type":"text","value":"Classic Pancakes","style":"headline"},{"type":"row","children":[{"type":"badge","value":"\u23f1 15 min prep","color":"secondary"},{"type":"badge","value":"\ud83c\udf73 10 min cook","color":"secondary"},{"type":"badge","value":"\ud83c\udf5e 8 pancakes","color":"primary"}]},{"type":"image","url":"https://upload.wikimedia.org/wikipedia/commons/4/43/Blueberry_pancakes_%283%29.jpg","aspectRatio":1.6},{"type":"divider"},{"type":"text","value":"Ingredients","style":"title"},{"type":"list","items":["1 cup all-purpose flour","2 tablespoons sugar","1 teaspoon baking powder","1 egg","3/4 cup milk","2 tablespoons melted butter"],"ordered":false},{"type":"divider"},{"type":"text","value":"Instructions","style":"title"},{"type":"accordion","title":"Step 1: Prepare dry ingredients","children":[{"type":"text","value":"In a large bowl, whisk together the flour, sugar, and baking powder until evenly combined.","style":"body"}]},{"type":"accordion","title":"Step 2: Mix the batter","children":[{"type":"text","value":"In a separate bowl, beat the egg, then add milk and melted butter. Pour the wet ingredients into the dry ingredients and stir until just combined. A few lumps are fine \u2014 don't overmix.","style":"body"}]},{"type":"accordion","title":"Step 3: Cook the pancakes","children":[{"type":"text","value":"Heat a skillet over medium heat and lightly grease it. Pour 1/4 cup of batter per pancake. Cook until bubbles form on the surface, then flip and cook until golden brown on both sides.","style":"body"}]},{"type":"divider"},{"type":"alert","title":"Chef's Tip","message":"Swap regular milk for buttermilk and add a splash of vanilla extract for extra fluffy, bakery-style pancakes.","severity":"info"},{"type":"divider"},{"type":"text_input","id":"customization","label":"Customizations","placeholder":"e.g., add blueberries, use oat milk, make gluten-free..."},{"type":"row","children":[{"type":"button","label":"Suggest Variations","variant":"filled","action":{"type":"callback","event":"suggest_variations","collectFrom":["customization"]}},{"type":"button","label":"More Recipes","variant":"outlined","action":{"type":"callback","event":"more_pancake_recipes"}}]}]}]}
```
