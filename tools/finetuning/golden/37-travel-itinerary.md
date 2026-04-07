---
mode: interactive
slug: travel-itinerary
description: Travel itinerary with accordion days, ordered schedule, badges, alerts, image
---

## User
Show me my trip itinerary for Tokyo.

## Assistant
```kai-ui
{"type":"column","children":[{"type":"text","value":"Tokyo Trip","style":"headline"},{"type":"row","children":[{"type":"badge","value":"\u2708\ufe0f Apr 15\u201322","color":"primary"},{"type":"badge","value":"7 nights","color":"secondary"},{"type":"badge","value":"\ud83c\uddef\ud83c\uddf5 Japan","color":"secondary"}]},{"type":"image","url":"https://upload.wikimedia.org/wikipedia/commons/b/b2/Skyscrapers_of_Shinjuku_2009_January.jpg","aspectRatio":2.0},{"type":"alert","title":"Reminder","message":"Your flight departs in 8 days. Passport and JR Pass confirmation printed?","severity":"info"},{"type":"accordion","title":"Day 1 \u2014 Arrival & Shinjuku","expanded":true,"children":[{"type":"list","items":["Arrive at Narita Airport (14:30)","Activate JR Pass at airport station","Train to Shinjuku hotel \u2014 check in","Explore Shinjuku Gyoen garden","Dinner at Omoide Yokocho alley"],"ordered":true}]},{"type":"accordion","title":"Day 2 \u2014 Shibuya & Harajuku","children":[{"type":"list","items":["Meiji Shrine morning visit","Takeshita Street shopping","Lunch at conveyor belt sushi","Shibuya Crossing & Hachiko statue","Shibuya Sky observation deck (sunset)"],"ordered":true}]},{"type":"accordion","title":"Day 3 \u2014 Asakusa & Akihabara","children":[{"type":"list","items":["Senso-ji Temple & Nakamise Street","Traditional matcha tea experience","Lunch in Asakusa","Akihabara electronics & anime district","Owl caf\u00e9 visit"],"ordered":true}]},{"type":"accordion","title":"Days 4\u20137 \u2014 Day Trips & More","children":[{"type":"text","value":"Day trips to Mt. Fuji, Kamakura, and Nikko. Final day for Tsukiji Market and last-minute shopping.","style":"body"}]},{"type":"card","children":[{"type":"row","children":[{"type":"stat","value":"\u20ac1,850","label":"\ud83d\udcb0 Budget"},{"type":"stat","value":"\u20ac620","label":"\u2708\ufe0f Flights"},{"type":"stat","value":"\u20ac490","label":"\ud83c\udfe8 Hotel"}]},{"type":"row","children":[{"type":"button","label":"Edit Trip","action":{"type":"callback","event":"edit_trip"}},{"type":"button","label":"Packing List","variant":"outlined","action":{"type":"callback","event":"packing_list"}},{"type":"button","label":"Share","variant":"text","action":{"type":"callback","event":"share_trip"}}]}]}]}
```
