## 2024-05-24 - Added Content Description to Retry Icon
**Learning:** Icon-only buttons frequently lack ARIA labels/content descriptions which makes them inaccessible for screen reader users.
**Action:** Identify `IconButton` components without associated text labels and ensure they have a proper `contentDescription`.
