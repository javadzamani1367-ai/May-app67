#!/usr/bin/env bash
# Converts a Hugging Face Whisper fine-tune to a quantized whisper.cpp model.
# usage: convert.sh <hf-repo> <quant e.g. q5_1> <out.bin>   (needs whisper-quantize on PATH dir ./wbuild/bin)
set -euo pipefail
repo=$1; quant=$2; out=$3
work=$(mktemp -d)
python3 - "$repo" "$work/hf" <<'PY'
import json, os, sys, shutil
from huggingface_hub import snapshot_download
repo, dst = sys.argv[1], sys.argv[2]
p = snapshot_download(repo, local_dir=dst, allow_patterns=["*.json", "*.safetensors", "*.bin", "*.txt", "*.model"], ignore_patterns=["optimizer*", "training_args*", "*.msgpack", "*.h5"])
cfg = json.load(open(os.path.join(dst, "config.json")))
print("config:", cfg.get("_name_or_path"), cfg.get("d_model"), cfg.get("encoder_layers"), cfg.get("decoder_layers"), cfg.get("num_mel_bins"))
# Fine-tunes often ship only tokenizer.json; the converter needs the base model's vocab files.
need = [f for f in ("vocab.json", "added_tokens.json", "normalizer.json") if not os.path.exists(os.path.join(dst, f))]
if need:
    d, enc, dec, mels = cfg["d_model"], cfg["encoder_layers"], cfg["decoder_layers"], cfg.get("num_mel_bins", 80)
    base = {384: "openai/whisper-tiny", 512: "openai/whisper-base", 768: "openai/whisper-small", 1024: "openai/whisper-medium"}.get(d)
    if d == 1280:
        base = "openai/whisper-large-v3-turbo" if dec == 4 else ("openai/whisper-large-v3" if mels == 128 else "openai/whisper-large-v2")
    print("tokenizer files from", base, need)
    b = snapshot_download(base, allow_patterns=need)
    for f in need:
        if os.path.exists(os.path.join(b, f)):
            shutil.copy(os.path.join(b, f), os.path.join(dst, f))
# safetensors -> pytorch_model.bin for the converter
if not os.path.exists(os.path.join(dst, "pytorch_model.bin")):
    from transformers import WhisperForConditionalGeneration
    m = WhisperForConditionalGeneration.from_pretrained(dst)
    m.save_pretrained(dst, safe_serialization=False)
PY
[ -d "$work/openai-whisper" ] || git clone -q --depth 1 https://github.com/openai/whisper "$work/openai-whisper"
python3 ai/runtime/src/main/cpp/whisper.cpp/models/convert-h5-to-ggml.py "$work/hf" "$work/openai-whisper" "$work"
./wbuild/bin/whisper-quantize "$work/ggml-model.bin" "$out" "$quant"
ls -l "$out"; sha256sum "$out"
rm -rf "$work"
