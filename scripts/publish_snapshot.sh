#!/bin/bash

# Copyright (c) Facebook, Inc. and its affiliates.
#
# This source code is licensed under the MIT license found in the/
# LICENSE file in the root directory of this source tree.

# Publishes SNAPSHOTs

REPO_SLUG=BoltsFramework/Bolts-Android
BRANCH=master

set -e

if [ "$TRAVIS_REPO_SLUG" != "$REPO_SLUG" ]; then
  echo "Skipping publishing SNAPSHOT: wrong repository. Expected '$REPO_SLUG' but was '$TRAVIS_REPO_SLUG'"
elif [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo "Skipping publishing SNAPSHOT: was PR"
elif [ "$TRAVIS_BRANCH" != "$BRANCH" ]; then
  echo "Skipping publishing SNAPSHOT: wrong branch. Expected '$BRANCH' but was '$TRAVIS_BRANCH'"
else
  echo "Publishing SNAPSHOT..."
  ./gradlew uploadArchives
  echo "SNAPSHOT published!"
fi
