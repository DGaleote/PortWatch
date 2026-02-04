package com.tss.portwatch.core.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Domain model representing a single TCP listening socket.
 * <p>
 * This class is the canonical representation of an open listening endpoint
 * inside PortWatch. It is used consistently as:
 * <ul>
 *   <li>In-memory representation of collected sockets</li>
 *   <li>JSON payload for snapshot persistence</li>
 *   <li>Input unit for snapshot diff computation</li>
 * </ul>
 * <p>
 * Fields are intentionally public to keep JSON serialization and
 * deserialization explicit and predictable across platforms.
 * <p>
 * This class is not immutable by design, as it acts primarily as a
 * data-transfer object populated by OS-specific collectors.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ListeningSocket {

    /**
     * Local IP address the socket is bound to (e.g. 127.0.0.1, 0.0.0.0, ::1).
     */
    public String LocalAddress;

    /**
     * Local TCP port number (e.g. 80, 443, 8080).
     */
    public Integer LocalPort;

    /**
     * Process ID owning this socket.
     * May be null on platforms where the PID cannot be resolved.
     */
    public Integer ProcessId;

    /**
     * Name of the owning process (e.g. "java", "nginx", "cupsd").
     */
    public String ProcessName;

    /**
     * Full executable path of the owning process, when available.
     * This field is typically populated on Windows systems.
     */
    public String Path;

    /**
     * Default constructor required by Jackson for JSON deserialization.
     */
    public ListeningSocket() {
    }

    /**
     * Equality definition for ListeningSocket.
     * <p>
     * Two instances are considered equal if they represent the same
     * listening endpoint owned by the same process.
     * <p>
     * This definition is intentionally strict and includes process-related
     * attributes, as it is used only for snapshot-level comparisons,
     * not for socket identity matching (which is handled separately
     * by SocketKey).
     * <p>
     * The distinction between "socket identity" and "socket state"
     * is essential for correctly detecting changed processes.
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
     * Hash code consistent with equals(), allowing this object to be safely
     * used in hash-based collections during diff computation.
     */
    @Override
    public int hashCode() {
        return Objects.hash(LocalAddress, LocalPort, ProcessId, ProcessName, Path);
    }
}
