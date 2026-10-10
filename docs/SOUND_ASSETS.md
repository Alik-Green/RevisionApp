# Sound assets

RevisionApp uses five short feedback sounds from the **arcade** sound pack in
[romainsimon/uisfx](https://github.com/romainsimon/uisfx), pinned to commit
`9950fe66f993a6660dab9c2651dcbcd899ffd83b`. The source repository dedicates
its generated audio to the public domain under **CC0 1.0**; attribution is not
required. The upstream `packages/uisfx/LICENSE-AUDIO` notice is preserved at
[`docs/licenses/UISFX-LICENSE-AUDIO.txt`](licenses/UISFX-LICENSE-AUDIO.txt).

| App event | Upstream cue | Bundled resource |
| --- | --- | --- |
| Correct answer | `success.mp3` | `sfx/correct.wav` |
| Incorrect answer | `error.mp3` | `sfx/incorrect.wav` |
| Lesson completion | `level-up.mp3` | `sfx/lesson-complete.wav` |
| Quest completion | `reward.mp3` | `sfx/quest-complete.wav` |
| Streak extension | `streak.mp3` | `sfx/streak-extended.wav` |

The MP3 cues were converted to 44.1 kHz, mono, signed 16-bit PCM WAV so the same
small assets work with Android `MediaPlayer` and the desktop Java Sound API.
Playback remains optional and is controlled by the saved Settings switch. Audio
reinforces the visible answer and progression feedback; it does not replace it.
