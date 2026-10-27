#!/bin/sh
# Developer broadcasts (see tools/LinkBroadcast.java). Compiles the game's pure-Java link codec
# with the tool and runs it:  tools/link_broadcast.sh sign tools/broadcasts/example.properties
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/link-broadcast"
mkdir -p "$OUT"
javac -d "$OUT" "$ROOT/app/src/main/java/com/estatica/domain/link/LinkPacket.java" \
  "$ROOT/app/src/main/java/com/estatica/domain/link/LinkCodec.java" "$ROOT/tools/LinkBroadcast.java"
java -cp "$OUT" LinkBroadcast "$@"
