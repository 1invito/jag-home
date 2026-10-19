# Launch film

`dumbtv-launch-fast.mp4` is the 29 s launch cut: 120 BPM, full-bleed colour
plates, hard cuts on the beat, kinetic type. 1920×1080, 30 fps, H.264 + AAC,
−14 LUFS.

`build.sh` still builds the 2:33 documentary cut if you want it; it is not kept
in `out/`.

Everything is rendered from `src/timeline.js`: picture, score and sound design
read the same cue list, so cuts, taps and table rows land with the music.

```
./build2.sh         # 29 s launch cut → out/dumbtv-launch-fast.mp4
./build.sh          # 2:33 long cut → out/dumbtv-launch.mp4
```

| Piece | What it is |
|---|---|
| `src/timeline2.js` | the 29 s cut: cards, stats, verify rows, cues |
| `src/film2.js` | the 29 s cut: plates, kinetic type, odometer numbers, transitions |
| `src/timeline.js` | the long cut: scene times, copy beats, terminal lines, verify rows, sound cues |
| `src/design.js` | palette, type, panel/terminal drawing, grain and vignette |
| `src/film.js` | the eleven scenes and the master timeline |
| `tools/render.mjs` | headless Chrome → PNG frames → ffmpeg; `--stills`, `--audit` |
| `tools/audio2.mjs` | the 29 s score: 120 BPM, kick/bass/stabs, synthesized |
| `tools/audio.mjs` | the long cut's score, synthesized with no samples |

`node tools/render.mjs --stills 30,52,110 --out out/check` renders single frames.
`node tools/render.mjs --audit 30,52,110` reports any text that leaves the frame
or collides with other text.

The home screen in `assets/home.png` is a real capture of `tv.dumbtv.home` on the
test TV. The score is original; there is no stock audio. Rebuild time is about
six minutes on an 8-core Mac.
