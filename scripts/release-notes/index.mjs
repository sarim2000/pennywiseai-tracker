// Structured release-note generator for PennyWise.
//
// Reads a git commit list on stdin (one "- subject" per line) and prints ONE
// JSON object on stdout:
//
//   { "summary": string, "highlights": string[] }
//
// It uses the Claude Agent SDK with schema-enforced structured output
// (`outputFormat: json_schema`), so the model can only return JSON matching the
// schema — no conversational preamble, no markdown code fences. release.sh
// formats both the GitHub notes and the F-Droid/Play changelog from this object.
//
// Auth: runs on the machine's Claude subscription (the same login the `claude`
// CLI uses) — no ANTHROPIC_API_KEY needed. Tools are disabled, so this is a
// single plain text-generation turn, not an agent loop.
//
// Env:
//   RELEASE_VERSION       version string (for context in the prompt)
//   RELEASE_NOTES_MODEL   model id/alias (default: "claude-sonnet-4-6")

import { query } from "@anthropic-ai/claude-agent-sdk";

const version = process.env.RELEASE_VERSION ?? "next";
const model = process.env.RELEASE_NOTES_MODEL ?? "claude-sonnet-4-6";

function readStdin() {
  return new Promise((resolve) => {
    let buf = "";
    process.stdin.setEncoding("utf8");
    process.stdin.on("data", (c) => (buf += c));
    process.stdin.on("end", () => resolve(buf.trim()));
  });
}

const commits = await readStdin();
if (!commits) {
  console.error("release-notes: no commits on stdin");
  process.exit(2);
}

const schema = {
  type: "object",
  additionalProperties: false,
  required: ["summary", "highlights"],
  properties: {
    summary: {
      type: "string",
      description:
        "One or two plain-language sentences summarising the release for end users. No version number, no markdown.",
    },
    highlights: {
      type: "array",
      items: { type: "string" },
      description:
        "User-facing changes, most important first. Each item is a short phrase with NO leading bullet character, NO version number and NO markdown. Name specific banks when a commit adds bank support.",
    },
  },
};

const systemPrompt =
  "You write release notes for PennyWise, an Android expense tracker that automatically parses bank SMS messages. " +
  "Turn raw git commits into clear, non-technical, user-facing notes. Group related changes, lead with the most " +
  "important, and omit pure-internal noise (refactors, CI, tests, version bumps). When commits add support for a " +
  "specific bank, name it.";

const prompt =
  `Release version ${version}. Git commits since the last release:\n\n${commits}\n\n` +
  "Produce the structured release notes.";

let structured;
try {
  for await (const message of query({
    prompt,
    options: {
      tools: [],
      // Structured output is emitted as a final formatting step, so allow a few
      // turns even though no tools run (maxTurns: 1 trips error_max_turns).
      maxTurns: 6,
      model,
      systemPrompt,
      outputFormat: { type: "json_schema", schema },
    },
  })) {
    if (message.type === "result") {
      if (message.subtype !== "success" || message.structured_output == null) {
        console.error(`release-notes: generation failed (${message.subtype})`);
        process.exit(1);
      }
      structured = message.structured_output;
    }
  }
} catch (err) {
  console.error("release-notes:", err?.message ?? err);
  process.exit(1);
}

if (structured == null) {
  console.error("release-notes: no result returned");
  process.exit(1);
}

process.stdout.write(JSON.stringify(structured));
