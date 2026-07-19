#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../desktop/linux"
make clean
make
