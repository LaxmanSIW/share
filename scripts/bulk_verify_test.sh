#!/bin/bash
# InvoiceStudio Bulk-Print dialog runtime verification: launches the real
# packaged app under Xvfb and drives the Bulk Label Print dialog through the
# exact interactions the user reported broken — Enter keys, focus-loss
# commits, popup picks — asserting no phantom rows are ever created, values
# commit, and the live preview shows fetched values (never {{placeholders}}).
#
# Usage:
#   mvn package -DskipTests     # harness runs the PACKAGED jar
#   ./scripts/bulk_verify_test.sh
set -u

BASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$(ls "$BASE"/target/invoice-studio-desktop-*.jar 2>/dev/null | grep -v original | head -1)"
TESTCLASSES="$BASE/target/test-classes"
RUN_DIR="${SMOKE_RUN_DIR:-$BASE/bulk-verify}"
SHOTS="${SMOKE_SHOTS:-$RUN_DIR/screenshots}"
LOG="$RUN_DIR/bulk-verify.log"
DISPLAY_NUM="${SMOKE_DISPLAY:-:79}"
TIMEOUT_S="${SMOKE_TIMEOUT_S:-150}"

echo "=== InvoiceStudio Bulk Dialog Runtime Test (Xvfb) ==="

for tool in Xvfb java timeout; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "[FAIL] $tool not found on PATH (Linux + xvfb required)"; exit 1
    fi
done
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
    echo "[FAIL] packaged jar missing in $BASE/target — run 'mvn package -DskipTests' first"; exit 1
fi
if [ ! -f "$TESTCLASSES/BulkVerifyLauncher.class" ]; then
    echo "[FAIL] BulkVerifyLauncher not compiled: $TESTCLASSES — run 'mvn package -DskipTests' first"; exit 1
fi

rm -rf "$RUN_DIR" "$SHOTS"
mkdir -p "$RUN_DIR" "$SHOTS"
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
    -Dsmoke.shots="$SHOTS" \
    -cp "$JAR:$TESTCLASSES" \
    BulkVerifyLauncher > "$LOG" 2>&1
EXIT_CODE=$?

kill $XVFB_PID 2>/dev/null

echo ""
echo "--- step log (from harness) ---"
grep -E "^\[(PASS|FAIL|SEED|SHOT)" "$LOG" | sed 's/^/  /'

echo ""
echo "--- diagnostics ---"
PASS=0; FAIL=0
check() {
    if [ "$2" = "0" ]; then echo "  [PASS] $1"; PASS=$((PASS+1))
    else echo "  [FAIL] $1"; FAIL=$((FAIL+1)); fi
}

check "harness exit code 0 (all steps + no uncaught exceptions)" "$([ $EXIT_CODE -eq 0 ] && echo 0 || echo 1)"

if grep -q "BULK VERIFY: SUCCESS" "$LOG"; then
    check "harness verdict: SUCCESS" 0
else
    check "harness verdict: SUCCESS" 1
fi

CSS_ERRS=$(grep -icE "css.*(error|parse|warning)|parseexpr|LoadException" "$LOG" 2>/dev/null); CSS_ERRS=${CSS_ERRS:-0}
check "no CSS parse/load errors in log (found $CSS_ERRS)" "$([ "$CSS_ERRS" = "0" ] && echo 0 || echo 1)"

FATALS=$(grep -cE "Exception in thread|Caused by:|NoClassDefFoundError|UnsatisfiedLink" "$LOG" 2>/dev/null); FATALS=${FATALS:-0}
check "no fatal exceptions in log (found $FATALS)" "$([ "$FATALS" = "0" ] && echo 0 || echo 1)"

SHOTS_COUNT=$(ls "$SHOTS"/*.png 2>/dev/null | wc -l)
check "screenshots captured (>= 4, got $SHOTS_COUNT)" "$([ "$SHOTS_COUNT" -ge 4 ] && echo 0 || echo 1)"

echo ""
if [ "$FAIL" = "0" ]; then
    echo "BULK DIALOG TEST: SUCCESS"
    exit 0
else
    echo "BULK DIALOG TEST: FAILED — full log: $LOG"
    echo "--- exception context ---"
    grep -B2 -A12 -E "UNCAUGHT|Exception|at com.invoicestudio" "$LOG" | head -80
    exit 1
fi
