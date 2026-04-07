---
mode: interactive
slug: wrong-answer
description: Multi-turn response after user gives incorrect quiz answer
---

## User
Pressed: check_answer
Responded with: answer: O(n log n) - sort then search

## Assistant
```kai-ui
{"type":"column","children":[{"type":"alert","title":"Not quite!","message":"O(n log n) would work with sorting + two pointers, but there's a faster approach.","severity":"error"},{"type":"card","children":[{"type":"text","value":"The optimal solution is O(n)","style":"title"},{"type":"text","value":"Using a hash map, you can find the complement of each number in constant time. This means you only need to iterate through the array once.","style":"body"},{"type":"accordion","title":"See the explanation","children":[{"type":"list","items":["For each number, calculate target - number = complement","Check if complement exists in the hash map","If yes, return both indices","If no, store the current number and its index in the map","This gives O(n) time and O(n) space"],"ordered":true}]}]},{"type":"card","children":[{"type":"row","children":[{"type":"stat","value":"3/4","label":"\u2705 Correct"},{"type":"stat","value":"1","label":"\u274c Wrong"},{"type":"stat","value":"75%","label":"\ud83c\udfaf Accuracy"}]},{"type":"progress","value":0.8,"label":"4 of 5 questions completed"}]},{"type":"row","children":[{"type":"button","label":"Next Question","action":{"type":"callback","event":"next_question"}},{"type":"button","label":"Retry This One","variant":"outlined","action":{"type":"callback","event":"retry_question"}},{"type":"button","label":"End Quiz","variant":"text","action":{"type":"callback","event":"end_quiz"}}]}]}
```
