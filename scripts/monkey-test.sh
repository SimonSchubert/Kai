#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.inspiredandroid.kai"
ACTIVITY="com.inspiredandroid.kai.MainActivity"
EVENT_COUNT="${1:-10000}"
THROTTLE="${2:-50}"

echo "=== Kai Monkey Test ==="
echo "Events: $EVENT_COUNT | Throttle: ${THROTTLE}ms"
echo ""

# Check device is connected
if ! adb get-state &>/dev/null; then
    echo "ERROR: No device connected. Connect a device or start an emulator."
    exit 1
fi

# Launch the app
echo "[1/4] Launching app..."
adb shell am start -n "$ACTIVITY/.MainActivity" -W

# Wait for activity to be fully up
sleep 2

# Pin the app (screen pinning / lock task mode)
echo "[2/4] Pinning app to screen..."
TASK_ID=$(adb shell am stack list 2>/dev/null | grep -i "$PACKAGE" | grep -oE 'taskId=[0-9]+' | head -1 | cut -d= -f2)

if [ -z "$TASK_ID" ]; then
    # Fallback: try dumpsys to find the task
    TASK_ID=$(adb shell dumpsys activity activities | grep -i "$PACKAGE" | grep -oE 'task=[0-9]+' | head -1 | cut -d= -f2)
fi

if [ -n "$TASK_ID" ]; then
    adb shell am task lock "$TASK_ID" 2>/dev/null && echo "  App pinned (task $TASK_ID)" || echo "  WARN: Could not pin app. Monkey may escape. Continuing anyway..."
else
    echo "  WARN: Could not find task ID. Monkey may escape. Continuing anyway..."
fi

# Run monkey
echo "[3/4] Running monkey ($EVENT_COUNT events)..."
echo ""
adb shell monkey \
    -p "$PACKAGE" \
    --pct-touch 50 \
    --pct-motion 35 \
    --pct-pinchzoom 5 \
    --pct-rotation 5 \
    --pct-flip 5 \
    --pct-nav 0 \
    --pct-majornav 0 \
    --pct-syskeys 0 \
    --pct-appswitch 0 \
    --pct-permission 0 \
    --throttle "$THROTTLE" \
    --ignore-crashes \
    --ignore-timeouts \
    --ignore-security-exceptions \
    --monitor-native-crashes \
    -v -v \
    "$EVENT_COUNT" 2>&1 | tee /tmp/kai-monkey-results.txt

# Unpin
echo ""
echo "[4/4] Unpinning app..."
adb shell am task lock stop 2>/dev/null || true

# Summary
echo ""
echo "=== Results ==="
CRASHES=$(grep -c "CRASH" /tmp/kai-monkey-results.txt 2>/dev/null || echo "0")
ANRS=$(grep -c "ANR" /tmp/kai-monkey-results.txt 2>/dev/null || echo "0")
echo "Crashes: $CRASHES"
echo "ANRs:    $ANRS"
echo ""
echo "Full log: /tmp/kai-monkey-results.txt"

if [ "$CRASHES" -gt 0 ] || [ "$ANRS" -gt 0 ]; then
    echo ""
    echo "=== Crash Details ==="
    grep -A 10 "CRASH\|ANR" /tmp/kai-monkey-results.txt || true
    exit 1
fi

echo "No crashes detected."
