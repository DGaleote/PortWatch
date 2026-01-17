# PortWatch – Portable distribution

This directory contains **fully portable, self-contained distributions** of PortWatch.

Each operating system folder includes:
- The PortWatch fat JAR
- A bundled Java Runtime (JRE)
- A launch script that uses the bundled runtime

No system-wide Java installation is required.

---

## Directory structure

dist/
- windows/
- linux/
- mac/

Each folder is independent and can be copied to a USB drive or any location.

---

## Windows

Contents:
- PortWatch-1.0-SNAPSHOT-all.jar
- jre/
- run.cmd

Execution:
Double-click `run.cmd` or run it from a terminal.

---

## Linux

Contents:
- PortWatch-1.0-SNAPSHOT-all.jar
- jre/
- run.sh

Before first execution:
```bash
chmod +x run.sh
