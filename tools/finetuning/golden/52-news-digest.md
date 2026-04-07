---
mode: dynamic_ui
slug: news-digest
description: News summary with article cards and topic actions
---

## User
What's happening in tech today?

## Assistant
Here's your tech news roundup for today:

```kai-ui
{"type":"column","children":[{"type":"card","children":[{"type":"badge","value":"Breaking","color":"error"},{"type":"text","value":"EU Passes New AI Regulation Framework","style":"title","bold":true},{"type":"text","value":"The European Parliament has approved comprehensive rules requiring AI systems to be transparent about training data and disclose when content is AI-generated.","style":"body"},{"type":"row","children":[{"type":"button","label":"Read More","action":{"type":"callback","event":"read_article","data":{"id":"eu-ai"}}},{"type":"button","label":"Summary","variant":"text","action":{"type":"callback","event":"summarize","data":{"id":"eu-ai"}}}]}]},{"type":"card","children":[{"type":"badge","value":"Trending","color":"primary"},{"type":"text","value":"Rust Overtakes C++ in Stack Overflow Survey","style":"title","bold":true},{"type":"text","value":"For the first time, Rust has been named the most used systems programming language, surpassing C++ in the annual developer survey.","style":"body"},{"type":"row","children":[{"type":"button","label":"Read More","action":{"type":"callback","event":"read_article","data":{"id":"rust-survey"}}},{"type":"button","label":"Summary","variant":"text","action":{"type":"callback","event":"summarize","data":{"id":"rust-survey"}}}]}]},{"type":"card","children":[{"type":"badge","value":"Launch","color":"secondary"},{"type":"text","value":"New Open-Source LLM Beats GPT-4 on Benchmarks","style":"title","bold":true},{"type":"text","value":"A consortium of universities has released an open-weight model that outperforms proprietary alternatives on reasoning and coding benchmarks.","style":"body"},{"type":"row","children":[{"type":"button","label":"Read More","action":{"type":"callback","event":"read_article","data":{"id":"open-llm"}}},{"type":"button","label":"Summary","variant":"text","action":{"type":"callback","event":"summarize","data":{"id":"open-llm"}}}]}]},{"type":"row","children":[{"type":"button","label":"More Headlines","variant":"outlined","action":{"type":"callback","event":"more_news"}},{"type":"button","label":"Change Topics","variant":"text","action":{"type":"callback","event":"change_topics"}}]}]}
```

Want me to go deeper on any of these stories?
