#!/usr/bin/env bash
# Builds and installs everything in this repository: the domain cores (which the modulith and the
# services build against) and the identity service.
# Needs ff-logistic-base installed first (../base/build.sh).
#
#   ./build.sh
#   ./build.sh -DskipTests
set -euo pipefail
cd "$(dirname "$0")"
./mvnw -B install "$@"
