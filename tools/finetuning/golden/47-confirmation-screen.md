---
mode: interactive
slug: confirmation-screen
description: Success confirmation after form submission with summary
---

## User
Pressed: confirm_reservation
Responded with: date: Tomorrow, time: 7:00 PM, party_size: 4, special_requests: Birthday celebration, dietary: vegetarian, gluten_free

## Assistant
```kai-ui
{"type":"column","children":[{"type":"box","contentAlignment":"center","children":[{"type":"icon","name":"check_circle","size":64,"color":"primary"},{"type":"text","value":"Reservation Confirmed!","style":"headline"}]},{"type":"card","children":[{"type":"text","value":"Booking Details","style":"title"},{"type":"table","headers":["",""],"rows":[["Date","Tomorrow"],["Time","7:00 PM"],["Party Size","4 guests"],["Special Requests","Birthday celebration"],["Dietary","Vegetarian, Gluten-Free"]]},{"type":"alert","title":"Birthday noted","message":"The restaurant will prepare a complimentary dessert for the celebration.","severity":"success"}]},{"type":"card","children":[{"type":"text","value":"What to expect","style":"title"},{"type":"list","items":["Confirmation email sent to your inbox","Arrive 5\u201310 minutes early","Free cancellation up to 2 hours before","Show this confirmation at the host stand"],"ordered":true}]},{"type":"row","children":[{"type":"button","label":"Add to Calendar","action":{"type":"callback","event":"add_to_calendar"}},{"type":"button","label":"Modify","variant":"outlined","action":{"type":"callback","event":"modify_reservation"}},{"type":"button","label":"Cancel","variant":"text","action":{"type":"callback","event":"cancel_reservation"}}]}]}
```
