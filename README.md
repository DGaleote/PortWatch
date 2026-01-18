# 🛡️ PortWatch

Cross-platform TCP listening port monitor (Windows, Linux, macOS)

PortWatch is a command-line security tool written in Java that detects which processes are listening on TCP ports and tracks changes over time using snapshots and diffs.

It is designed as a small, clean, and extensible monitoring engine suitable for security labs, system auditing and technical practices.

---

## 🚀 What it does

On each run PortWatch:

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
- configuration changes

---

## 🧱 Architecture

PortWatch is built around a clean separation of concerns:

```
core/
 ├─ collectors/   → OS-specific scanners
 ├─ diff/         → snapshot comparison engine
 ├─ model/        → domain objects (ListeningSocket, SocketKey)
 ├─ io/           → snapshot & diff persistence
 └─ PortWatchApp  → application orchestration
```

Each operating system has its own **collector** that converts native system commands into a common JSON format:

| OS | Command used |
|------|-------------------------------|
| Windows | PowerShell `Get-NetTCPConnection` |
| Linux | `ss -lntpHn` |
| macOS | `lsof -nP -iTCP -sTCP:LISTEN` |

The rest of the application is OS-independent.

---

## 🖥️ Running PortWatch

### Requirements
Java 17 or higher

### Run

```bash
java -jar PortWatch.jar
```

On the first run:
- a baseline snapshot is created

On subsequent runs:
- a new snapshot is created
- a diff is generated and printed

---

## 📂 Data storage

By default, data is stored relative to where the JAR is executed:

```
data/
 ├─ snapshots/
 └─ diffs/
```

You can override the location with:

```bash
PORTWATCH_DATA_DIR=/path/to/data java -jar PortWatch.jar
```

---

## 🧪 Example output

```
=== ADDED ===
[+] NEW     0.0.0.0:8080 -> java (PID 12345)

=== REMOVED ===
[-] CLOSED  127.0.0.1:631 -> cupsd (PID 312)

=== CHANGED ===
[*] CHANGED 0.0.0.0:3000 node (PID 1001) => node (PID 2043)
```

---

## 🎯 Purpose

This project was developed as part of cybersecurity technical training, focusing on:

- OS-level networking
- process inspection
- cross-platform architecture
- clean Java design
- reproducible system monitoring

It is intentionally CLI-based and backend-focused.
