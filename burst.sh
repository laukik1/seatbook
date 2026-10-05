#!/usr/bin/env sh
# usage: ./burst.sh <BASE_URL> [ADMIN_TOKEN]   (needs JDK 21+)
set -e
exec java "$(dirname "$0")/burst/Burst.java" "${1:-http://localhost:8080}" "${2:-${ADMIN_TOKEN:-admin-secret}}"
