#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
WORK=$(mktemp -d "${TMPDIR:-/tmp}/jag-home-policy.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
"${JAVA_HOME:?Set JDK 17}/bin/javac" --release 8 -Xlint:all -Werror -d "$WORK" \
  stable/src/com/jag/home/integration/GuardPolicy.java stable/tests/com/jag/home/integration/GuardPolicyTest.java
"$JAVA_HOME/bin/java" -cp "$WORK" com.jag.home.integration.GuardPolicyTest
python3 -m unittest discover -s home/tests -v
python3 stable/tests/safety_test.py
