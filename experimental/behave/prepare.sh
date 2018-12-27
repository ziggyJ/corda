#!/bin/bash

set -x

# Please ensure you run this script using source code (eg. GitHub master, branch or TAG) that reflects the version label defined below
# For example:
#   os-master   => git clone https://github.com/corda/corda
#   enterprise-master => git clone https://github.com/corda/enterprise
VERSION=master

STAGING_DIR="${STAGING_ROOT:-$TMPDIR/staging}"
echo "Staging directory: $STAGING_DIR"

CORDA_DIR=${STAGING_DIR}/corda/os/${VERSION}
echo "Corda staging directory: $CORDA_DIR"

CORDAPP_DIR=${CORDA_DIR}/apps
echo "CorDapp staging directory: $CORDAPP_DIR"

DRIVERS_DIR=${STAGING_DIR}/drivers
echo "Drivers staging directory: $DRIVERS_DIR"

# Set up directories
mkdir -p ${STAGING_DIR} || { echo "Unable to create directory $STAGING_DIR"; exit; }
mkdir -p ${CORDA_DIR}
mkdir -p ${CORDAPP_DIR}
mkdir -p ${DRIVERS_DIR}

# Copy Corda capsule into staging
cd ../..
./gradlew :node:capsule:buildCordaJar :finance:contracts:jar :finance:workflows:jar
cp -v $(ls -t node/capsule/build/libs/corda-*.jar | head !ls-1) ${CORDA_DIR}/corda.jar

# Copy finance CorDapps (contracts and flows)
cp -v $(ls -t finance/contracts/build/libs/corda-finance-*.jar | head -1) ${CORDAPP_DIR}
cp -v $(ls -t finance/workflows/build/libs/corda-finance-*.jar | head -1) ${CORDAPP_DIR}

# Download database drivers
curl "https://search.maven.org/remotecontent?filepath=com/h2database/h2/1.4.196/h2-1.4.196.jar" > ${DRIVERS_DIR}/h2-1.4.196.jar
curl -L "http://central.maven.org/maven2/org/postgresql/postgresql/42.1.4/postgresql-42.1.4.jar" > ${DRIVERS_DIR}/postgresql-42.1.4.jar

# Build Network Bootstrapper
./gradlew tools:bootstrapper:jar
cp -v $(ls tools/bootstrapper/build/libs/*.jar | tail -n1) ${CORDA_DIR}/network-bootstrapper.jar
