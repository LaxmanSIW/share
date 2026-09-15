#!/bin/bash
# InvoiceStudio runtime verification: Settings Knowledge Hub + Brightness
# Threshold slider + design-canvas variable resolution (TSC quality round).
#
# Usage:
#   mvn package -DskipTests && mvn test-compile
#   ./scripts/knowledge_verify_test.sh
set -u

BASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$(ls "$BASE"/target/invoice-studio-desktop-*.jar 2>/dev/null | grep -v original | head -1)"
TESTCLASSES="$BASE/target/test-classes"
RUN_DIR="${SMOKE_RUN_DIR:-$BASE/knowledge-verify}"
LOG="$RUN_DIR/knowledge-verify.log"
DISPLAY_NUM="${SMOKE_DISPLAY:-:84}"
TIMEOUT_S="${SMOKE_TIMEOUT_S:-150}"

echo "=== InvoiceStudio Settings/Knowledge Runtime Test (Xvfb) ==="

for tool in Xvfb java timeout; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "[FAIL] $tool not found on PATH (Linux + xvfb required)"; exit 1
    fi
done
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
    echo "[FAIL] packaged jar missing in $BASE/target — run 'mvn package -DskipTests' first"; exit 1
fi
if [ ! -f "$TESTCLASSES/SettingsKnowledgeLauncher.class" ]; then
    echo "[FAIL] SettingsKnowledgeLauncher not compiled: $TESTCLASSES — run 'mvn test-compile' first"; exit 1
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
    -Dsmoke.shots="$RUN_DIR/screenshots" \
    -cp "$JAR:$TESTCLASSES" \
    SettingsKnowledgeLauncher > "$LOG" 2>&1
EXIT_CODE=$?

kill $XVFB_PID 2>/dev/null

echo "--- harness output ---"
grep -v "^$" "$LOG" | grep -Ev "WARNING|log4j" | tail -30
echo "----------------------"

if [ $EXIT_CODE -eq 0 ] && grep -q "SETTINGS KNOWLEDGE VERIFY: SUCCESS" "$LOG"; then
    echo "[PASS] settings/knowledge runtime verification SUCCESS"
    if grep -q "\[UNCAUGHT" "$LOG"; then
        echo "[FAIL] uncaught exceptions present"; exit 1
    fi
    SHOTS=$(ls "$RUN_DIR/screenshots" 2>/dev/null | wc -l)
    echo "[PASS] screenshots captured: $SHOTS"
    exit 0
else
    echo "[FAIL] settings/knowledge runtime verification FAILED (exit=$EXIT_CODE)"
    grep -E "UNCAUGHT|FAIL" "$LOG" | head -10
    exit 1
fi
