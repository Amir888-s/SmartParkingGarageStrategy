#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mvn clean package
mkdir -p dist
cp target/smart-parking-garage-1.0.0.jar dist/
