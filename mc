#!/usr/bin/env bash
# Voordeur van de beheertool: `mc help` toont alle opdrachten. Code in mctool/ (Python 3.9+, alleen standaardbibliotheek).
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHONPATH="$DIR" exec python3 -m mctool "$@"
