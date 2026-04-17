#!/bin/bash
# Cross-platform desktop notification when Claude needs attention
if [ "$(uname)" = "Darwin" ]; then
  osascript -e 'display notification "Claude Code needs your attention" with title "Claude Code"' 2>/dev/null
elif command -v notify-send >/dev/null 2>&1; then
  notify-send "Claude Code" "Claude Code needs your attention" 2>/dev/null
fi
exit 0
