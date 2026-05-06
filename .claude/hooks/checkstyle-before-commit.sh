#!/bin/bash
# Run checkstyle on modules with staged Java files before committing
INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

# Only trigger on git commit commands
if ! echo "$COMMAND" | grep -qE '^git commit'; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR" || exit 0

# Find modules with staged Java files
MODULES=$(git diff --cached --name-only --diff-filter=ACMR -- '*.java' \
  | sed -n 's|^\(.*\)/src/.*|\1|p' \
  | sort -u)

if [ -z "$MODULES" ]; then
  exit 0
fi

# Run checkstyle on each affected module
FLAGS="-Dfull -Pintegration-tests -Pexamples" # Have to enable all profiles, otherwise Maven can't find some modules.
FAILED=0
for MODULE in $MODULES; do
  if ! ./mvnw -q checkstyle:check -pl "$MODULE" $FLAGS 2>/dev/null; then
    echo "Checkstyle failed for module: $MODULE" >&2
    FAILED=1
  fi
done

if [ "$FAILED" -eq 1 ]; then
  echo "Fix checkstyle violations before committing. Run: ./mvnw checkstyle:check -pl <module>" >&2
  exit 2
fi

exit 0
