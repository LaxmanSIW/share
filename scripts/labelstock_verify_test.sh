#!/bin/bash
# InvoiceStudio runtime verification: Label Stock dialog (BarTender-style,
# modern card layout) — physical numbers, live captions, round-trip safety,
# auto-fit. Usage: mvn package -DskipTests && mvn test-compile, then run.
set -u

BASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$(ls "$BASE"/target/invoice-studio-desktop-*.jar 2>/dev/null | grep -v original | head -1)"
TESTCLASSES="$BASE/target/test-classes"
RUN_DIR="${LS_RUN_DIR:-$BASE/labelstock-verify}"
LOG="$RUN_DIR/labelstock-verify.log"
DISPLAY_NUM="${LS_DISPLAY:-:85}"
TIMEOUT_S="${LS_TIMEOUT_S:-150}"

echo "=== InvoiceStudio Label Stock Dialog Test (Xvfb) ==="

for tool in Xvfb java timeout; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "[FAIL] $tool not found on PATH"; exit 1
    fi
done
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
    echo "[FAIL] packaged jar missing in $BASE/target"; exit 1
fi
if [ ! -f "$TESTCLASSES/LabelStockLauncher.class" ]; then
    echo "[FAIL] LabelStockLauncher not compiled — run 'mvn test-compile'"; exit 1
fi

rm -rf "$RUN_DIR"
mkdir -p "$RUN_DIR"
cd "$RUN_DIR"

Xvfb $DISPLAY_NUM -screen 0 1440x900x24 &
XVFB_PID=$!
sleep 2
if ! kill -0 $XVFB_PID 2>/dev/null; then echo "[FAIL] Xvfb failed to start"; exit 1; fi
echo "Xvfb on $DISPLAY_NUM (pid $XVFB_PID)"

XLIBS_DIR="$BASE/../xlibs/extracted/usr/lib/x86_64-linux-gnu"
LD_EXTRA=""
[ -d "$XLIBS_DIR" ] && LD_EXTRA=":$XLIBS_DIR"

DISPLAY=$DISPLAY_NUM LD_LIBRARY_PATH="$LD_EXTRA" \
    timeout --signal=INT ${TIMEOUT_S}s java \
    -Dprism.order=sw \
    -Dinvoicestudio.data.dir="$RUN_DIR" \
    -Dsmoke.shots="$RUN_DIR/screenshots" \
    -cp "$JAR:$TESTCLASSES" \
    LabelStockLauncher > "$LOG" 2>&1
EXIT_CODE=$?

kill $XVFB_PID 2>/dev/null

echo "--- harness output ---"
grep -v "^$" "$LOG" | grep -Ev "WARNING|log4j" | tail -30
if [ "$EXIT_CODE" = "0" ] && grep -q "LABEL STOCK VERIFY: SUCCESS" "$LOG"; then
    echo "[PASS] label stock dialog verification SUCCESS"
    ls "$RUN_DIR/screenshots" 2>/dev/null | sed 's/^/[SHOT] /'
    exit 0
fi
echo "[FAIL] label stock dialog verification FAILED (exit=$EXIT_CODE)"
exit 1
