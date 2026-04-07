---
mode: interactive
slug: complex-booking
description: Complex booking form with multiple input types
---

## User
Design a booking screen for a restaurant reservation: date picker (use select for date and time), party size slider, special requests text input, dietary restrictions chips, and a confirm button.

## Assistant
```kai-ui
{"type":"column","children":[{"type":"text","value":"Restaurant Reservation","style":"headline"},{"type":"card","children":[{"type":"text","value":"When would you like to dine?","style":"title"},{"type":"select","id":"date","label":"Date","options":["Today","Tomorrow","This Saturday","This Sunday","Next Week"],"selected":"Tomorrow"},{"type":"select","id":"time","label":"Time","options":["6:00 PM","6:30 PM","7:00 PM","7:30 PM","8:00 PM","8:30 PM","9:00 PM"],"selected":"7:00 PM"}]},{"type":"card","children":[{"type":"text","value":"Party Details","style":"title"},{"type":"slider","id":"party_size","label":"Party Size","value":2,"min":1,"max":12,"step":1},{"type":"text_input","id":"special_requests","label":"Special Requests","placeholder":"e.g., high chair needed, birthday celebration"}]},{"type":"card","children":[{"type":"text","value":"Dietary Restrictions","style":"title"},{"type":"chip_group","id":"dietary","chips":[{"label":"Vegetarian","value":"vegetarian"},{"label":"Vegan","value":"vegan"},{"label":"Gluten-Free","value":"gluten_free"},{"label":"Nut Allergy","value":"nut_allergy"},{"label":"Dairy-Free","value":"dairy_free"},{"label":"Halal","value":"halal"}],"selection":"multi"}]},{"type":"row","children":[{"type":"button","label":"Confirm","action":{"type":"callback","event":"confirm_reservation","collectFrom":["date","time","party_size","special_requests","dietary"]}},{"type":"button","label":"View Menu","variant":"outlined","action":{"type":"callback","event":"view_menu"}},{"type":"button","label":"Cancel","variant":"text","action":{"type":"callback","event":"cancel_reservation"}}]}]}
```
