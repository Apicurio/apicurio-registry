#!/bin/bash

# DB_VERSION_BUILD=$(yq .project.properties."apicurio.sql.storage.db-version" app/pom.xml -r)
DB_VERSION_BUILD=$(cat app/src/main/resources/io/apicurio/registry/storage/impl/sql/db-version)
echo "Build's DB version is $DB_VERSION_BUILD"

DDLS="app/src/main/resources/io/apicurio/registry/storage/impl/sql/postgresql.ddl app/src/main/resources/io/apicurio/registry/storage/impl/sql/h2.ddl app/src/main/resources/io/apicurio/registry/storage/impl/sql/mysql.ddl app/src/main/resources/io/apicurio/registry/storage/impl/sql/mssql.ddl"
for ddl in $DDLS 
do
    echo "Processing DDL $ddl"
    DB_VERSION_INSERT=$(grep "INSERT INTO apicurio (propName, propValue) VALUES ('db_version'" $ddl)
    DB_VERSION_IN_DDL=$(echo $DB_VERSION_INSERT | awk '{ print $8 }' - | awk -F ")" '{ print $1}' -)
    echo "DB version in DDL is $DB_VERSION_IN_DDL"

    if (( $(echo "$DB_VERSION_BUILD $DB_VERSION_IN_DDL" | awk '{print ($1 != $2)}') )); then
        echo "DB version mismatch between DDL and build"
        exit 1
    fi
done
echo "DB version ok between build and DDLs"

# ---------------------------------------------------------------------------
# Checkstyle configuration consistency
#
# Guards the class of bug where the build silently enforces far less than the
# documentation claims: a pom pointing at a stripped-down config, a second
# config drifting in a sub-module, or a <headerLocation> wired up with no
# header module to consume it.
# ---------------------------------------------------------------------------
CHECKSTYLE_CONFIG=".checkstyle/checkstyle.xml"

if [ ! -f "$CHECKSTYLE_CONFIG" ]; then
    echo "Missing $CHECKSTYLE_CONFIG"
    exit 1
fi

# Every module must use the one config. A <configLocation> naming anything else
# means some module is being checked against different rules.
BAD_CONFIG_REFS=$(grep -rn "<configLocation>" --include=pom.xml . \
    | grep -v "/target/" \
    | grep -v "/\.checkstyle/checkstyle\.xml<" || true)
if [ -n "$BAD_CONFIG_REFS" ]; then
    echo "Checkstyle <configLocation> must point at $CHECKSTYLE_CONFIG:"
    echo "$BAD_CONFIG_REFS"
    exit 1
fi

# Strip XML comments so a commented-out rule is never mistaken for a live one.
CHECKSTYLE_ACTIVE=$(sed -e 's/<!--.*-->//g' "$CHECKSTYLE_CONFIG" \
    | awk '/<!--/{skip=1} !skip{print} /-->/{skip=0}')

# <headerLocation> only makes sense alongside an active header module.
# Skip target/, which can hold generated poms from an earlier local build.
HEADER_REFS=$(grep -rn "<headerLocation>" --include=pom.xml . 2>/dev/null \
    | grep -v "/target/" || true)
if [ -n "$HEADER_REFS" ]; then
    if ! printf '%s\n' "$CHECKSTYLE_ACTIVE" | grep -q 'name="RegexpHeader"\|name="Header"'; then
        echo "<headerLocation> is configured but $CHECKSTYLE_CONFIG declares no"
        echo "header module, so the license header check would be inert:"
        echo "$HEADER_REFS"
        exit 1
    fi
fi

# Rules documented as enforced must really carry severity=error.
ENFORCED_RULES="FileTabCharacter UnusedImports RedundantImport IllegalImport \
OneStatementPerLine DefaultComesLast UpperEll PackageName ClassTypeParameterName \
MethodTypeParameterName InterfaceTypeParameterName MethodParamPad TypecastParenPad"

# printf rather than echo: the config holds regex properties containing
# backslashes, and echo's escape handling is implementation-defined.
# Match the severity property specifically, so an unrelated property that
# happens to be valued "error" cannot mark a staged rule as enforced.
ACTUALLY_ENFORCED=$(printf '%s\n' "$CHECKSTYLE_ACTIVE" | awk '
    /<module name="/ {
        match($0, /name="[^"]+"/)
        current = substr($0, RSTART + 6, RLENGTH - 7)
        if ($0 ~ /\/>/) { current = "" }   # self-closing: no severity override
        next
    }
    current != "" && /name="severity"/ && /value="error"/ { print current }
    /<\/module>/ { current = "" }
')

for rule in $ENFORCED_RULES; do
    if ! printf '%s\n' "$ACTUALLY_ENFORCED" | grep -qx "$rule"; then
        echo "Rule '$rule' is documented as enforced but is not set to"
        echo "severity=error in $CHECKSTYLE_CONFIG."
        exit 1
    fi
done
echo "Checkstyle config ok: $(printf '%s\n' "$ACTUALLY_ENFORCED" | grep -c .) rules enforced"
