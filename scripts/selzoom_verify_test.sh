#!/bin/bash
# Selection-overlay zoom + inline editor regression harness: runs
# SelectionZoomVerify (see that file) under Xvfb in an isolated data dir,
# measuring handle sizes on screen at 30 % / 100 % / 400 % zoom and the
# inline editor's padding/border/content neutrality.
#
#   mvn package -DskipTests   # harness runs the PACKAGED jar + test-classes
#   ./scripts/selzoom_verify_test.sh
set -u

BASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$(ls "$BASE"/target/invoice-studio-desktop-*.jar 2>/dev/null | grep -v original | head -1)"
TESTCLASSES="$BASE/target/test-classes"
RUN_DIR="${SELZOOM_RUN_DIR:-$BASE/selzoom-verify}"
SHOTS="${SELZOOM_SHOTS:-$RUN_DIR/shots}"
LOG="$RUN_DIR/selzoom-verify.log"
DISPLAY_NUM="${SELZOOM_DISPLAY:-:82}"
TIMEOUT_S="${SELZOOM_TIMEOUT_S:-120}"

for tool in Xvfb java timeout; do
    if ! command -v "$tool" >/dev/null 2>&1; then echo "[FAIL] $tool missing"; exit 1; fi
done
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then echo "[FAIL] packaged jar missing — run mvn package"; exit 1; fi
if [ ! -f "$TESTCLASSES/SelectionZoomLauncher.class" ]; then echo "[FAIL] SelectionZoomLauncher not compiled — run mvn test-compile"; exit 1; fi

rm -rf "$RUN_DIR"; mkdir -p "$RUN_DIR" "$SHOTS"
cd "$RUN_DIR"

Xvfb $DISPLAY_NUM -screen 0 1440x900x24 &
XVFB_PID=$!
sleep 2
if ! kill -0 $XVFB_PID 2>/dev/null; then echo "[FAIL] Xvfb failed"; exit 1; fi

XLIBS_DIR="$BASE/../xlibs/extracted/usr/lib/x86_64-linux-gnu"
LD_EXTRA=""
[ -d "$XLIBS_DIR" ] && LD_EXTRA=":$XLIBS_DIR"

DISPLAY=$DISPLAY_NUM LD_LIBRARY_PATH="$LD_EXTRA" \
    timeout --signal=INT ${TIMEOUT_S}s java \
    -Dprism.order=sw \
    -Dinvoicestudio.data.dir="$RUN_DIR" \
    -Dselzoom.shots="$SHOTS" \
    -cp "$JAR:$TESTCLASSES" \
    SelectionZoomLauncher > "$LOG" 2>&1
EXIT_CODE=$?
kill $XVFB_PID 2>/dev/null

echo "--- checks & verdicts ---"
grep -E "^\[(OK|BAD|INFO|NODES|SHOT|SEED)" "$LOG" | head -60
echo ""
FAILS=$(grep -c "^\[BAD\]" "$LOG" 2>/dev/null); FAILS=${FAILS:-0}
if grep -q "SELECTION ZOOM VERIFY: SUCCESS" "$LOG" && [ "$FAILS" = "0" ] && [ "$EXIT_CODE" = "0" ]; then
    echo "SELECTION ZOOM TEST: SUCCESS"
    exit 0
else
    echo "SELECTION ZOOM TEST: FAILED (exit=$EXIT_CODE, bad=$FAILS) — full log: $LOG"
    grep -B2 -A10 -E "UNCAUGHT|Exception" "$LOG" | head -60
    exit 1
fi
