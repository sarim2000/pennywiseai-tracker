#!/usr/bin/env python3
"""Scores the voice pipeline runs in results/. Usage: voice_score.py"""
import collections
import json

import score

cases = {}
for f in ("cases_voice_all.json", "cases_voice_norm.json"):
    for c in json.load(open(f"results/{f}"))["cases"]:
        cases[c["id"]] = c
agg = collections.defaultdict(list)
for f in ("voice_a", "voice_b", "voice_g", "voice_norm_a", "voice_norm_b"):
    for line in open(f"results/{f}.jsonl"):
        r = json.loads(line)
        c = cases[r["id"]]
        parts = r["id"].split("__")
        agg[f"{parts[1]:8}{parts[2]:6}{c['lang']}{'+norm' if parts[-1] == 'norm' else ''}"].append(score.score_row(c, r))
print(f"{'pipeline':24}{'n':>4}{'amounts':>9}{'exact':>7}{'badAmt':>8}")
for k in sorted(agg):
    v = agg[k]
    print(f"{k:24}{len(v):>4}{100 * sum(s['amounts'] for s in v) / len(v):>9.1f}{100 * sum(s['exact'] for s in v) / len(v):>7.1f}{sum(s['bad'] for s in v):>8}")
