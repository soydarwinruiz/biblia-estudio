#!/bin/bash
# Uso: ./build_all.sh /ruta/bible-data-es-spa /ruta/macarthur.txt salida.db
set -e
cd "$(dirname "$0")"
python3 build_bibles.py "$1" "$3"
python3 build_macarthur.py "$2" "$3"
python3 build_content.py "$3"
