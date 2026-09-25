#!/usr/bin/env bash
# Lists Persian fine-tunes of Whisper on Hugging Face and checks the FLEURS fa_ir sample API.
set -u
for q in "whisper%20persian" "whisper%20farsi" "whisper-fa" "whisper%20fa"; do
  echo "=== SEARCH $q"
  curl -s "https://huggingface.co/api/models?search=$q&sort=downloads&direction=-1&limit=25&full=false" \
    | python3 -c "
import json,sys
for m in json.load(sys.stdin):
    print(m.get('downloads',0), m.get('likes',0), m['id'], (m.get('pipeline_tag') or ''), ','.join(t for t in m.get('tags',[]) if t in ('ggml','gguf','transformers','safetensors','pytorch','whisper')))
"
done
echo "=== FLEURS"
curl -s "https://datasets-server.huggingface.co/rows?dataset=google/fleurs&config=fa_ir&split=test&offset=0&length=2" | head -c 1500; echo
echo "=== COMMON VOICE"
curl -s "https://datasets-server.huggingface.co/rows?dataset=mozilla-foundation/common_voice_17_0&config=fa&split=test&offset=0&length=1" | head -c 600; echo
