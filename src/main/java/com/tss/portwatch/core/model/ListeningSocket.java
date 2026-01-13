package com.tss.portwatch.core.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Domain model representing a single TCP listening socket.
 *
 * This class is used as:
 *  - The in-memory representation of an open listening port
 *  - The JSON structure written to snapshot files
 *  - The unit of comparison for diff generation
 *
 * All fields are public to keep JSON serialization simple and explicit.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ListeningSocket {

    /** Local IP address the socket is bound to (e.g. 127.0.0.1, 0.0.0.0, ::1). */
    public String LocalAddress;

    /** Local TCP port (e.g. 80, 443, 8080). */
    public Integer LocalPort;

    /** Process ID that owns this socket (may be null on some platforms). */
    public Integer ProcessId;

    /** Name of the owning process (e.g. "java", "nginx", "cupsd"). */
    public String ProcessName;

    /** Full executable path, when available (mostly on Windows). */
    public String Path;

    /**
     * Default constructor required by Jackson for JSON deserialization.
     */
    public ListeningSocket() {
    }

    /**
     * Two ListeningSocket objects are considered equal if they represent
     * the same listening endpoint and the same owning process.
     *
     * This definition is critical for snapshot diffing:
     * it determines whether a socket is considered "the same",
     * "new", or "removed".
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ListeningSocket that = (ListeningSocket) o;
        return Objects.equals(LocalPort, that.LocalPort)
                && Objects.equals(ProcessId, that.ProcessId)
                && Objects.equals(LocalAddress, that.LocalAddress)
                && Objects.equals(ProcessName, that.ProcessName)
                && Objects.equals(Path, that.Path);
    }

    /**
     * Hash code consistent with equals(), so this object can be safely
     * used in hash-based collections (sets, maps) during diff computation.
     */
    @Override
    public int hashCode() {
        return Objects.hash(LocalAddress, LocalPort, ProcessId, ProcessName, Path);
    }
}
