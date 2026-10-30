#!/bin/bash
# Turns the compiler and test errors in a build log into a single GitHub annotation, so they can be
# read from the check run without downloading logs. Usage: annotate-errors.sh <log file>
log="$1"
[ -f "$log" ] || { echo "::error::No log file $log"; exit 0; }
errors=$(grep -E "error:|fatal error|failed \(|: error" "$log" | sed -E 's#^/Users/runner/work/[^/]+/[^/]+/##' | sort -u | head -80)
if [ -z "$errors" ]; then
  errors=$(tail -40 "$log")
fi
# Annotations are one line: encode newlines and the characters GitHub reserves.
message=$(printf '%s' "$errors" | sed -e 's/%/%25/g' -e 's/\r//g' | awk 'BEGIN{ORS="%0A"} {print}')
echo "::error title=Build errors::${message}"
