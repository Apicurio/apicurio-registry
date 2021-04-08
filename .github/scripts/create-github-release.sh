#!/bin/bash
set -euxo pipefail

RELEASE_VERSION="$1"
BRANCH="$2"
REPOSITORY="$3"
ACCESS_TOKEN="$4"

generate_post_data() 
{
cat <<EOF
{
  "tag_name": "$RELEASE_VERSION",
  "target_commitish": "$BRANCH",
  "name": "$RELEASE_VERSION",
  "body": "",
  "draft": false,
  "prerelease": false
}
EOF
}

echo "Creating github release '$RELEASE_VERSION' for Repo '$REPOSITORY' and Branch: '$BRANCH'"
curl -H "Authorization: token $ACCESS_TOKEN" --data "$(generate_post_data)" "https://api.github.com/repos/$REPOSITORY/releases"
echo "Github Release Created Successfully"