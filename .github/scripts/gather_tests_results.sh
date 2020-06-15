#!/usr/bin/env bash

RESULTS_PATH=${1}

FILE_RESULTS=tests_results.json

function get_test_count () {
    _LOOKUP_PATH=${1}
    _TEST_TYPE=${2}
    _VALUES=$(find "${_LOOKUP_PATH}" -name "TEST*.xml" -type f -print0 | xargs -0 sed -n "s#.*${_TEST_TYPE}=\"\([0-9]*\)\".*#\1#p")
    _TEST_COUNTS_ARR=$(echo "${_VALUES}" | tr " " "\n")
    _TEST_COUNT=0

    for item in ${_TEST_COUNTS_ARR}
    do
        _TEST_COUNT=$((_TEST_COUNT + item))
    done

    echo ${_TEST_COUNT}
}

STORAGES=(inmemory asyncmem infinispan jpa kafka streams)
SUMMARY=""

TOTAL_TEST_COUNT=0
TOTAL_TEST_FAILED=0

for variant in "${STORAGES[@]}"
do 
    TEST_COUNT=$(get_test_count "${RESULTS_PATH}/${variant}" "tests")

    TOTAL_TEST_COUNT=$((TOTAL_TEST_COUNT + TEST_COUNT))

    TEST_ERRORS_COUNT=$(get_test_count "${RESULTS_PATH}/${variant}" "errors")
    TEST_SKIPPED_COUNT=$(get_test_count "${RESULTS_PATH}/${variant}" "skipped")
    TEST_FAILURES_COUNT=$(get_test_count "${RESULTS_PATH}/${variant}" "failures")
    TEST_ALL_FAILED_COUNT=$((TEST_ERRORS_COUNT + TEST_FAILURES_COUNT))

    TOTAL_TEST_FAILED=$((TOTAL_TEST_FAILED + TEST_ALL_FAILED_COUNT))

    _VARIANT_SUMMARY="\n**STORAGE VARIANT TESTED:** ${variant}\n**TOTAL:** ${TEST_COUNT}\n**PASS:** $((TEST_COUNT - TEST_ALL_FAILED_COUNT - TEST_SKIPPED_COUNT))\n**FAIL:** ${TEST_ALL_FAILED_COUNT}\n**SKIP:** ${TEST_SKIPPED_COUNT}\n"
    SUMMARY="$SUMMARY$_VARIANT_SUMMARY"

    _VARIANT_FAILED_TESTS=$(find "${RESULTS_PATH}/${variant}" -name 'TEST*.xml' -type f -print0 | xargs -0 sed -n "s#\(<testcase.*time=\"[0-9]*,\{0,1\}[0-9]\{1,3\}\..*[^\/]>\)#\1#p" | awk -F '"' '{print "\\n- " $2 " in "  $4}')
    if [[ -n "${_VARIANT_FAILED_TESTS}" ]]
    then
        FAILED_TEST_BODY="### :heavy_exclamation_mark: Test Failures :heavy_exclamation_mark:${_VARIANT_FAILED_TESTS}\n"
        SUMMARY="$SUMMARY$FAILED_TEST_BODY"
    fi

done

echo "Creating body ..."

if [[ "${TOTAL_TEST_COUNT}" == 0 ]]
then
  BODY="{\"body\":\":heavy_exclamation_mark: **Build Failed** :heavy_exclamation_mark:\"}"
else
  if [[ "${TOTAL_TEST_FAILED}" == 0 ]]
  then
    BODY="{\"body\":\"### :heavy_check_mark: Test Summary :heavy_check_mark:\n${SUMMARY}\"}"
  else
    BODY="{\"body\":\"### :x: Test Summary :x:\n${SUMMARY}\"}"
  fi
fi

echo "${BODY}" > ${FILE_RESULTS}

# Cat created file
cat ${FILE_RESULTS}
