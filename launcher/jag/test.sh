#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
[[ -x ${JAVA_HOME:-}/bin/javac ]] || { echo 'set JAVA_HOME to JDK 17' >&2; exit 1; }
host=$(mktemp -d "${TMPDIR:-/tmp}/jag-models.XXXXXX")
trap 'rm -rf "$host"' EXIT
"$JAVA_HOME/bin/javac" --release 8 -Xlint:all -Werror -d "$host" \
  src/com/jag/home/dev/{Favorites,Navigation,MemoryPolicy,WallpaperPolicy,Configuration,RecentHistory,Theme,LayoutPolicy}.java \
  tests/com/jag/home/dev/ModelTest.java
"$JAVA_HOME/bin/java" -cp "$host" com.jag.home.dev.ModelTest
python3 tests/safety_test.py
