# chat-eval

Offline evaluation of the Android chat on the desktop, using the app's own tool schema and chat
template through `litertlm-jvm`. Cases are synthetic.

```bash
# Models are not in the repo. Qwen2.5 is the app's own (Constants.ModelDownload.MODEL_URL); others:
# huggingface.co/litert-community/Qwen3-1.7B  Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm
# huggingface.co/litert-community/gemma-4-E2B-it-litert-lm  gemma-4-E2B-it.litertlm

./run.sh <model.litertlm> app temp=0.8 topk=10 topp=0.95 reps=3 backend=cpu out=results/run.jsonl
python3 score.py cases.json results/run.jsonl --fails
python3 paired.py results prod_greedy ext_rules_greedy array_rules_greedy
python3 voice_score.py

# What the card would show: put a run's raw calls through the app's own TransactionDraft.
cp ChatReplayTest.kt ../../app/src/test/java/com/pennywiseai/tracker/data/model/
CHAT_REPLAY_CASES=$PWD/cases.json CHAT_REPLAY_IN=$PWD/results/run.jsonl CHAT_REPLAY_OUT=$PWD/results/replayed.jsonl \
  ../../gradlew -p ../.. :app:testStandardDebugUnitTest --tests '*ChatReplayTest' --rerun
rm ../../app/src/test/java/com/pennywiseai/tracker/data/model/ChatReplayTest.kt
python3 score.py cases.json results/replayed.jsonl

# The same cases on a phone, on the backend the app uses. Needs the debug build, which holds or downloads the model.
python3 phone.py <adb serial> cases_multi.json results/phone.jsonl backend=gpu seeds=1,2,3
```

Results stay out of git (`results/`).
`cases_multi.json` holds English messages with several transactions, and `cases_multi_heldout.json` the ones Codex wrote without seeing them.
`cases_text.json` holds free-text questions and multi-turn chats, to read by eye: it has no expected answers.

Measure accuracy on the CPU backend. GPU output changed between identical runs on a Mac.
`stt.py` generates speech with macOS `say` and transcribes it (`sherpa-onnx`, `pywhispercpp`, `soundfile`).
`normalize.py` is a spike of the number-word converter, fitted to `stt_cases.json`.
