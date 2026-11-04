#!/bin/sh
set -e
cd "$(dirname "$0")/.."
mkdir -p build/export_run
echo "eula=true" > build/export_run/eula.txt
rm -rf export
./gradlew runExport_data --args=nogui --console=plain -q
test -f export/meta.json
