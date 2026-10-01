#!/usr/bin/env bash
# Starts the inference service on all interfaces so a real iPhone on the same Wi-Fi can reach it.
set -euo pipefail
cd "$(dirname "$0")"
if [[ -x .venv/bin/python ]]; then PY=.venv/bin/python; else PY=python3; fi
exec "$PY" -m uvicorn app.main:app --host 0.0.0.0 --port "${PORT:-8000}"
