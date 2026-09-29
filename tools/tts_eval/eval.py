"""Reads the same Persian sentences with every voice, times it, and judges clarity by
transcribing the audio back with a Persian Whisper model (character error rate)."""
import os
import sys
import time
import unicodedata

import numpy as np
import sherpa_onnx
import soundfile as sf

SENTENCES = [
    "فردا ساعت نه صبح جلسه با مدیرعامل داری.",
    "سه کار عقب‌افتاده به امروز منتقل شد.",
    "یادت نره قبض برق را تا آخر هفته پرداخت کنی.",
    "امروز پنج‌شنبه، دوم مهر است و هوا کمی خنک شده.",
    "برای تمرکز بیشتر، گوشی را بی‌صدا کن و بیست و پنج دقیقه کار کن.",
    "کتاب را تا صفحهٔ صد و بیست خواندم و فردا ادامه می‌دهم.",
    "جلسهٔ هفتگی تیم فروش به سه‌شنبه ساعت چهار بعدازظهر منتقل شد.",
    "سلام! من روزبان هستم و کمکت می‌کنم روزت را بهتر برنامه‌ریزی کنی.",
]


def engine(d, vocoder):
    name = os.path.basename(d)
    tokens = os.path.join(d, "tokens.txt")
    data = os.path.join(d, "espeak-ng-data")
    if vocoder:
        model = sherpa_onnx.OfflineTtsModelConfig(
            matcha=sherpa_onnx.OfflineTtsMatchaModelConfig(
                acoustic_model=os.path.join(d, "model.onnx"), vocoder=vocoder, tokens=tokens, data_dir=data),
            num_threads=4)
    else:
        onnx = [f for f in os.listdir(d) if f.endswith(".onnx")][0]
        model = sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=os.path.join(d, onnx), tokens=tokens, data_dir=data),
            num_threads=4)
    return sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=model))


def norm(s):
    s = unicodedata.normalize("NFKC", s).replace("ي", "ی").replace("ك", "ک")
    return "".join(c for c in s if c.isalnum())


def cer(ref, hyp):
    r, h = norm(ref), norm(hyp)
    d = list(range(len(h) + 1))
    for i in range(1, len(r) + 1):
        prev, d[0] = d[0], i
        for j in range(1, len(h) + 1):
            cur = d[j]
            d[j] = min(d[j] + 1, d[j - 1] + 1, prev + (r[i - 1] != h[j - 1]))
            prev = cur
    return d[len(h)] / max(1, len(r))


def main(root, out):
    os.makedirs(out, exist_ok=True)
    from transformers import pipeline
    import librosa
    asr = pipeline("automatic-speech-recognition", model="C1Tech/whisper_base_persian", device="cpu")

    # "<dir>" or "<dir>+<vocoder file>" (Matcha models need a vocoder).
    wanted = os.environ.get("VOICES", "").split()
    if not wanted:
        wanted = [d if not d.startswith("matcha") else d + "+vocos-22khz-univ.onnx"
                  for d in sorted(os.listdir(root)) if os.path.isdir(os.path.join(root, d))]
    rows = []
    for spec in wanted:
        d, _, voc = spec.partition("+")
        v = d + ("-" + voc.split(".")[0] if voc else "")
        tts = engine(os.path.join(root, d), os.path.join(root, voc) if voc else None)
        synth = audio = 0.0
        errors = []
        sample = []
        for i, text in enumerate(SENTENCES):
            t = time.time()
            a = tts.generate(text, sid=0, speed=1.0)
            synth += time.time() - t
            samples = np.array(a.samples, dtype=np.float32)
            audio += len(samples) / a.sample_rate
            if i in (0, 2, 7):
                sample += [samples, np.zeros(a.sample_rate // 2, dtype=np.float32)]
            wav16 = librosa.resample(samples, orig_sr=a.sample_rate, target_sr=16000)
            hyp = asr(wav16, generate_kwargs={"language": "persian", "task": "transcribe"})["text"]
            e = cer(text, hyp)
            errors.append(e)
            print(f"  {v} [{i}] cer={e:.2f} | {hyp}", flush=True)
        sf.write(os.path.join(out, f"compare-{v}.wav"), np.concatenate(sample), a.sample_rate)
        rows.append((v, float(np.mean(errors)), synth / audio))
        print(f"RESULT {v} cer={np.mean(errors):.3f} rtf={synth / audio:.3f}", flush=True)
    print("\nSUMMARY (lower CER = clearer)")
    for v, e, rtf in sorted(rows, key=lambda r: r[1]):
        print(f"SUMMARY\t{v}\t{e:.3f}\t{rtf:.3f}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
