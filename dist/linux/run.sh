#!/usr/bin/env bash
cd "$(dirname "$0")"
#!/bin/bash
"./runtime/bin/java" -jar "./PortWatch.jar"
# Si NO estamos en una terminal interactiva, pausa para que no se cierre al vuelo
if [ ! -t 1 ]; then
  echo
  read -r -p "Pulsa ENTER para cerrar..."
fi

