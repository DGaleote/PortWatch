#!/bin/bash
set -e

cd "$(dirname "$0")"

"./runtime/bin/java" -jar "./PortWatch.jar"
