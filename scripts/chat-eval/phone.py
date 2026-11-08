#!/usr/bin/env python3
"""Runs a case file on a phone through the debug app's LlmProbeReceiver, with the Chat screen's own config.
Same output as Eval.kt, so score.py and ChatReplayTest.kt read it. The system prompt is the synthetic one below.

  phone.py <serial> <cases.json> <out.jsonl> [backend=gpu] [seeds=1,2,3] [repeat=1] [fresh=0] [only=prefix]

repeat runs each case and seed that many times, to see whether identical runs differ. fresh=1 restarts the
engine for every run. The debug build must hold the model (or download it in the app).
"""
import json
import re
import shlex
import subprocess
import sys
import time

PKG = "com.pennywiseai.tracker.debug"
EXPENSE = "Food & Dining, Groceries, Transportation, Shopping, Bills & Utilities, Entertainment, Healthcare, Investments, Banking, Personal Care, Education, Mobile, Fitness, Insurance, Travel, Others"


def system_prompt(base):
    src = open("../../app/src/main/java/com/pennywiseai/tracker/data/repository/LlmRepository.kt", encoding="utf-8").read()
    body = re.search(r'\): String = """\n(.*?)"""\.trimIndent\(\)', src, re.S).group(1)
    symbol = {"INR": "₹", "SAR": "SR ", "EGP": "E£", "JOD": "JD "}.get(base, base + " ")
    text = "\n".join(line.strip() for line in body.splitlines()).strip()
    return (text.replace("$currentDate", "2026-11-04")
            .replace("$monthLine", f"{symbol}18,450 spent, {symbol}52,000 income, 37 transactions")
            .replace("${promptList(expenseCategories, MAX_EXPENSE_CATEGORY_CHARS)}", EXPENSE)
            .replace("${promptList(incomeCategories, MAX_INCOME_CATEGORY_CHARS)}", "Salary, Income")
            .replace('${promptList(accounts, MAX_ACCOUNT_CHARS).ifEmpty { "none yet" }}', "HDFC 1234, Cash wallet"))


def adb(serial, *args, check=True):
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True, check=check).stdout


def cat(serial, name):
    return subprocess.run(["adb", "-s", serial, "exec-out", "run-as", PKG, "cat", f"cache/{name}"], capture_output=True, text=True).stdout


def run_one(serial, system, text, backend, seed, fresh):
    adb(serial, "shell", "run-as", PKG, "rm", "-f", "cache/llm_probe_error.txt", check=False)
    adb(serial, "logcat", "-c")
    shell = ["am", "broadcast", "-n", f"{PKG}/com.pennywiseai.tracker.debug.LlmProbeReceiver", "-a", "LLM_PROBE",
             "--es", "mode", "chat", "--es", "backend", backend, "--ei", "seed", str(seed),
             "--ez", "freshEngine", "true" if fresh else "false", "--es", "system", system, "--es", "text", text]
    adb(serial, "shell", " ".join(shlex.quote(a) for a in shell))
    start = time.time()
    while time.time() - start < 300:
        log = adb(serial, "logcat", "-d", "-s", "LlmProbe")
        m = re.search(r"RESULT .*load=(\d+)ms gen=(\d+)ms", log)
        if m:
            reply = cat(serial, "llm_probe_reply.txt")
            calls = json.loads(cat(serial, "llm_probe_calls.json") or "[]")
            return dict(load_ms=int(m[1]), total_ms=int(m[2]), text=reply.split("] TEXT=", 1)[-1], calls=calls)
        if "FAILED:" in log:
            return dict(load_ms=0, total_ms=int((time.time() - start) * 1000), text="", calls=[], error=cat(serial, "llm_probe_error.txt"))
        time.sleep(1)
    return dict(load_ms=0, total_ms=300000, text="", calls=[], error="timeout")


def main():
    serial, cases_path, out = sys.argv[1:4]
    opts = dict(a.split("=", 1) for a in sys.argv[4:])
    backend, repeat, fresh = opts.get("backend", "gpu"), int(opts.get("repeat", 1)), opts.get("fresh", "0") == "1"
    seeds = [int(s) for s in opts.get("seeds", "1").split(",")]
    cases = [c for c in json.load(open(cases_path))["cases"] if c["id"].startswith(opts.get("only", ""))]
    with open(out, "a", encoding="utf-8") as f:
        for c in cases:
            system = system_prompt(c.get("base", "INR"))
            for seed in seeds:
                for _ in range(repeat):
                    r = run_one(serial, system, c["text"], backend, seed, fresh)
                    row = dict(id=c["id"], rep=seed, shape="app", temp=0.8, topk=10, model="Qwen2.5-1.5B-phone", backend=backend.upper(), **r)
                    f.write(json.dumps(row, ensure_ascii=False) + "\n")
                    f.flush()
                    print(c["id"], seed, f"{r['total_ms']}ms calls={len(r['calls'])}", (r.get("error") or "")[:80], flush=True)


if __name__ == "__main__":
    main()
