
# 🛡️ PortWatch

Cross-platform TCP listening port monitor  
(Windows · Linux · macOS)

PortWatch is a command-line security tool written in Java that detects which processes are listening on TCP ports and tracks changes over time using snapshots and diffs.

It is designed as a small, clean, and extensible monitoring engine suitable for security labs, system auditing, and technical cybersecurity practices.

---

## 🚀 What it does

On each run, PortWatch:

1. Scans the local system for TCP listening sockets
2. Saves a snapshot of the current state
3. Compares it with the previous snapshot (if any)
4. Reports:
    - New listening ports
    - Closed ports
    - Ports that changed owning process

This allows you to detect:

- New services starting
- Suspicious processes opening ports
- Service restarts or crashes
- Configuration or deployment changes over time

PortWatch is designed to be **stateful**: it builds a historical view of the system by comparing consecutive executions.

---

## 🧱 Architecture

PortWatch is built around a clear separation of concerns:
```text
core/
├─ collector/ → OS-specific scanners
├─ diff/ → snapshot comparison engine
├─ model/ → domain objects
├─ io/ → snapshot & diff persistence
├─ exec/ → safe command execution
├─ os/ → OS detection
└─ PortWatchApp → application orchestration
```

Each operating system has its own **collector** that translates native system commands into a common internal format:

| OS      | Native command |
|--------|----------------|
| Windows | PowerShell `Get-NetTCPConnection` |
| Linux   | `ss -lntpHn` |
| macOS   | `lsof -nP -iTCP -sTCP:LISTEN` |

All higher-level logic (diffs, persistence, reporting) is OS-independent.

---

## 🖥️ Running PortWatch

### Portable distributions (recommended)

Ready-to-run **portable distributions** are provided for each supported operating system.

These distributions:

- Include a minimal Java runtime
- Do **not** require Java to be installed
- Store all generated data locally next to the executable

📦 See details here:  
➡️ **[`dist/`](./dist)**

---

### Running from source / JAR

For development or Java-enabled environments, PortWatch can also be executed directly from the JAR.

This mode is intended for development and testing.

---

## ⚙️ CLI usage
```text
portwatch [--snapshot | --diff]
          [--output=console|file]
          [--output-dir=<path>]
          [--report=md|html]
```
---

### Modes

- **Default (no flags)**
    - If no baseline exists: creates a baseline snapshot
    - Otherwise: generates a snapshot + diff

- **`--snapshot`**
    - Snapshot-only mode
    - No diff is generated

- **`--diff`**
    - Diff-only mode
    - Requires an existing baseline snapshot

`--snapshot` and `--diff` are mutually exclusive.

---

### Output modes

- **`--output=console`**
    - No files written
    - Snapshot or diff is computed in memory
    - Intended for quick inspection

- **`--output=file`**
    - Writes snapshot and/or diff to disk
    - Prints file paths to stdout

- **Implicit output (no `--output`)**
    - Combined mode
    - Writes files **and** prints a console summary

---

### Reports (MD / HTML)

PortWatch can generate **human-readable reports** from diffs.
```text
--report=md
--report=html
```

Rules:

- `--report` **requires `--diff`**
- Reports require persisted data  
  (`--output=console` is not allowed with reports)

Generated reports are stored under:
```text
data/
└─ reports/
   └─ <machine-id>/
      ├─ report-<machine-id>-<timestamp>.md
      └─ report-<machine-id>-<timestamp>.html
```


Reports include:

- System metadata (machine, OS, timestamp)
- Narrative event descriptions
- Ordered summary table of changes

---

## 📁 Data layout

By default, PortWatch stores all data under `./data`:

```text
data/
├─ snapshots/
│  └─ <machine-id>/
│     └─ snapshot-<machine-id>-<timestamp>.json
├─ diffs/
│  └─ <machine-id>/
│     └─ diff-<machine-id>-<timestamp>.json
└─ reports/
   └─ <machine-id>/
      ├─ report-<machine-id>-<timestamp>.md
      └─ report-<machine-id>-<timestamp>.html

```

The base directory can be overridden using:
- The CLI flag ```--output-dir=<path>```
- The environment variable ```PORTWATCH_DATA_DIR```

---

## 📂 Custom data directories (```--output-dir```)

PortWatch allows overriding the default ./data directory to control where snapshots, diffs, and reports are stored.
This is useful for:

- Portable executions (USB drives)
- Shared network storage
- Centralized analysis across multiple machines

### Local directory example

Store all PortWatch data in a custom local folder:

```bash
portwatch --diff --output-dir=./portwatch-data
```

This will produce the following structure:

```text
./portwatch-data/
├─ snapshots/
├─ diffs/
└─ reports/
```

### Network / shared directory example

Store data on a shared network location (for example, a mounted SMB or NFS share):

Windows (UNC path):

```bash
portwatch --diff --output-dir=\\SERVER\shared\portwatch
```

Linux / macOS (mounted share):

```bash
portwatch --diff --output-dir=/mnt/portwatch-share
```

This allows multiple machines to:

- Write snapshots independently

- Generate diffs and reports in a centralized location

- Build a shared historical view per machine ID

Each machine still stores its data under its own ```<machine-id>``` directory.

## 🧠 Design notes

- The **baseline snapshot** is created automatically on first run
- Diffs are always computed between the **latest snapshot** and the current state
- Socket identity is based on **address + port**, independent of the owning process
- Process changes on the same port are reported as **changed**, not removed/added
- The application is intentionally:
    - CLI-only
    - deterministic
    - backend-focused

---

## 🎯 Purpose

This project was developed as part of cybersecurity technical training, with emphasis on:

- OS-level networking
- Process inspection
- Cross-platform architecture
- Clean and maintainable Java design
- Reproducible system monitoring

PortWatch is not a GUI tool by design.  
It is intended as a **technical foundation** for understanding and auditing system exposure over time.