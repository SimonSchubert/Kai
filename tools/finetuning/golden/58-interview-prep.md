---
mode: interactive
slug: interview-prep
description: AI presents interview question with difficulty selection and structured feedback
---

## User
Help me prepare for a software engineering interview.

## Assistant
```kai-ui
{"type":"column","children":[{"type":"text","value":"Interview Prep","style":"headline"},{"type":"row","children":[{"type":"badge","value":"Question 1","color":"secondary"},{"type":"badge","value":"System Design","color":"primary"}]},{"type":"card","children":[{"type":"text","value":"Design a URL shortening service like bit.ly","style":"title"},{"type":"text","value":"The interviewer expects you to discuss the system architecture, data storage, API design, and how to handle high traffic. Think about the key requirements before diving into the design.","style":"body"},{"type":"divider"},{"type":"text","value":"Key points to cover:","style":"body","color":"secondary"},{"type":"list","items":["Requirements: How many URLs per day? Read vs write ratio?","API: What endpoints are needed?","Database: SQL vs NoSQL? How to store mappings?","Hash generation: How to create short, unique IDs?","Scaling: Caching, load balancing, database sharding"],"ordered":true}]},{"type":"card","children":[{"type":"text","value":"How would you approach this?","style":"title"},{"type":"radio_group","id":"approach","label":"Pick your starting point","options":["Start with requirements gathering","Jump into API design","Begin with database schema","Focus on the hashing algorithm"]},{"type":"button","label":"Check My Approach","action":{"type":"callback","event":"check_approach","collectFrom":["approach"]}}]},{"type":"row","children":[{"type":"button","label":"Show Sample Answer","variant":"outlined","action":{"type":"callback","event":"show_answer"}},{"type":"button","label":"Different Question","variant":"text","action":{"type":"callback","event":"next_question"}},{"type":"button","label":"Switch Topic","variant":"text","action":{"type":"callback","event":"switch_topic"}}]}]}
```
