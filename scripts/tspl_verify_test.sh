#!/bin/bash
# InvoiceStudio TSPL pipeline runtime verification: launches a hardware-less
# FX harness under Xvfb that drives the NATIVE TSC print path end to end —
# strip-row rendering (same renderer as the preview) → 203-dpi 1-bit
# rasterization → TSPL script assembly → fake RAW spooler — asserting that
# ONE selected label produces EXACTLY "PRINT 1,1", copies compress to a
# single "PRINT n,1", and the script declares SIZE/GAP in dots so the
# driver can never re-interpret stock.
#
# Usage:
#   mvn package -DskipTests     # harness runs the PACKAGED jar
#   ./scripts/tspl_verify_test.sh
set -u

BASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$(ls "$BASE"/target/invoice-studio-desktop-*.jar 2>/dev/null | grep -v original | head -1)"
TESTCLASSES="$BASE/target/test-classes"
RUN_DIR="${SMOKE_RUN_DIR:-$BASE/tspl-verify}"
LOG="$RUN_DIR/tspl-verify.log"
DISPLAY_NUM="${SMOKE_DISPLAY:-:81}"
TIMEOUT_S="${SMOKE_TIMEOUT_S:-150}"

echo "=== InvoiceStudio TSPL Pipeline Runtime Test (Xvfb) ==="

for tool in Xvfb java timeout; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "[FAIL] $tool not found on PATH (Linux + xvfb required)"; exit 1
    fi
done
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
    echo "[FAIL] packaged jar missing in $BASE/target — run 'mvn package -DskipTests' first"; exit 1
fi
if [ ! -f "$TESTCLASSES/TsplVerifyLauncher.class" ]; then
    echo "[FAIL] TsplVerifyLauncher not compiled: $TESTCLASSES — run 'mvn test-compile' first"; exit 1
fi

rm -rf "$RUN_DIR"
mkdir -p "$RUN_DIR"
cd "$RUN_DIR"

Xvfb $DISPLAY_NUM -screen 0 1440x900x24 &
XVFB_PID=$!
sleep 2
if ! kill -0 $XVFB_PID 2>/dev/null; then
    echo "[FAIL] Xvfb failed to start"; exit 1
fi
echo "Xvfb on $DISPLAY_NUM (pid $XVFB_PID)"

XLIBS_DIR="$BASE/../xlibs/extracted/usr/lib/x86_64-linux-gnu"
LD_EXTRA=""
[ -d "$XLIBS_DIR" ] && LD_EXTRA=":$XLIBS_DIR"

DISPLAY=$DISPLAY_NUM LD_LIBRARY_PATH="$LD_EXTRA" \
    timeout --signal=INT ${TIMEOUT_S}s java \
    -Dprism.order=sw \
    -Dinvoicestudio.data.dir="$RUN_DIR" \
    -cp "$JAR:$TESTCLASSES" \
    TsplVerifyLauncher > "$LOG" 2>&1
EXIT_CODE=$?

kill $XVFB_PID 2>/dev/null

echo "--- harness output ---"
cat "$LOG"
echo "----------------------"

if [ "$EXIT_CODE" -eq 0 ] && grep -q "TSPL PIPELINE VERIFY: SUCCESS" "$LOG"; then
    echo "[PASS] TSPL pipeline runtime verification SUCCESS"
    exit 0
fi
echo "[FAIL] TSPL pipeline runtime verification FAILED (exit=$EXIT_CODE)"
exit 1
