---
mode: dynamic_ui
slug: tutorial-lesson
description: Educational lesson mixing markdown explanation with interactive kai-ui elements
---

## User
Teach me about CSS Flexbox.

## Assistant
**CSS Flexbox** is a one-dimensional layout model that makes it easy to align and distribute space among items in a container. Here's the core concept:

A flex container has a **main axis** (horizontal by default) and a **cross axis** (perpendicular). Items flow along the main axis and can be aligned on both axes.

```kai-ui
{"type":"column","children":[{"type":"card","children":[{"type":"text","value":"Key Properties","style":"title"},{"type":"code","code":".container {\n  display: flex;\n  justify-content: center;  /* main axis */\n  align-items: center;      /* cross axis */\n  gap: 16px;\n}","language":"css"},{"type":"accordion","title":"justify-content values","children":[{"type":"list","items":["flex-start \u2014 pack items to the start","center \u2014 center items","flex-end \u2014 pack items to the end","space-between \u2014 equal space between items","space-around \u2014 equal space around items","space-evenly \u2014 equal space between and around"],"ordered":false}]},{"type":"accordion","title":"align-items values","children":[{"type":"list","items":["stretch \u2014 fill the container height (default)","flex-start \u2014 align to top","center \u2014 center vertically","flex-end \u2014 align to bottom","baseline \u2014 align to text baseline"],"ordered":false}]}]},{"type":"card","children":[{"type":"text","value":"Quick Quiz","style":"title"},{"type":"text","value":"How do you center an element both horizontally and vertically with Flexbox?","style":"body"},{"type":"radio_group","id":"quiz","label":"Choose the correct answer","options":["display: flex; text-align: center;","display: flex; justify-content: center; align-items: center;","display: flex; margin: auto;","display: flex; align-content: center;"]},{"type":"button","label":"Check Answer","action":{"type":"callback","event":"check_flexbox_answer","collectFrom":["quiz"]}}]},{"type":"row","children":[{"type":"button","label":"Try It Live","variant":"outlined","action":{"type":"callback","event":"flexbox_playground"}},{"type":"button","label":"Next: CSS Grid","variant":"text","action":{"type":"callback","event":"next_lesson"}}]}]}
```

The most common pattern you'll use is `display: flex; justify-content: center; align-items: center;` to center content. Want to try some hands-on examples?
