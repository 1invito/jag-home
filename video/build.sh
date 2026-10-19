#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

mkdir -p out/seg
rm -f out/seg/*.mp4 out/seg/list.txt

echo "== score"
node tools/audio.mjs

echo "== picture (3 segments in parallel)"
node tools/render.mjs --port 8801 --debug 9471 --from 0 --to 51 --out out/seg/a.mp4 > out/seg/a.log 2>&1 &
A=$!
node tools/render.mjs --port 8802 --debug 9472 --from 51 --to 102 --out out/seg/b.mp4 > out/seg/b.log 2>&1 &
B=$!
node tools/render.mjs --port 8803 --debug 9473 --from 102 --to 153 --out out/seg/c.mp4 > out/seg/c.log 2>&1 &
C=$!
wait $A $B $C

printf "file 'a.mp4'\nfile 'b.mp4'\nfile 'c.mp4'\n" > out/seg/list.txt
ffmpeg -y -hide_banner -loglevel error -f concat -safe 0 -i out/seg/list.txt -c copy out/picture.mp4

echo "== loudness (2-pass -14 LUFS, -1.5 dBTP)"
ffmpeg -hide_banner -nostats -i out/score.wav \
  -af "loudnorm=I=-14:TP=-1.5:LRA=11:print_format=json" -f null - 2> out/loudness.json || true
read -r MI MT ML MTH MO < <(python3 - <<'PY'
import json, re
s = open('out/loudness.json').read()
m = re.search(r'\{[^{}]*"input_i"[\s\S]*?\}', s)
d = json.loads(m.group(0))
print(d['input_i'], d['input_tp'], d['input_lra'], d['input_thresh'], d['target_offset'])
PY
)
echo "  measured I=$MI TP=$MT LRA=$ML"

ffmpeg -y -hide_banner -loglevel error \
  -i out/picture.mp4 -i out/score.wav \
  -map 0:v:0 -map 1:a:0 \
  -c:v copy \
  -af "loudnorm=I=-14:TP=-1.5:LRA=11:measured_I=$MI:measured_TP=$MT:measured_LRA=$ML:measured_thresh=$MTH:offset=$MO:linear=true" \
  -c:a aac -b:a 320k -ar 48000 \
  -movflags +faststart \
  -shortest \
  out/dumbtv-launch.mp4

echo "== poster"
ffmpeg -y -hide_banner -loglevel error -ss 110 -i out/dumbtv-launch.mp4 -frames:v 1 out/poster.png

echo "== result"
ffprobe -v error -show_entries format=duration,size:stream=codec_name,width,height,r_frame_rate -of default=nw=1 out/dumbtv-launch.mp4
ffmpeg -hide_banner -nostats -i out/dumbtv-launch.mp4 -af ebur128=peak=true -f null - 2>&1 | grep -A2 -E "Integrated|True peak" | head -8
