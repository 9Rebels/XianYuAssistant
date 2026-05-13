#!/bin/sh
set -e

DISPLAY="${DISPLAY:-:99}"
DISPLAY_NUM="${DISPLAY#:}"
XVFB_LOG=/tmp/xvfb.log
XVFB_SOCKET="/tmp/.X11-unix/X${DISPLAY_NUM}"

mkdir -p /tmp/.X11-unix
chmod 1777 /tmp /tmp/.X11-unix || true
rm -f "/tmp/.X${DISPLAY_NUM}-lock" "$XVFB_LOG"

echo "启动虚拟显示服务器 Xvfb..."
Xvfb "$DISPLAY" -screen 0 1920x1080x24 -ac -nolisten tcp -dpi 96 +extension RANDR >"$XVFB_LOG" 2>&1 &
XVFB_PID=$!

ATTEMPTS=0
while [ "$ATTEMPTS" -lt 10 ]; do
  if kill -0 "$XVFB_PID" 2>/dev/null && [ -S "$XVFB_SOCKET" ]; then
    break
  fi
  ATTEMPTS=$((ATTEMPTS + 1))
  sleep 1
done

if ! kill -0 "$XVFB_PID" 2>/dev/null || [ ! -S "$XVFB_SOCKET" ]; then
  echo "Xvfb 启动失败，日志如下："
  cat "$XVFB_LOG" 2>/dev/null || true
  exit 1
fi

echo "Xvfb 已启动 (PID: $XVFB_PID, DISPLAY=$DISPLAY)"
echo "DISPLAY=$DISPLAY"

# 启动 Java 应用
echo "启动 XianYuAssistant..."
exec java ${JAVA_OPTS} -Dserver.port=${SERVER_PORT} -jar app.jar
