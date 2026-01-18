#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")" && pwd)"
gnome-terminal -- bash -lc "cd \"$DIR\" && ./run.sh; echo; read -p 'Pulsa ENTER para cerrar...'"
