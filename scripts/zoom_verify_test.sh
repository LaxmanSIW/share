#!/bin/bash
# Zoom/scroll regression harness: runs ZoomScrollVerify (see that file) under
# Xvfb in an isolated data dir, printing anchor-drift + scroll-range metrics
# for bill mode AND barcode label mode.
#
#   mvn package -DskipTests   # harness runs the PACKAGED jar + test-classes
#   ./scripts/zoom_verify_test.sh
set -u

BASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$(ls "$BASE"/target/invoice-studio-desktop-*.jar 2>/dev/null | grep -v original | head -1)"
TESTCLASSES="$BASE/target/test-classes"
RUN_DIR="${ZOOM_RUN_DIR:-$BASE/zoom-verify}"
SHOTS="${ZOOM_SHOTS:-$RUN_DIR/shots}"
LOG="$RUN_DIR/zoom-verify.log"
DISPLAY_NUM="${ZOOM_DISPLAY:-:79}"
TIMEOUT_S="${ZOOM_TIMEOUT_S:-120}"

for tool in Xvfb java timeout; do
    if ! command -v "$tool" >/dev/null 2>&1; then echo "[FAIL] $tool missing"; exit 1; fi
done
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then echo "[FAIL] packaged jar missing — run mvn package"; exit 1; fi
if [ ! -f "$TESTCLASSES/ZoomLauncher.class" ]; then echo "[FAIL] ZoomLauncher not compiled — run mvn test-compile"; exit 1; fi

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
    -Dzoom.shots="$SHOTS" \
    -cp "$JAR:$TESTCLASSES" \
    ZoomLauncher > "$LOG" 2>&1
EXIT_CODE=$?
kill $XVFB_PID 2>/dev/null

echo "--- metrics & verdicts ---"
grep -E "^\[(METRIC|DRIFT|RANGE|PASS|FAIL|OK|BAD|NODES|SHOT|SEED)" "$LOG" | head -80
echo ""
FAILS=$(grep -c "^\[BAD\]" "$LOG" 2>/dev/null); FAILS=${FAILS:-0}
if grep -q "ZOOM VERIFY: SUCCESS" "$LOG" && [ "$FAILS" = "0" ] && [ "$EXIT_CODE" = "0" ]; then
    echo "ZOOM TEST: SUCCESS"
    exit 0
else
    echo "ZOOM TEST: FAILED (exit=$EXIT_CODE, bad=$FAILS) — full log: $LOG"
    grep -B2 -A10 -E "UNCAUGHT|Exception" "$LOG" | head -60
    exit 1
fi
