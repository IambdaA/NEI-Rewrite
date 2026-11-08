#!/usr/bin/env bash
# Starts the Fabric dev client with the NEI smoke-test driver, runs a script, then checks the log.
#   smoketest/run.sh [script]        (default: smoketest/basic.txt)
# Exit code 0 = the script ran to `quit` with no errors, crashes or NEI exceptions in the log.
# While it runs, more commands can be appended to fabric/run-smoketest/nei-smoketest/commands.txt.
set -u
cd "$(dirname "$0")/.."
SCRIPT="$(realpath "${1:-smoketest/basic.txt}")"
RUN=fabric/run-smoketest
OUT=$RUN/nei-smoketest
mkdir -p "$RUN"
if pgrep -f 'Dnei.smoketest.script|nei_smoketest' >/dev/null; then
  echo "Another smoke-test game is still running (it holds the test world); quit it first."; exit 3
fi
# options that keep a test run from stopping on its own (pause on focus loss, first-launch screens)
if [ ! -f "$RUN/options.txt" ]; then
  printf '%s\n' pauseOnLostFocus:false onboardAccessibility:false tutorialStep:none narrator:0 guiScale:2 \
    soundCategory_master:0.0 joinedFirstServer:true skipMultiplayerWarning:true > "$RUN/options.txt"
fi
rm -rf "$OUT/shots" "$RUN/dumps"
# NEI's options start at their defaults every run (SMOKE_KEEP_CONFIG=1 keeps the last run's settings)
[ "${SMOKE_KEEP_CONFIG:-0}" = 1 ] || rm -f "$RUN"/config/notenoughitems-*
START=$(date +%s)
timeout "${SMOKE_TIMEOUT:-600}" ./gradlew --offline -q :fabric:runSmokeTest "-Pnei.smoketest.script=$SCRIPT" > "$RUN/gradle-run.log" 2>&1
CODE=$?
LOG=$RUN/logs/latest.log
echo "=== driver output ($OUT/out.log)"
cat "$OUT/out.log" 2>/dev/null
echo "=== problems in $LOG"
PROBLEMS=$(grep -nE '\[NEI-SMOKE\] ERROR|Mixin apply.*failed|InvalidMixin|Exception|/ERROR\]' "$LOG" 2>/dev/null \
  | grep -vE 'narrator|Narrator|[Rr]ealms|Failed to (fetch user properties|retrieve profile key pair)|MinecraftClientHttpException|status=401|telemetry|authlib|Yggdrasil|amecs' )
# (dev runs use an offline account, so 401s from Mojang's services and the narrator error are expected)
echo "${PROBLEMS:-none}"
CRASH=$(find "$RUN/crash-reports" -newermt "@$START" -name '*.txt' 2>/dev/null | head -1)
[ -n "$CRASH" ] && echo "=== CRASH REPORT: $CRASH" && head -40 "$CRASH"
echo "=== screenshots"
ls "$OUT/shots" 2>/dev/null | sed "s|^|$OUT/shots/|"
if [ $CODE -eq 124 ]; then echo "RESULT: TIMEOUT"; exit 2; fi
if [ -n "$CRASH" ] || ! grep -q 'QUIT errors=0' "$OUT/out.log" 2>/dev/null || [ -n "$PROBLEMS" ]; then
  echo "RESULT: FAIL"; exit 1
fi
echo "RESULT: PASS"
