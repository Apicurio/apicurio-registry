#!/bin/sh

echo "Merging latest changes from the master branch!"
git fetch upstream master
git merge upstream/master

echo "Merge complete.  Check for conflicts!"
echo ""
while [ "x$CONTINUE" != "xyes" ]
do
  read -p "Have you fixed all conflicts? [yes]" CONTINUE
  if [[ -z $CONTINUE ]] ; then
    CONTINUE=yes
  fi
done

echo "OK great, updating version number using MAS scheme."
CURRENT_VERSION=`mvn help:evaluate -Dexpression=project.version -q -DforceStdout`
if [[ "$CURRENT_VERSION" =~ .*"-".* ]]; then
   arrIN=(${CURRENT_VERSION//-/ })
   CURRENT_VERSION=${arrIN[0]}
fi
MAS_BUILD_NUMBER=`mvn help:evaluate -Dexpression=mas.build.number -q -DforceStdout`
NEW_MAS_BUILD_NUMBER=$(($MAS_BUILD_NUMBER+1))
NEW_VERSION="$CURRENT_VERSION-$NEW_MAS_BUILD_NUMBER"

echo "Updating the MAS version to: $NEW_VERSION"
mvn versions:set -DnewVersion=$NEW_VERSION -DgenerateBackupPoms=false -DprocessAllModules=true
echo "Updating the MAS build number to: $NEW_MAS_BUILD_NUMBER"
mvn versions:set-property -Dproperty=mas.build.number -DgenerateBackupPoms=false -DnewVersion=$NEW_MAS_BUILD_NUMBER

git status
echo "MAS version and build numbers updated (see changes above)."

CONTINUE="no"
while [ "x$CONTINUE" != "xyes" ]
do
  read -p "OK to push changes to 'mas-sr' branch? [yes]" CONTINUE
  if [[ -z $CONTINUE ]] ; then
    CONTINUE=yes
  fi
done

git add .
git commit -m "Updated MAS version to $NEW_VERSION and build number to $NEW_MAS_BUILD_NUMBER"
git push upstream mas-sr

echo "All done!  Everything was successful.  Great job.  You're killing it!"
