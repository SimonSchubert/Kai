---
mode: dynamic_ui
slug: pros-cons
description: Decision helper with pros and cons layout
---

## User
Should I learn Kotlin or Swift first?

## Assistant
Both are great modern languages. It depends on your goals — here's a breakdown:

```kai-ui
{"type":"column","children":[{"type":"row","children":[{"type":"card","children":[{"type":"row","children":[{"type":"icon","name":"\ud83d\udfe3","size":20},{"type":"text","value":"Kotlin","style":"title","bold":true}]},{"type":"text","value":"Pros","style":"body","color":"primary"},{"type":"list","items":["Android + server + multiplatform","Java interop \u2014 huge ecosystem","Growing demand in enterprise","Compose Multiplatform for UI"],"ordered":false},{"type":"text","value":"Cons","style":"body","color":"error"},{"type":"list","items":["Steeper learning curve than Swift","Compile times can be slow","Smaller iOS community"],"ordered":false}]},{"type":"card","children":[{"type":"row","children":[{"type":"icon","name":"\ud83d\udfe0","size":20},{"type":"text","value":"Swift","style":"title","bold":true}]},{"type":"text","value":"Pros","style":"body","color":"primary"},{"type":"list","items":["Native iOS + macOS development","Clean, beginner-friendly syntax","Excellent tooling with Xcode","Strong Apple ecosystem"],"ordered":false},{"type":"text","value":"Cons","style":"body","color":"error"},{"type":"list","items":["Limited to Apple platforms","Smaller server-side community","Less multiplatform reach"],"ordered":false}]}]},{"type":"card","children":[{"type":"text","value":"My recommendation","style":"title"},{"type":"text","value":"Want to build Android apps or cross-platform?","style":"body"},{"type":"button","label":"Go with Kotlin","variant":"tonal","action":{"type":"callback","event":"choose","data":{"language":"kotlin"}}},{"type":"text","value":"Want to build iOS/Mac apps?","style":"body"},{"type":"button","label":"Go with Swift","variant":"tonal","action":{"type":"callback","event":"choose","data":{"language":"swift"}}},{"type":"text","value":"Not sure yet?","style":"body"},{"type":"button","label":"Help Me Decide","variant":"outlined","action":{"type":"callback","event":"help_decide"}}]}]}
```

The bottom line: if you're in the Android/multiplatform world, Kotlin is the clear choice. If you're targeting Apple devices, Swift is the way to go.
