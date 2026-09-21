#!/usr/bin/env bash
set -euo pipefail

# Keep released history separate from the current development version. This is
# also run on the rendered copy, so a Maven minor-version bump needs no YAML edit.
FILE=$1
export CHANNEL=$2
export YQ=${YQ:-yq}
export PLACEHOLDER='${PLACEHOLDER_PACKAGE}'
export PACKAGE_PLACEHOLDER='${PLACEHOLDER_PACKAGE_NAME}'

if [ "$#" -eq 5 ]; then
    export RELEASED_PACKAGE=$3 RELEASED_CHANNEL=$4 RELEASED_IMAGE=$5
    # Finalize the rolling placeholder before moving the development channel.
    "$YQ" -i '(.entries[] | select(.schema == "olm.channel") | .entries) |=
        map(select(.name != strenv(PLACEHOLDER)))' "$FILE"
    # The released CSV carries its predecessor in the existing rolling head.
    export HEAD
    HEAD=$("$YQ" '.entries[] | select(.schema == "olm.channel" and .name == "3.x") | .entries[0].name' "$FILE")
    if [ "$HEAD" != "$RELEASED_PACKAGE" ]; then
        "$YQ" -i '(.entries[] | select(.schema == "olm.channel" and .name == "3.x") | .entries) |=
            [{"name": strenv(RELEASED_PACKAGE), "replaces": strenv(HEAD)}] + .' "$FILE"
    fi
    if ! "$YQ" -e '.entries[] | select(.schema == "olm.channel" and .name == strenv(RELEASED_CHANNEL))' "$FILE" >/dev/null 2>&1; then
        "$YQ" -i '.entries += [{"schema": "olm.channel", "package": strenv(PACKAGE_PLACEHOLDER), "name": strenv(RELEASED_CHANNEL), "entries": []}]' "$FILE"
    fi
    export MINOR_HEAD
    MINOR_HEAD=$("$YQ" '.entries[] | select(.schema == "olm.channel" and .name == strenv(RELEASED_CHANNEL)) | [.entries[] | select(.name != strenv(PLACEHOLDER))] | .[0].name // ""' "$FILE")
    if [ "$MINOR_HEAD" != "$RELEASED_PACKAGE" ]; then
        "$YQ" -i '(.entries[] | select(.schema == "olm.channel" and .name == strenv(RELEASED_CHANNEL)) | .entries) |=
            [{"name": strenv(RELEASED_PACKAGE)}] + .' "$FILE"
        if [ -n "$MINOR_HEAD" ]; then
            "$YQ" -i '(.entries[] | select(.schema == "olm.channel" and .name == strenv(RELEASED_CHANNEL)) | .entries[0].replaces) = strenv(MINOR_HEAD)' "$FILE"
        fi
    fi
    if ! "$YQ" -e '.entries[] | select(.schema == "olm.bundle" and .image == strenv(RELEASED_IMAGE))' "$FILE" >/dev/null 2>&1; then
        "$YQ" -i '.entries += [{"schema": "olm.bundle", "image": strenv(RELEASED_IMAGE)}]' "$FILE"
    fi
fi

"$YQ" -i '(.entries[] | select(.schema == "olm.channel") | .entries) |= map(select(.name != strenv(PLACEHOLDER)))' "$FILE"
"$YQ" -i 'del(.entries[] | select(.schema == "olm.channel" and .name != "3.x" and .name != strenv(CHANNEL)) | select(.entries | length == 0))' "$FILE"
if ! "$YQ" -e '.entries[] | select(.schema == "olm.channel" and .name == strenv(CHANNEL))' "$FILE" >/dev/null 2>&1; then
    "$YQ" -i '.entries += [{"schema": "olm.channel", "package": strenv(PACKAGE_PLACEHOLDER), "name": strenv(CHANNEL), "entries": []}]' "$FILE"
fi
"$YQ" -i '(.entries[] | select(.schema == "olm.channel" and (.name == "3.x" or .name == strenv(CHANNEL))) | .entries) |=
    [{"name": strenv(PLACEHOLDER)}] + .' "$FILE"
for TARGET in 3.x "$CHANNEL"; do
    export TARGET
    export PREDECESSOR
    PREDECESSOR=$("$YQ" '.entries[] | select(.schema == "olm.channel" and .name == strenv(TARGET)) | .entries[1].name // ""' "$FILE")
    if [ -n "$PREDECESSOR" ]; then
        "$YQ" -i '(.entries[] | select(.schema == "olm.channel" and .name == strenv(TARGET)) | .entries[0].replaces) = strenv(PREDECESSOR)' "$FILE"
    fi
done
