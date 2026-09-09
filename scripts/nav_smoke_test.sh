#!/bin/bash
# InvoiceStudio DEEP runtime verification: launches the real packaged app under
# Xvfb (virtual display) and drives every view through REAL navigation — fired
# UI buttons, dialogs, edit/duplicate/convert flows, dashboard month navigation —
# taking a screenshot of each step, then greps the log for failures.
#
# Usage (Linux, Xvfb required:  sudo apt install xvfb ):
#   mvn package              # harness runs the PACKAGED jar, not target/classes
#   ./scripts/nav_smoke_test.sh
#
# Optional overrides (environment variables):
#   SMOKE_RUN_DIR   working dir for the isolated run (default <repo>/nav-smoke)
#   SMOKE_SHOTS     screenshot output dir (default <run dir>/screenshots)
#   SMOKE_DISPLAY   X display number (default :78)
#   SMOKE_TIMEOUT_S hard kill timeout for the whole run (default 180s)
set -u

BASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$BASE/target/invoice-studio-desktop-2.0.1.jar"
TESTCLASSES="$BASE/target/test-classes"
RUN_DIR="${SMOKE_RUN_DIR:-$BASE/nav-smoke}"
SHOTS="${SMOKE_SHOTS:-$RUN_DIR/screenshots}"
LOG="$RUN_DIR/nav-smoke.log"
DISPLAY_NUM="${SMOKE_DISPLAY:-:78}"
TIMEOUT_S="${SMOKE_TIMEOUT_S:-180}"

echo "=== InvoiceStudio Deep Navigation Runtime Test (Xvfb) ==="

# --- sanity checks -----------------------------------------------------------
for tool in Xvfb java timeout; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "[FAIL] $tool not found on PATH (Linux + xvfb required)"; exit 1
    fi
done
if [ ! -f "$JAR" ]; then
    echo "[FAIL] packaged jar missing: $JAR — run 'mvn package' first"; exit 1
fi
if [ ! -f "$TESTCLASSES/SmokeLauncher.class" ]; then
    echo "[FAIL] SmokeLauncher not compiled: $TESTCLASSES — run 'mvn package' (or 'mvn test-compile') first"; exit 1
fi

rm -rf "$RUN_DIR" "$SHOTS"
mkdir -p "$RUN_DIR" "$SHOTS"
cd "$RUN_DIR"   # CWD == fresh dir; -Dinvoicestudio.data.dir below keeps DB isolated

Xvfb $DISPLAY_NUM -screen 0 1440x900x24 &
XVFB_PID=$!
sleep 2
if ! kill -0 $XVFB_PID 2>/dev/null; then
    echo "[FAIL] Xvfb failed to start"; exit 1
fi
echo "Xvfb on $DISPLAY_NUM (pid $XVFB_PID)"

# EXTRA_XLIBS: only needed in stripped-down containers whose system libraries
# are incomplete; on a normal desktop Linux this directory doesn't exist and
# the variable simply stays empty.
XLIBS_DIR="$BASE/../xlibs/extracted/usr/lib/x86_64-linux-gnu"
LD_EXTRA=""
[ -d "$XLIBS_DIR" ] && LD_EXTRA=":$XLIBS_DIR"

DISPLAY=$DISPLAY_NUM LD_LIBRARY_PATH="$LD_EXTRA" \
    timeout --signal=INT ${TIMEOUT_S}s java \
    -Dprism.order=sw \
    -Dinvoicestudio.data.dir="$RUN_DIR" \
    -Dsmoke.shots="$SHOTS" \
    -cp "$JAR:$TESTCLASSES" \
    SmokeLauncher > "$LOG" 2>&1
EXIT_CODE=$?

kill $XVFB_PID 2>/dev/null

echo ""
echo "--- step log (from harness) ---"
grep -E "^\[(PASS|FAIL|SEED|DIALOG|SHOT)" "$LOG" | sed 's/^/  /'

echo ""
echo "--- diagnostics ---"
PASS=0; FAIL=0
check() {
    if [ "$2" = "0" ]; then echo "  [PASS] $1"; PASS=$((PASS+1))
    else echo "  [FAIL] $1"; FAIL=$((FAIL+1)); fi
}

check "harness exit code 0 (all steps + no uncaught exceptions)" "$([ $EXIT_CODE -eq 0 ] && echo 0 || echo 1)"

if grep -q "NAV SMOKE: SUCCESS" "$LOG"; then
    check "harness verdict: SUCCESS" 0
else
    check "harness verdict: SUCCESS" 1
fi

CSS_ERRS=$(grep -icE "css.*(error|parse|warning)|parseexpr|LoadException" "$LOG" 2>/dev/null); CSS_ERRS=${CSS_ERRS:-0}
check "no CSS parse/load errors in log (found $CSS_ERRS)" "$([ "$CSS_ERRS" = "0" ] && echo 0 || echo 1)"

FATALS=$(grep -cE "Exception in thread|Caused by:|NoClassDefFoundError|UnsatisfiedLink" "$LOG" 2>/dev/null); FATALS=${FATALS:-0}
check "no fatal exceptions in log (found $FATALS)" "$([ "$FATALS" = "0" ] && echo 0 || echo 1)"

SHOTS_COUNT=$(ls "$SHOTS"/*.png 2>/dev/null | wc -l)
check "screenshots captured (>= 20, got $SHOTS_COUNT)" "$([ "$SHOTS_COUNT" -ge 20 ] && echo 0 || echo 1)"

check "isolated DB created in $RUN_DIR" "$(test -s "$RUN_DIR/invoicestudio.db" && echo 0 || echo 1)"

echo ""
if [ "$FAIL" = "0" ]; then
    echo "DEEP NAV TEST: SUCCESS"
    exit 0
else
    echo "DEEP NAV TEST: FAILED — full log: $LOG"
    echo "--- exception context ---"
    grep -B2 -A12 -E "UNCAUGHT|Exception|at com.invoicestudio" "$LOG" | head -100
    exit 1
fi
