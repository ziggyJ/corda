#!/bin/bash

source .ci/ci-gradle-build-cache-init.sh

# Tools
echo ":tools:blobinspector"
cd tools/blobinspector
../../gradlew  --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test install --build-cache ${PERFORM_GRADLE_SCAN}

echo ":tools:bootstrapper"
cd ../bootstrapper
../../gradlew  --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean install --build-cache ${PERFORM_GRADLE_SCAN}

echo ":tools:demobench"
cd ../demobench
../../gradlew  --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test install --build-cache ${PERFORM_GRADLE_SCAN}

echo ":tools:explorer"
cd ../explorer
../../gradlew  --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test install --build-cache ${PERFORM_GRADLE_SCAN}

echo ":tools:network-bootstrapper"
cd ../network-bootstrapper
../../gradlew  --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test jar --build-cache ${PERFORM_GRADLE_SCAN}

echo ":tools:shell"
cd ../shell
../../gradlew  --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test integrationTest install --build-cache ${PERFORM_GRADLE_SCAN}

echo ":tools:shell-cli"
cd ../shell-cli
../../gradlew  --stacktrace -Dorg.gradle.daemon=${USE_GRADLE_DAEMON} -Dorg.gradle.caching.debug=${GRADLE_CACHE_DEBUG} clean test install --build-cache ${PERFORM_GRADLE_SCAN}
