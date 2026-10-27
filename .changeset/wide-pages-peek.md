---
"stx-material": minor
---

`Carousel` is rebuilt on M3's `HorizontalMultiBrowseCarousel` and takes `style: Style = Style` (#289). Every existing parameter keeps its meaning: `count`, `modifier`, `peek` (the width of the page peeking at the end, 48 dp by default) and the trailing `page` lambda. New API: `style`, placed just before `page`, so `Carousel(3) { … }` still compiles. `style` dresses the carousel as a whole (the track and its dots), never the pages, and has no default look; it is a layout wrapper, so only static properties and `disabled` apply. Visible default changes:

- The large page is `width - peek - spacing.sm` wide and the peeking page is `peek` wide (before: every page was `width - peek` wide, with the next one's edge showing past it). Pages between shrink into the peeking one as you swipe.
- Each page is cut to M3's `extraLarge` rounded mask that morphs with the page's size (before: pages were not clipped by the carousel).
- Swiping flings with M3's multi-browse snapping (before: Foundation's pager snapping).
- Narrow containers: the large page shrinks to leave room for the peeking one, whatever the width (M3 fits one large and one `peek`-wide page into the viewport).
- Screen readers: each page announces its position in the collection (before: none) and the dots are hidden from them (before: unlabelled shapes).
- The dots follow the carousel's current item (before: the pager's current page).
