# 🍎 PortWatch – macOS Portable

This directory contains the **portable macOS distribution** of PortWatch.

It is fully self-contained and **does not require Java to be installed** on the system.

---

## 🔐 Permissions (required)

Before running PortWatch for the first time, execution permissions must be granted.

From a terminal, navigate to this directory and run:

```bash
chmod +x run.sh
```
This only needs to be done once.

▶️ How to run
From terminal (recommended)

From this directory:
```bash
./run.sh
```
This runs PortWatch normally and returns control to the terminal once finished.

📂 Output data

All generated data (snapshots and diffs) is stored locally in a data/ folder created next to the executable.

No system-wide changes are made.

⚠️ Important notes

Execution via Finder (double click) is not supported.

The terminal is required due to macOS security restrictions.

The bundled runtime is specific to macOS.

Administrative privileges are not required.

This behavior is expected and intentional.