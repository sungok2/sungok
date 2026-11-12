#!/usr/bin/env sh
set -eu
if [ "$#" -ne 2 ]; then
  echo "Usage: tests/run-escl-network-integration.sh <private-ip:port> <jpeg|png>"; exit 2
fi
cd "$(dirname "$0")/.."
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT HUP INT TERM
javac --release 17 -encoding UTF-8 -d "$TEMP" \
  app/src/main/java/com/galaxyuniversalio/EsclProtocol.java \
  app/src/main/java/com/galaxyuniversalio/EsclScanClient.java \
  app/src/main/java/com/galaxyuniversalio/LanEsclTransport.java \
  tests/EsclNetworkIntegrationTests.java
java -cp "$TEMP" com.galaxyuniversalio.EsclNetworkIntegrationTests "$1" "$2"
