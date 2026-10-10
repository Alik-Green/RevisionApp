# Changelog

## v2.7.0 — 2026-10-10

- Rebuilt dark mode around the requested evergreen/slate surfaces and role-mapped Feather Green, Mask Green, Macaw Blue, Humpback Blue, Cardinal Red, Bee Yellow, Fox Orange and Beetle Purple accents; preserved the light palette.
- Tightened dark-mode insets, slightly reduced card/control rounding while keeping cards generously rounded, and increased smaller text by 1 sp.
- Strengthened dark headings and button labels, added blue secondary actions, bordered panels and state-aware raised controls, including course-path nodes.
- Made course-path progress, rewards, achievements and streak visuals follow the green/yellow/orange semantic roles.

## v2.6.0 — 2026-10-10

- Redesigned dark mode with near-black evergreen surfaces and five role-based bright accents; left the light palette and existing light-mode corner radii unchanged.
- Standardized dark-mode card and control shapes, refined shared headers and filter chips, and kept course-path and subject accents within the same palette.
- Replaced synthetic feedback tones with five short CC0 UI sounds for correct/incorrect answers, lesson and quest completion, and streak milestones. Playback remains optional in Settings.

## v2.5.0 — 2026-10-10

- Removed the Ink & Paper selector so Settings exposes a single colour style; kept the device-following light/dark default and gave dark mode a cool blue-green primary accent.
- Added a saved Settings switch for answer, lesson-completion, quest-completion and streak-extension sounds on desktop and Android.
- Added an explicit `isCuratedReview` marker for authored V2 review lessons, independent of lesson completion.
- Prepared a separate TMUA V2 JSON patch with 10 topics, 6 lessons per topic and 6 multiple-choice questions per lesson. It is not live in the Store until applied to the `course-content` branch.
- Replaced the narrow induction-step text answer in the separate TMUA patch with an objective multiple-choice question.

## v2.4.0 — 2026-10-10

- Turned the V2 course path into centered, gently winding, topic-coloured lesson circles; stars mark the next lesson and weights mark curated cross-topic review.
- Grouped hair and eye colours under their matching avatar features in both the character designer and Cosmetics.
- Raised light/dark palette contrast, used green for correct-answer feedback in either mode, and applied the friendly Nunito typeface.
- Removed the answer-format helper, made free-text marking more forgiving of small typos and close wording, and protected the direction of induction implications.
- Simplified lesson completion to the avatar, “Lesson complete!”, animated daily-quest progress and one Continue action back to the pathway.

## v2.3.0 — 2026-10-10

- Made hairstyles visibly distinct in the live avatar renderer and added adjustable face width, height and jaw shape.
- Redesigned the Cosmetics shelf with a fixed live preview, Hair/Eyes/Nose/Face categories, Style/Colour/Skin/Shape controls, and equip actions for owned parts.
- Rebuilt Store Courses as a selectable browse/detail flow with course icons, downloaded status, descriptions, lesson counts and opt-in Download/Open actions.
- Added a local developer-code entry in Settings. The unlock code enables an unlimited coin balance and the full current cosmetics catalog without storing the code itself.
- Simplified Study: removed its quest spotlight and large continue card, added a compact active-course info/switcher, and centered the topic-first path with star and practice/review icons.
- Randomly selects and persists three daily quests; raised course, store and quest cards above the page background with stronger borders and contrast.
- Added an edit action by the Profile avatar and compacted the active-course card.
- Made the default theme follow device light/dark settings and increased contrast in the Playful palette.

## v2.2.0 — 2026-10-10

- Kept light as the default for new installs and unset theme preferences.
- Expanded Profile's Character Designer with a drawn person avatar, eight free natural skin tones and coin-unlocked hairstyles, eye and nose styles, fantasy skin colours, hair colours and eye colours. Size, spacing and position controls remain free.
- Preserved existing v2.1 appearance unlocks and converted saved 0–100 shape values during startup.
- Added Store to bottom navigation, with separate Courses and Cosmetics shelves and no Store-root back button.
- Moved the full achievement gallery to its own page from Progress.
- Added a next-quest spotlight to Study and refreshed avatar previews across the learning experience.
