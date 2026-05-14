#!/bin/bash
# Block edits to generated files and secrets
INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

if [ -z "$FILE_PATH" ]; then
  exit 0
fi

case "$FILE_PATH" in
  */target/*)
    echo "Blocked: do not edit generated files in target/" >&2
    exit 2
    ;;
  *.env|*.env.*)
    echo "Blocked: do not edit .env files" >&2
    exit 2
    ;;
esac

exit 0
