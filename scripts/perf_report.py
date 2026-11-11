#!/usr/bin/env python3
"""Performance report: Macrobenchmark results and APK sizes as Markdown.

Usage:
  scripts/perf_report.py [--results DIR] [--apk PATH ...] [--gate THRESHOLDS.json]

--results  Directory searched for *benchmarkData.json (Macrobenchmark output).
--apk      APK files to size (repeatable). Reports total and compressed dex size.
--gate     Fail (exit 1) when a metric exceeds its threshold for the measured device.
           Thresholds are keyed by device model, so a gate never applies to a device
           (or emulator) it wasn't calibrated on. Without --gate the report never fails.

Stdlib only, so it runs on a bare CI runner. See docs/performance.md.
"""
import argparse
import glob
import json
import os
import sys
import zipfile


def load_benchmarks(results_dir):
    rows, device = [], None
    for path in sorted(glob.glob(os.path.join(results_dir, "**", "*benchmarkData.json"), recursive=True)):
        with open(path) as f:
            data = json.load(f)
        build = data.get("context", {}).get("build", {})
        device = device or build.get("model")
        for bench in data.get("benchmarks", []):
            name = bench["name"]
            for metric, v in bench.get("metrics", {}).items():
                rows.append((name, metric, "median", v["median"], v.get("minimum"), v.get("maximum")))
            for metric, v in bench.get("sampledMetrics", {}).items():
                for p in ("P50", "P90", "P99"):
                    if p in v:
                        rows.append((name, metric, p, v[p], None, None))
    return rows, device


def apk_sizes(paths):
    out = []
    for p in paths:
        with zipfile.ZipFile(p) as z:
            dex = sum(i.compress_size for i in z.infolist() if i.filename.endswith(".dex"))
        out.append((os.path.basename(p), os.path.getsize(p), dex))
    return out


def fmt(v):
    return f"{v:,.1f}" if isinstance(v, float) and not v.is_integer() else f"{v:,.0f}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--results")
    ap.add_argument("--apk", action="append", default=[])
    ap.add_argument("--gate")
    args = ap.parse_args()

    lines, failures = ["## Performance report", ""], []
    rows, device = load_benchmarks(args.results) if args.results else ([], None)

    if rows:
        lines += [f"Device: **{device}**", "",
                  "| Benchmark | Metric | Stat | Value | Min | Max |", "|---|---|---|---:|---:|---:|"]
        for name, metric, stat, v, lo, hi in rows:
            lines.append(f"| {name} | {metric} | {stat} | {fmt(v)} | {fmt(lo) if lo is not None else ''} | {fmt(hi) if hi is not None else ''} |")
        lines.append("")
    elif args.results:
        lines += ["No benchmark results found.", ""]

    if args.apk:
        lines += ["| APK | Size (KB) | Compressed dex (KB) |", "|---|---:|---:|"]
        for name, size, dex in apk_sizes(args.apk):
            lines.append(f"| {name} | {size / 1024:,.0f} | {dex / 1024:,.0f} |")
        lines.append("")

    if args.gate and not rows:
        # Missing or empty results must not pass as "nothing regressed".
        failures.append("no benchmark results to gate on")
    elif args.gate:
        with open(args.gate) as f:
            limits = {k: v for k, v in json.load(f).get(device or "", {}).items() if not k.startswith("_")}
        if not limits:
            lines.append(f"Gate: no thresholds for `{device}`, so nothing was gated.")
        values = {f"{n}.{m}.{s}": v for n, m, s, v, _, _ in rows}
        for key, limit in limits.items():
            v = values.get(key)
            if v is None:
                failures.append(f"{key}: not measured")
            elif v > limit:
                failures.append(f"{key}: {fmt(v)} > {fmt(limit)}")
        lines += [f"Gate passed ({len(limits)} thresholds)."] if limits and not failures else []
    lines += [f"Gate failed: {f}" for f in failures]

    print("\n".join(lines))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
