# Dark theme

The Playful light palette is intentionally unchanged. The dark palette uses the
requested evergreen/slate surfaces and assigns each bright accent a clear role.
DIN Next Rounded is not bundled; the app continues to use the bundled Nunito
variable font, with dark-mode headings and button labels strengthened and small
body/label styles enlarged by 1 sp.

| Role | Colour | Use |
| --- | --- | --- |
| Main background | `#131F24` | App canvas |
| Secondary background | `#17272E` | Navigation and low-elevation panels |
| Card surface | `#202F36` | Cards and grouped content |
| Subtle / standard border | `#263941` / `#37464F` | Section boundaries and control outlines |
| Primary text | `#F1F7FB` | Main content and headings |
| Feather Green | `#58CC02` | Primary actions, correct answers, progress and active states |
| Mask Green | `#89E219` | Highlights and current lesson-node rims |
| Macaw Blue | `#1CB0F6` | Links, hints, information and secondary actions |
| Humpback Blue | `#2B70C9` | Deeper accents and selected graphics |
| Cardinal Red | `#FF4B4B` | Incorrect answers, errors and lost hearts |
| Bee Yellow | `#FFC800` | Achievements, rewards and highlights |
| Fox Orange | `#FF9600` | Streaks, fire and attention |
| Beetle Purple | `#CE82FF` | Premium/playful accents and selected course art |

Verdict, reward, streak and premium containers use quieter tinted versions of
their accents with high-contrast text. Lesson nodes and primary controls have
state-aware raised edges; section cards use visible borders. Dark-mode insets
are tightened slightly and large card/control corners are reduced while staying
rounded (the shape scale is 6/10/12/14/18 dp). Existing light-mode colour, type
size, spacing and corner treatments remain unchanged.
