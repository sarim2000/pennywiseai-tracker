#!/usr/bin/env python3
"""Scores chat-eval runs. Usage: score.py cases.json results.jsonl [more.jsonl ...] [--fails]

Every metric below requires the run to have finished without an engine error.
  route    the right tool family (or none) and nothing else
  amounts  right number of items and right amounts, one to one
  exact    amounts + effective type + merchant + tool arguments
  strict   exact + currency + date (a shape with no such field keeps base currency / today)
  post     amounts + merchant + type/currency/date after post_item, the planned code around the model
  card     exact, and it would reach the user today: the app shows one pending card
  recall   expected items matched, over expected items (not averaged per message)
  badAmt   runs with a recorded amount the user never said, or a transaction on a no-call case
  merged   runs where a recorded amount is the sum of two or more expected amounts
  fakeAcc  recorded items carrying an account the message doesn't mention
Effective type: a replayed run keeps what the app's TransactionDraft decided (ChatReplayTest.kt). A raw run
gets the English-only rule from before the PR, which is the "before" number, not what the app does today.
rawType is the model's own type on a raw run; replay overwrites it, so there it repeats the app's.
ADD row: only the add cases, the common ground for comparing extraction shapes.
"""
import collections
import itertools
import json
import re
import sys
from decimal import Decimal, InvalidOperation

SPEND = re.compile(r"\b(spent|spend|paid|pay|bought|buy|purchased|gave|sent|ordered|bill|fee|fees|rent|recharge|donated|subscription)\b", re.I)
INCOME = re.compile(r"\b(received|got|salary|refund|refunded|cashback|credited|sold|bonus|interest|income|won|earned|reimburse\w*)\b", re.I)
INCOME_ONLY = {"salary", "income"}
EASTERN = str.maketrans("٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹०१२३४५६७८९", "012345678901234567890123456789")
FAMILY = {"add_transaction": "add", "add_transactions": "add", "delete_transaction": "delete",
          "update_transaction": "update", "month_summary": "summary", "spending_by_category": "category"}
ACCOUNT_WORDS = re.compile(r"hdfc|card|cash|wallet|account|upi|1234", re.I)


def amount(v):
    try:
        d = Decimal(str(v).translate(EASTERN).replace(",", "").strip())
        return d.normalize() if d.is_finite() else None
    except (InvalidOperation, ValueError):
        return None


def effective_type(text, item):
    """The rule before the PR, for raw runs. Replay a run to score the app's current rule."""
    if SPEND.search(text):
        return "EXPENSE"
    if INCOME.search(text):
        return "INCOME"
    says_income = str(item.get("type", "")).strip().upper() == "INCOME"
    return "INCOME" if says_income and str(item.get("category", "")).strip().lower() in INCOME_ONLY else "EXPENSE"


def items_of(calls):
    out = []
    for c in calls:
        a = c.get("args") or {}
        if FAMILY.get(c["name"]) != "add":
            continue
        if c["name"] == "add_transactions":
            raw = a.get("items")
            if isinstance(raw, str):
                try:
                    raw = json.loads(raw)
                except ValueError:
                    raw = []
            out.extend(i for i in (raw or []) if isinstance(i, dict))
        else:
            out.append(a)
    return out


def merchant_ok(want, got):
    return not want.get("merchant") or any(m.lower() in str(got.get("merchant", "")).lower() for m in want["merchant"])


def days_of(item):
    v = item.get("days_ago", item.get("daysAgo"))
    if v in (None, ""):
        return 0
    a = amount(v)
    return int(a) if a is not None and a == a.to_integral_value() else None


def pair(want, got):
    """One-to-one amount matching, preferring the pairing with the most merchant matches."""
    if not want or not got:
        return []
    best, best_score = [], -1
    for perm in itertools.permutations(range(len(got)), min(len(want), len(got))):
        cur = [(w, got[i]) for w, i in zip(want, perm) if amount(got[i].get("amount")) == amount(w["amount"])]
        s = 100 * len(cur) + sum(merchant_ok(w, g) for w, g in cur)
        if s > best_score:
            best, best_score = cur, s
    return best


def args_ok(case, calls):
    want = case.get("args")
    if not want:
        return True
    for c in calls:
        a = c.get("args") or {}
        for k, accepted in want.items():
            v = str(a.get(k, a.get(re.sub("_(.)", lambda m: m[1].upper(), k), ""))).lower()
            if not any(x.lower() in v for x in accepted):
                return False
    return True


CURRENCY_WORDS = {
    "USD": r"\$|\busd\b|dollars?\b|دولار|डॉलर",
    "AED": r"\baed\b|dirhams?\b|درهم|دراهم|दिरहम",
    "EUR": r"€|\beur\b|euros?\b|يورو|यूरो",
    "SAR": r"\bsar\b|riyals?\b|ريال|रियाल",
    "EGP": r"\begp\b|جنيه",
    "JOD": r"\bjod\b|دينار|دنانير",
    "INR": r"₹|\binr\b|rupees?\b|rupaye\b|रुपये|रुपए|روبية",
}
INCOME_ANYWHERE = re.compile(r"\b(received|got|salary|refund\w*|cashback|credited|sold|bonus|interest|income|won|earned|reimburse\w*|aayi|aa gayi)\b|راتب|رجع|استرجاع|استلمت|सैलरी|वेतन|रिफंड", re.I)
DAY_WORDS = [(1, r"\byesterday\b|\bkal\b|أمس|امس|البارح|कल"), (2, r"قبل يومين|\b2 days ago\b|दो दिन पहले"), (3, r"\b3 days ago\b|قبل ثلاث")]


def post_item(text, item, base):
    """What the planned code around the model would do: a currency or date the message
    doesn't state falls back to the default; a model INCOME needs an income word somewhere."""
    named = {code for code, pat in CURRENCY_WORDS.items() if re.search(pat, text, re.I)}
    cur = str(item.get("currency") or "").strip().upper()
    if cur not in named:
        cur = next(iter(named)) if len(named) == 1 else base
    day_hits = [d for d, pat in DAY_WORDS if re.search(pat, text, re.I)]
    model_day = days_of(item)
    days = model_day if model_day in day_hits else (day_hits[0] if len(day_hits) == 1 else 0)
    typ = str(item.get("type", "")).strip().upper()
    typ = "INCOME" if typ in ("INCOME", "RECEIVED", "REFUND") and INCOME_ANYWHERE.search(text) else "EXPENSE"
    return dict(item, currency=cur, days_ago=days, type=typ)


def score_row(case, row):
    base = case.get("base", "INR")
    text = case["text"]
    calls = row.get("calls") or []
    errored = bool(row.get("error"))
    fam = {FAMILY.get(c["name"], c["name"]) for c in calls}
    want = case["expect"]
    routed = not errored and ((not fam) if want == "none" else fam == {want})
    got = items_of(calls)
    s = dict(route=routed, error=errored, total_ms=row["total_ms"])
    if want != "add":
        s["exact"] = s["strict"] = s["card"] = s["post"] = routed and args_ok(case, calls)
        s["bad"] = want == "none" and bool(got)
        return s
    items = case["items"]
    pairs = pair(items, got)
    complete = routed and len(pairs) == len(items) == len(got)
    wanted_amounts = [amount(w["amount"]) for w in items]
    # A replayed run already went through the app's own code (ChatReplayTest.kt).
    types_eff = all((g.get("type") if row.get("replayed") else effective_type(text, g)) == w["type"] for w, g in pairs)
    types_raw = all(str(g.get("type", "")).strip().upper() == w["type"] for w, g in pairs)
    merchants = all(merchant_ok(w, g) for w, g in pairs)
    currency = all(str(g.get("currency") or base).strip().upper() == w.get("currency", base) for w, g in pairs)
    dates = all(days_of(g) == w.get("days_ago", 0) for w, g in pairs)
    posted = [(w, post_item(text, g, base)) for w, g in pairs]
    s.update(
        post=complete and merchants and all(
            g["type"] == w["type"] and g["currency"] == w.get("currency", base) and g["days_ago"] == w.get("days_ago", 0)
            for w, g in posted),
        amounts=complete,
        exact=complete and types_eff and merchants,
        rawtype=complete and types_raw and merchants,
        strict=complete and types_eff and merchants and currency and dates,
        card=complete and types_eff and merchants and len(items) == 1,
        matched=0 if errored else len(pairs),
        expected=len(items),
        bad=not errored and any(amount(g.get("amount")) not in wanted_amounts for g in got),
        merged=not errored and any(
            amount(g.get("amount")) is not None and amount(g.get("amount")) not in wanted_amounts
            and any(sum(combo) == amount(g.get("amount")) for n in range(2, len(wanted_amounts) + 1)
                    for combo in itertools.combinations(wanted_amounts, n))
            for g in got),
        accounts=len(got),
        fake_acc=0 if ACCOUNT_WORDS.search(text) else sum(1 for g in got if str(g.get("account") or "").strip()),
    )
    return s


def pct(xs):
    xs = list(xs)
    return f"{100 * sum(xs) / len(xs):5.1f}" if xs else "    -"


def report(cases, path, show_fails):
    rows = [json.loads(line) for line in open(path)]
    rows = [r for r in rows if r["id"] in cases]
    if not rows:
        return
    configs = {(r["model"], r["shape"], r["backend"], r["temp"], r["topk"]) for r in rows}
    if len(configs) > 1:
        print(f"!! {path}: mixed configs {configs}")
    r0 = rows[0]
    per_case = collections.Counter(r["id"] for r in rows)
    eligible = [c for c in cases.values() if r0["shape"] != "json" or c["expect"] in ("add", "none")]
    missing = [c["id"] for c in eligible if c["id"] not in per_case]
    uneven = len(set(per_case.values())) > 1
    print(f"\n== {path.split('/')[-1]}  {'replayed' if r0.get('replayed') else 'raw (old type rule)'} {r0['model'][:24]} {r0['shape']} {r0['backend']} T={r0['temp']} k={r0['topk']} runs={len(rows)} load={r0['load_ms']}ms rss={max(r.get('rss_peak_mb', 0) for r in rows)}MB"
          + (f"  !! INCOMPLETE missing={len(missing)} uneven={uneven}" if missing or uneven else ""))
    by = collections.defaultdict(list)
    fails = []
    for row in rows:
        case = cases[row["id"]]
        if r0["shape"] == "json" and case["expect"] not in ("add", "none"):
            continue
        s = score_row(case, row)
        keys = ["ALL", case["group"], "lang:" + case["lang"]] + (["ADD"] if case["expect"] == "add" else [])
        for k in keys:
            by[k].append(s)
        if not s["strict"]:
            fails.append((row["id"], row["rep"], case["text"], json.dumps(items_of(row.get("calls") or []) or row.get("calls"), ensure_ascii=False)[:190], (row.get("text") or "")[:80].replace("\n", " "), (row.get("error") or "")[:60].replace("\n", " ")))
    print(f"{'group':14}{'n':>4} {'route':>6}{'amnts':>7}{'exact':>7}{'raw':>7}{'strict':>7}{'post':>7}{'card':>7}{'recall':>7}{'badAmt':>7}{'merged':>7}{'fakeAcc':>8}{'err':>5}{'p50ms':>7}{'p95ms':>7}")
    for k in ["ALL", "ADD"] + sorted(x for x in by if x not in ("ALL", "ADD")):
        v = by[k]
        add = [s for s in v if "amounts" in s]
        ms = sorted(s["total_ms"] for s in v)
        recall = f"{100 * sum(s['matched'] for s in add) / max(1, sum(s['expected'] for s in add)):5.1f}" if add else "    -"
        fake = f"{sum(s['fake_acc'] for s in add)}/{sum(s['accounts'] for s in add)}"
        print(f"{k:14}{len(v):>4} {pct(s['route'] for s in v):>6}{pct(s['amounts'] for s in add):>7}{pct(s['exact'] for s in v):>7}"
              f"{pct(s['rawtype'] for s in add):>7}{pct(s['strict'] for s in v):>7}{pct(s['post'] for s in v):>7}{pct(s['card'] for s in v):>7}{recall:>7}"
              f"{sum(s['bad'] for s in v):>7}{sum(s.get('merged', False) for s in v):>7}{fake:>8}{sum(s['error'] for s in v):>5}"
              f"{ms[len(ms) // 2]:>7}{ms[max(0, int(len(ms) * 0.95) - 1)]:>7}")
    if show_fails:
        for f in fails:
            print("  FAIL", *f, sep=" | ")


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    cases = {c["id"]: c for c in json.load(open(args[0]))["cases"]}
    for path in args[1:]:
        report(cases, path, "--fails" in sys.argv)


if __name__ == "__main__":
    main()
