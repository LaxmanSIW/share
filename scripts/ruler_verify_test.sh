#!/bin/bash
# InvoiceStudio runtime verification: canvas RULER tick hierarchy at zoom.
# Checks (on the real rendered rulers): major ticks longest, numbers sit ON
# major ticks with true mm values, 1-2-5 major step adapts per zoom.
set -u

BASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$(ls "$BASE"/target/invoice-studio-desktop-*.jar 2>/dev/null | grep -v original | head -1)"
TESTCLASSES="$BASE/target/test-classes"
RUN_DIR="${RULER_RUN_DIR:-$BASE/ruler-verify}"
LOG="$RUN_DIR/ruler-verify.log"
DISPLAY_NUM="${RULER_DISPLAY:-:86}"
TIMEOUT_S="${RULER_TIMEOUT_S:-150}"

echo "=== InvoiceStudio Ruler Tick Verification (Xvfb) ==="

for tool in Xvfb java timeout; do
    if ! command -v "$tool" >/dev/null 2>&1; then echo "[FAIL] $tool missing"; exit 1; fi
done
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then echo "[FAIL] packaged jar missing"; exit 1; fi
if [ ! -f "$TESTCLASSES/RulerVerify.class" ]; then
    echo "[FAIL] RulerVerify not compiled — run 'mvn test-compile'"; exit 1
fi

rm -rf "$RUN_DIR"
mkdir -p "$RUN_DIR" "$RUN_DIR/shots"
cd "$RUN_DIR"

Xvfb $DISPLAY_NUM -screen 0 1440x900x24 &
XVFB_PID=$!
sleep 2
if ! kill -0 $XVFB_PID 2>/dev/null; then echo "[FAIL] Xvfb failed to start"; exit 1; fi

XLIBS_DIR="$BASE/../xlibs/extracted/usr/lib/x86_64-linux-gnu"
LD_EXTRA=""
[ -d "$XLIBS_DIR" ] && LD_EXTRA=":$XLIBS_DIR"

DISPLAY=$DISPLAY_NUM LD_LIBRARY_PATH="$LD_EXTRA" \
    timeout --signal=INT ${TIMEOUT_S}s java \
    -Dprism.order=sw \
    -Dinvoicestudio.data.dir="$RUN_DIR" \
    -Druler.shots="$RUN_DIR/shots" \
    -cp "$JAR:$TESTCLASSES" \
    RulerLauncher > "$LOG" 2>&1
EXIT_CODE=$?

kill $XVFB_PID 2>/dev/null

echo "--- harness output ---"
grep -E "\[OK\]|\[BAD\]|\[SHOT\]|SUCCESS|FAILED|NODES" "$LOG" | tail -30
if [ "$EXIT_CODE" = "0" ] && grep -q "RULER VERIFY: SUCCESS" "$LOG"; then
    echo "[PASS] ruler verification SUCCESS"
    exit 0
fi
echo "[FAIL] ruler verification FAILED (exit=$EXIT_CODE)"
exit 1
