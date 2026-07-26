// Mines this repo's parser history for facts worth writing a post about.
//
// The premise: nobody else on earth has run a bank-SMS parser against 140+ banks
// in 23 countries for a year. That history is a genuinely unique dataset, and it's
// the only kind of "marketing" that works on r/PersonalFinanceIndia — value first,
// product as a footnote.
//
// Usage:
//   node scripts/content-mine/index.mjs                    # print the facts (no deps, no LLM)
//   node scripts/content-mine/index.mjs --json             # same, machine-readable
//   node scripts/content-mine/index.mjs --since "6 months ago"
//   node scripts/content-mine/index.mjs --draft reddit-pfi # + a post draft via Claude
//
// The facts step is pure git + node: zero dependencies, and it is the part you can
// actually defend in a comment thread. --draft additionally needs the Claude Agent
// SDK (`npm install` in this directory); it runs on the local Claude subscription,
// same as scripts/release-notes.
//
// IMPORTANT: the drafting prompt is deliberately fenced to the mined numbers. A post
// claiming "N banks changed their SMS format this year" is NOT supported by this
// repo's history (it is only ~1 year old, so most parser commits are initial
// coverage, not format churn) — and getting caught overstating that on Reddit costs
// more than the post earns.

import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const BANK_DIR = "parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/";
const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

// A parser fixed this long after it first landed is real post-launch breakage —
// a format variant we never saw, or one the bank started sending. Fixes inside the
// window are just the normal write-it-then-tighten-it loop.
const SETTLED_DAYS = 30;

const args = process.argv.slice(2);
const flag = (name, fallback = null) => {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] && !args[i + 1].startsWith("--") ? args[i + 1] : fallback;
};
const has = (name) => args.includes(name);

const since = flag("--since", "12 months ago");
const asJson = has("--json");
const draftAudience = flag("--draft");

const git = (...a) =>
  execFileSync("git", a, { cwd: REPO_ROOT, encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });

// ---------------------------------------------------------------- gather

/** One entry per (commit, parser file) pair. */
function parserCommits(sinceExpr) {
  const raw = git(
    "log",
    `--since=${sinceExpr}`,
    "--format=%x00%H|%ad|%s",
    "--date=short",
    "--name-only",
    "--",
    `${BANK_DIR}*.kt`
  );
  const out = [];
  let commit = null;
  for (const line of raw.split("\n")) {
    if (line.startsWith("\0")) {
      const [hash, date, ...rest] = line.slice(1).split("|");
      commit = { hash, date, subject: rest.join("|") };
    } else if (commit && line.startsWith(BANK_DIR) && line.endsWith(".kt")) {
      const file = line.slice(BANK_DIR.length).replace(/\.kt$/, "");
      if (file.includes("/") || notABank(file)) continue;
      out.push({ ...commit, parser: file });
    }
  }
  return out;
}

/** First time each parser file ever appeared — over ALL history, not just the window. */
function firstSeen() {
  const raw = git(
    "log",
    "--diff-filter=A",
    "--format=%x00%ad",
    "--date=short",
    "--name-only",
    "--",
    `${BANK_DIR}*.kt`
  );
  const seen = new Map();
  let date = null;
  for (const line of raw.split("\n")) {
    if (line.startsWith("\0")) date = line.slice(1);
    else if (date && line.startsWith(BANK_DIR) && line.endsWith(".kt")) {
      const file = line.slice(BANK_DIR.length).replace(/\.kt$/, "");
      if (file.includes("/") || notABank(file)) continue;
      // git log walks newest-first, so the last write wins = the earliest add.
      seen.set(file, date);
    }
  }
  return seen;
}

const kind = (subject) => {
  const s = subject.toLowerCase();
  if (s.startsWith("fix")) return "fix";
  if (s.startsWith("feat")) return "feat";
  return "other";
};

// What actually breaks, bucketed by the failure mode named in the commit subject.
// Ordered: first match wins, so put the specific buckets above the generic ones.
// Word boundaries matter here: an unanchored /rs\.?/ happily matches the "rs" inside
// "parsers" and swallows most of the corpus.
const FAILURE_MODES = [
  ["Message wrongly skipped", /\bskip|\bignored\b|\bdropped\b|returned null|not detected|\bmissed\b|false negative/],
  ["Non-transaction wrongly parsed", /\botp\b|\bpromo|false positive|\badvert|\bmarketing\b/],
  ["Merchant name extraction", /\bmerchant|\bpayee\b|\bcounterparty\b/],
  ["Sender ID not recognised", /\bsender\b|\bheader\b|\bdlt\b|\bshortcode\b|\bsms code\b/],
  ["Card vs account confusion", /credit.?card|debit.?card|\bcard\b|last.?4\b|last four|\blast-?4\b/],
  ["Balance & limit parsing", /\bbalance\b|\bavl\b|available limit|\boutstanding\b/],
  ["Amount / currency regex", /\bamount\b|\bcurrency\b|\brs\.?\b|\bdecimal\b|\bplural\b|\bcomma\b|\bamt\b/],
  ["Transaction type / direction", /\bdebit\b|\bcredit\b|\brefund\b|\breversal\b|\bincome\b|\bexpense\b|\bdirection\b/],
  ["Transfer / mandate semantics", /\btransfer|\bmandate\b|\bneft\b|\bimps\b|\brtgs\b|\bdividend\b|redemption|\bmf\b/],
  ["UPI-specific format", /\bupi\b|\bvpa\b|@ok|\bcollect\b/],
  // Last resort before "Other": these subjects name a reviewer, not a failure mode, so
  // they only land here when nothing more specific matched.
  ["Code-review follow-up", /greptile|\breview\b|\bfeedback\b/],
];

// Base classes, factories and registries live in the same directory as the parsers but
// are not banks, and counting them inflates every per-parser statistic below.
//
// This was a hand-maintained denylist and it had already rotted — BaseIranianBankParser
// and BaseThailandBankParser were being counted as banks. So derive it instead: anything
// whose file declares an abstract/sealed class of the same name is scaffolding, not a
// bank. Files no longer on disk are kept (a deleted parser was still a real parser).
const INFRA_BY_NAME = new Set(["BankParserFactory", "BankParserRegistry"]);

const notABank = (() => {
  const cache = new Map();
  return (name) => {
    if (INFRA_BY_NAME.has(name)) return true;
    if (cache.has(name)) return cache.get(name);
    let abstract = false;
    try {
      const src = readFileSync(path.join(REPO_ROOT, BANK_DIR, `${name}.kt`), "utf8");
      abstract = new RegExp(`\\b(?:abstract|sealed)\\s+class\\s+${name}\\b`).test(src);
    } catch {
      abstract = false; // deleted/renamed — assume it was a genuine parser
    }
    cache.set(name, abstract);
    return abstract;
  };
})();

const classify = (subject) => {
  const s = subject.toLowerCase();
  for (const [label, re] of FAILURE_MODES) if (re.test(s)) return label;
  return "Other / unclassified";
};

// ---------------------------------------------------------------- analyse

const commits = parserCommits(since);
const added = firstSeen();
const repoStart = git("log", "--reverse", "--format=%ad", "--date=short").split("\n")[0];

const byParser = new Map();
for (const c of commits) {
  if (!byParser.has(c.parser)) byParser.set(c.parser, []);
  byParser.get(c.parser).push(c);
}

const daysBetween = (a, b) => (new Date(b) - new Date(a)) / 86400000;

const churn = [];
for (const [parser, cs] of byParser) {
  const born = added.get(parser);
  if (!born) continue;
  const lateFixes = cs.filter(
    (c) => kind(c.subject) === "fix" && daysBetween(born, c.date) > SETTLED_DAYS
  );
  if (lateFixes.length) {
    churn.push({
      parser,
      addedOn: born,
      lateFixes: lateFixes.length,
      fixes: lateFixes.map((f) => ({ date: f.date, subject: f.subject })),
    });
  }
}
churn.sort((a, b) => b.lateFixes - a.lateFixes || a.parser.localeCompare(b.parser));

// Classify per COMMIT, not per (commit, file) pair — one commit touching eight
// parsers is one fix, otherwise wide commits dominate the taxonomy.
const fixCommits = [
  ...new Map(
    commits.filter((c) => kind(c.subject) === "fix").map((c) => [c.hash, c])
  ).values(),
];
const modeCounts = {};
for (const c of fixCommits) {
  const m = classify(c.subject);
  modeCounts[m] = (modeCounts[m] || 0) + 1;
}
const failureModes = Object.entries(modeCounts).sort((a, b) => b[1] - a[1]);

const newParsersByMonth = {};
for (const date of added.values()) {
  const month = date.slice(0, 7);
  newParsersByMonth[month] = (newParsersByMonth[month] || 0) + 1;
}

const facts = {
  window: since,
  repoFirstCommit: repoStart,
  parsersEverAdded: added.size,
  parserCommitsInWindow: new Set(commits.map((c) => c.hash)).size,
  fixCommitsInWindow: new Set(fixCommits.map((c) => c.hash)).size,
  parsersTouchedInWindow: byParser.size,
  parsersNeedingLateFixes: churn.length,
  settledDaysThreshold: SETTLED_DAYS,
  mostRewritten: churn.slice(0, 15),
  failureModes,
  newParsersByMonth,
};

// ---------------------------------------------------------------- output

if (asJson) {
  console.log(JSON.stringify(facts, null, 2));
} else {
  const pct = (n, d) => (d ? ((n / d) * 100).toFixed(0) : "0");
  console.log(`\nPennyWise parser history — window: ${since}`);
  console.log(`Repo's first commit: ${repoStart}\n`);
  console.log(`  Parsers ever written .................. ${facts.parsersEverAdded}`);
  console.log(`  Parser commits in window ............. ${facts.parserCommitsInWindow}`);
  console.log(`  ...of which are fixes ................ ${facts.fixCommitsInWindow}`);
  console.log(
    `  Parsers needing a fix >${SETTLED_DAYS}d after launch  ${facts.parsersNeedingLateFixes}` +
      ` (${pct(facts.parsersNeedingLateFixes, facts.parsersEverAdded)}% of all parsers)`
  );

  console.log(`\nMost-rewritten parsers (fixes >${SETTLED_DAYS} days after they landed):`);
  for (const c of facts.mostRewritten) {
    console.log(`  ${c.parser.padEnd(30)} ${String(c.lateFixes).padStart(2)}  (added ${c.addedOn})`);
  }

  console.log(`\nWhat actually breaks (${fixCommits.length} fix commits, by failure mode):`);
  for (const [mode, n] of failureModes) {
    console.log(`  ${mode.padEnd(34)} ${String(n).padStart(3)}  ${pct(n, fixCommits.length)}%`);
  }

  console.log(`\nRun with --json to pipe these facts, or --draft reddit-pfi to draft a post.\n`);
}

// ---------------------------------------------------------------- draft

const AUDIENCES = {
  "reddit-pfi": {
    where: "r/PersonalFinanceIndia",
    guidance:
      "Readers are ordinary Indians managing their own money, not developers. No jargon. " +
      "The useful takeaway must be about THEIR money — e.g. which alerts are easy to " +
      "misread, why a transaction can be missed, what to double-check on a statement.",
  },
  "reddit-devindia": {
    where: "r/developersIndia",
    guidance:
      "Readers are engineers. Lead with the technical texture: regex fragility, the " +
      "long tail of formats, why a rules engine beat an LLM on-device. Concrete examples win.",
  },
  blog: {
    where: "a personal engineering blog",
    guidance:
      "Long-form and technical. A clear thesis, the data as evidence, and a conclusion " +
      "someone could disagree with.",
  },
};

if (draftAudience) {
  const audience = AUDIENCES[draftAudience];
  if (!audience) {
    console.error(
      `Unknown audience "${draftAudience}". Options: ${Object.keys(AUDIENCES).join(", ")}`
    );
    process.exit(2);
  }

  let query;
  try {
    ({ query } = await import("@anthropic-ai/claude-agent-sdk"));
  } catch {
    console.error(
      "\n--draft needs the Claude Agent SDK. Run:  npm install --prefix scripts/content-mine\n"
    );
    process.exit(2);
  }

  const systemPrompt =
    "You draft posts about PennyWise, an open-source Android app that parses bank SMS on-device. " +
    "You will be given MINED FACTS from the project's git history. Hard rules:\n" +
    "1. Every number and claim in the post must come from the mined facts. Invent nothing.\n" +
    "2. Do NOT claim banks 'changed their SMS format' unless the facts show that specifically. " +
    "The honest framing is that bank SMS has a long tail of formats a parser has to learn.\n" +
    "3. Value first. The post must be worth reading by someone who never installs the app. " +
    "Mention PennyWise once, near the end, as a footnote.\n" +
    "4. No marketing voice, no emoji, no hype. Write like an engineer sharing a finding.\n" +
    "5. If the facts don't support an interesting post, say so plainly instead of padding.";

  const prompt =
    `Draft a post for ${audience.where}.\n\n${audience.guidance}\n\n` +
    `MINED FACTS (JSON):\n${JSON.stringify(facts, null, 2)}\n\n` +
    "Output the post as plain text: a title line, then the body.";

  console.log(`\n${"=".repeat(70)}\nDRAFT for ${audience.where}\n${"=".repeat(70)}\n`);
  for await (const message of query({
    prompt,
    options: { tools: [], maxTurns: 6, model: process.env.CONTENT_MINE_MODEL ?? "claude-sonnet-4-6", systemPrompt },
  })) {
    if (message.type === "assistant") {
      for (const block of message.message.content) {
        if (block.type === "text") process.stdout.write(block.text);
      }
    }
  }
  console.log("\n");
}
