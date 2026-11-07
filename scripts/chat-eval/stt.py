#!/usr/bin/env python3
"""Generates synthetic speech with macOS `say` and transcribes it with candidate engines.

Usage: stt.py gen <audio_dir>
       stt.py run <audio_dir> <engine> <model_path> <out.jsonl>
Engines: whisper (ggml file), qwen3 (sherpa-onnx model dir).
Each result row carries id, noise, transcript, seconds of audio and of processing.
"""
import json
import pathlib
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).parent
CASES = json.load(open(HERE / "stt_cases.json"))["cases"]
THREADS = 4  # Roughly the big cores on a phone.


def gen(audio_dir):
    audio_dir.mkdir(parents=True, exist_ok=True)
    for c in CASES:
        clean = audio_dir / f"{c['id']}.clean.wav"
        aiff = audio_dir / f"{c['id']}.aiff"
        voice = "Majed (Arabic (World))" if c["voice"] == "Majed" else c["voice"]
        subprocess.run(["say", "-v", voice, "-o", str(aiff), c["spoken"]], check=True)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(aiff), "-ar", "16000", "-ac", "1", str(clean)], check=True)
        aiff.unlink()
        # Steady background noise about 15 dB under the speech, like a street or a fan.
        noisy = audio_dir / f"{c['id']}.noisy.wav"
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(clean),
                        "-f", "lavfi", "-i", "anoisesrc=c=pink:r=16000:a=0.05:s=7",
                        "-filter_complex", "[0:a][1:a]amix=inputs=2:duration=first:normalize=0",
                        "-ar", "16000", "-ac", "1", str(noisy)], check=True)
    print(f"generated {len(CASES) * 2} clips in {audio_dir}")


def read_wav(path):
    import soundfile
    samples, rate = soundfile.read(str(path), dtype="float32")
    return samples, rate


def run(audio_dir, engine, model, out):
    if engine == "whisper":
        from pywhispercpp.model import Model
        cache = {}

        def transcribe(samples, rate, lang):
            m = cache.get(lang)
            if m is None:
                m = cache[lang] = Model(model, n_threads=THREADS, language=lang, print_progress=False, print_realtime=False, redirect_whispercpp_logs_to=False)
            return " ".join(seg.text.strip() for seg in m.transcribe(samples)).strip()
    else:
        import sherpa_onnx
        d = pathlib.Path(model)
        rec = sherpa_onnx.OfflineRecognizer.from_qwen3_asr(
            conv_frontend=str(d / "conv_frontend.onnx"), encoder=str(d / "encoder.int8.onnx"),
            decoder=str(d / "decoder.int8.onnx"), tokenizer=str(d / "tokenizer"), num_threads=THREADS)

        def transcribe(samples, rate, lang):
            s = rec.create_stream()
            s.accept_waveform(rate, samples)
            rec.decode_stream(s)
            return s.result.text.strip()

    with open(out, "a") as f:
        for c in CASES:
            for noise in ("clean", "noisy"):
                samples, rate = read_wav(audio_dir / f"{c['id']}.{noise}.wav")
                t = time.perf_counter()
                text = transcribe(samples, rate, c["lang"])
                took = time.perf_counter() - t
                row = dict(id=c["id"], noise=noise, engine=engine, model=pathlib.Path(model).name, transcript=text, audio_s=round(len(samples) / rate, 2), proc_s=round(took, 3))
                f.write(json.dumps(row, ensure_ascii=False) + "\n")
                f.flush()
                print(c["id"], noise, f"{took:.2f}s", text)


if __name__ == "__main__":
    if sys.argv[1] == "gen":
        gen(pathlib.Path(sys.argv[2]))
    else:
        run(pathlib.Path(sys.argv[2]), sys.argv[3], sys.argv[4], sys.argv[5])
