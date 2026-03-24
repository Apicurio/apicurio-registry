#!/bin/bash
# Auto-run checkstyle after editing a Java file
INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

if [ -z "$FILE_PATH" ]; then
  exit 0
fi

# Only run for Java files
if echo "$FILE_PATH" | grep -q '\.java$'; then
  MODULE=$(echo "$FILE_PATH" | sed -n 's|^\(.*\)/src/.*|\1|p')
  if [ -n "$MODULE" ]; then
    cd "$CLAUDE_PROJECT_DIR" || exit 0
    ./mvnw -q checkstyle:check -pl "$MODULE" 2>/dev/null || \
      echo "Checkstyle warning: run ./mvnw checkstyle:check -pl $MODULE to see details" >&2
  fi
fi

exit 0
