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
3. Compares it with the previous snapshot
4. Reports:
   - New listening ports
   - Closed ports
   - Ports that changed owning process

This allows you to detect:
- new services starting
- suspicious processes opening ports
- service restarts or crashes
- configuration changes over time

---

## 🧱 Architecture

PortWatch is built around a clean separation of concerns:

core/
├─ collectors/ → OS-specific scanners
├─ diff/ → snapshot comparison engine
├─ model/ → domain objects
├─ io/ → snapshot & diff persistence
└─ PortWatchApp → application orchestration


Each operating system has its own **collector** that translates native system commands into a common internal format:

| OS | Native command |
|------|-------------------------------|
| Windows | PowerShell `Get-NetTCPConnection` |
| Linux | `ss -lntpHn` |
| macOS | `lsof -nP -iTCP -sTCP:LISTEN` |

All higher-level logic (diffs, persistence, reporting) is OS-independent.

---

## 🖥️ Running PortWatch

### Portable distributions (recommended)

Ready-to-run **portable distributions** are provided for each supported operating system.

These distributions:
- include a minimal Java runtime
- do **not** require Java to be installed
- store all generated data locally next to the executable

📦 See details here:  
➡️ **[`dist/`](./dist)**

---

### Running from source / JAR

For development or Java-enabled environments, PortWatch can also be executed directly from the JAR.

This mode is intended for development and testing only.

---

## 🎯 Purpose

This project was developed as part of cybersecurity technical training, with emphasis on:

- OS-level networking
- process inspection
- cross-platform architecture
- clean and maintainable Java design
- reproducible system monitoring

The project is intentionally CLI-based and backend-focused.
