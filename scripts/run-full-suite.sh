#!/usr/bin/env bash
# Runs UI suite -> load suite -> report aggregator, all sharing one run folder.
# Usage: scripts/run-full-suite.sh [--skip-load]
set -euo pipefail
cd "$(dirname "$0")/.."

RUN_ID="$(date +%Y-%m-%d_%H-%M-%S)"
echo "Run ID: $RUN_ID"

mvn test -Dqa.run.id="$RUN_ID"

if [[ "${1:-}" != "--skip-load" ]]; then
  mvn test -Pload-tests -Dqa.run.id="$RUN_ID"
else
  echo "Skipping load tests (--skip-load)"
fi

mvn exec:java -Dexec.mainClass=com.coresrp.qa.report.ReportMain -Dqa.run.id="$RUN_ID"

echo "Report: reports/$RUN_ID/report.html"
