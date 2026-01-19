# 🐧 PortWatch – Linux Portable (Ubuntu)

This directory contains the **portable Linux distribution** of PortWatch.

It is fully self-contained and **does not require Java to be installed** on the system.

This version has been tested on **Ubuntu**.

---

## 🔐 Permissions (required)

Before running PortWatch for the first time, execution permissions must be granted to the scripts.

From a terminal, navigate to this directory and run:

```bash
chmod +x run.sh run-terminal.sh
```

▶️ How to run
From terminal (recommended)

From this directory:
```bash
./run.sh
```
This runs PortWatch normally and returns control to the terminal once finished.

From file manager (GUI)

Using the system file manager:

Right-click on run-terminal.sh

Select “Run as Program”

Note: Double-click execution may open the script in a text editor depending on system configuration.

📂 Output data

All generated data (snapshots and diffs) is stored locally in a data/ folder created next to the executable.

No system-wide changes are made.

ℹ️ Notes

The bundled runtime is specific to Linux.

Administrative privileges are not required.

Terminal-based execution is the most reliable method.