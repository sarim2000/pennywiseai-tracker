# /// script
# requires-python = ">=3.10"
# dependencies = ["minijinja==2.14.0"]
# ///
"""Checks QwenChatTemplate against the chat template inside the model file.

LiteRT-LM renders chat templates with minijinja (2.14.0 in LiteRT-LM 0.19.0)
and, since 0.18, passes each message's content as a list of parts. This renders
the model's own template with plain-string content and QwenChatTemplate with
string content, list content and the "model" role, and fails unless all of them
produce the same prompt. Run it after changing the template or the model:

    uv run scripts/check_qwen_chat_template.py path/to/model.litertlm
"""
import copy
import json
import re
import sys
from pathlib import Path

from minijinja import Environment

KT = Path(__file__).resolve().parent.parent / (
    "app/src/main/java/com/pennywiseai/tracker/data/service/QwenChatTemplate.kt"
)


def model_template(model_path: str) -> str:
    with open(model_path, "rb") as f:
        data = f.read(64 << 20)  # the template sits in the header, before the weights
    start = data.find(b"{%- if tools %}")
    if start < 0:
        sys.exit("No Qwen chat template found in the model file")
    end = data.find(b"\x00", start)
    return data[start:end].decode()


def app_template() -> str:
    match = re.search(r'val TEMPLATE = """\n(.*?)"""\.trimStart\(\)', KT.read_text(), re.S)
    return match.group(1)


def render(template: str, **context) -> str:
    # Same settings as LiteRT-LM's runtime/components/rust/minijinja_template.rs.
    env = Environment(trim_blocks=True, lstrip_blocks=True, keep_trailing_newline=True)
    env.add_filter("tojson", lambda v: json.dumps(v, ensure_ascii=False, separators=(", ", ": ")))
    return env.render_str(template, **context)


def as_parts(messages, model_role=False):
    out = copy.deepcopy(messages)
    for m in out:
        if isinstance(m.get("content"), str):
            m["content"] = [{"type": "text", "text": m["content"]}]
        if model_role and m["role"] == "assistant":
            m["role"] = "model"
    return out


TOOLS = [{"type": "function", "function": {
    "name": "add_transaction", "description": "Record a transaction",
    "parameters": {"type": "object", "properties": {"amount": {"type": "number"}}, "required": ["amount"]}}}]
SYSTEM = {"role": "system", "content": "You are a test assistant.\nToday is 2026-01-01."}
CHAT = [
    SYSTEM,
    {"role": "user", "content": "coffee 120 at a cafe"},
    {"role": "assistant", "content": "", "tool_calls": [
        {"type": "function", "function": {"name": "add_transaction", "arguments": {"amount": 120}}}]},
    {"role": "tool", "content": "recorded"},
    {"role": "tool", "content": "ok"},
    {"role": "assistant", "content": "Here's what I'll add."},
    {"role": "user", "content": "how much on food?"},
]
CASES = {
    "tools and history": (CHAT, TOOLS),
    "no tools": (CHAT, None),
    "no system prompt": (CHAT[1:], TOOLS),
    "system prompt only": ([SYSTEM], TOOLS),
    "tool call with text": ([SYSTEM, {"role": "user", "content": "x"}, {
        "role": "assistant", "content": "Sure.", "tool_calls": [{"name": "month_summary", "arguments": {}}]}], TOOLS),
}


def main() -> int:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    stock, ours = model_template(sys.argv[1]), app_template()
    failures = 0
    for name, (messages, tools) in CASES.items():
        for gen in (True, False):
            want = render(stock, messages=messages, tools=tools, add_generation_prompt=gen)
            for label, msgs in (("string", messages), ("parts", as_parts(messages)),
                                ("model role", as_parts(messages, model_role=True))):
                got = render(ours, messages=msgs, tools=tools, add_generation_prompt=gen)
                if got != want:
                    failures += 1
                    print(f"FAIL {name} / {label} / generation prompt {gen}\n--- model's\n{want}\n--- ours\n{got}")
    print("OK: matches the model's template" if not failures else f"{failures} case(s) differ")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
