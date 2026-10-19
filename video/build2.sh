#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

mkdir -p out/seg2
rm -f out/seg2/*.mp4 out/seg2/list.txt

echo "== score"
node tools/audio2.mjs

echo "== picture"
node tools/render.mjs --port 8811 --debug 9481 --page index2.html --from 0 --to 14 --out out/seg2/a.mp4 > out/seg2/a.log 2>&1 &
A=$!
node tools/render.mjs --port 8812 --debug 9482 --page index2.html --from 14 --to 29 --out out/seg2/b.mp4 > out/seg2/b.log 2>&1 &
B=$!
wait $A $B

printf "file 'a.mp4'\nfile 'b.mp4'\n" > out/seg2/list.txt
ffmpeg -y -hide_banner -loglevel error -f concat -safe 0 -i out/seg2/list.txt -c copy out/picture2.mp4

echo "== loudness"
ffmpeg -hide_banner -nostats -i out/score2.wav \
  -af "loudnorm=I=-14:TP=-1.5:LRA=9:print_format=json" -f null - 2> out/loudness2.json || true
read -r MI MT ML MTH MO < <(python3 - <<'PY'
import json, re
s = open('out/loudness2.json').read()
m = re.search(r'\{[^{}]*"input_i"[\s\S]*?\}', s)
d = json.loads(m.group(0))
print(d['input_i'], d['input_tp'], d['input_lra'], d['input_thresh'], d['target_offset'])
PY
)
echo "  measured I=$MI TP=$MT LRA=$ML"

ffmpeg -y -hide_banner -loglevel error \
  -i out/picture2.mp4 -i out/score2.wav \
  -map 0:v:0 -map 1:a:0 \
  -c:v copy \
  -af "loudnorm=I=-14:TP=-1.5:LRA=9:measured_I=$MI:measured_TP=$MT:measured_LRA=$ML:measured_thresh=$MTH:offset=$MO:linear=true" \
  -c:a aac -b:a 320k -ar 48000 \
  -movflags +faststart -shortest \
  out/dumbtv-launch-fast.mp4

echo "== poster"
ffmpeg -y -hide_banner -loglevel error -ss 27.0 -i out/dumbtv-launch-fast.mp4 -frames:v 1 out/poster-fast.png

echo "== result"
ffprobe -v error -show_entries format=duration,size:stream=codec_name,width,height,r_frame_rate -of default=nw=1 out/dumbtv-launch-fast.mp4
ffmpeg -hide_banner -nostats -i out/dumbtv-launch-fast.mp4 -af ebur128=peak=true -f null - 2>&1 | grep -A2 -E "Integrated|True peak" | head -8
