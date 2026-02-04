package com.tss.portwatch.core.model;

import java.util.Objects;

/**
 * Immutable key that uniquely identifies a listening network endpoint.
 * <p>
 * A {@code SocketKey} represents the *identity* of a listening socket
 * independently of the process that owns it.
 * <p>
 * It is intentionally limited to protocol + local address + local port,
 * which allows PortWatch to:
 * <ul>
 *   <li>detect newly opened sockets</li>
 *   <li>detect sockets that have been closed</li>
 *   <li>detect sockets that remain but changed their owning process</li>
 * </ul>
 * <p>
 * This class is primarily used during snapshot diff computation
 * as a stable map/set key.
 */
public final class SocketKey {

    /**
     * Local IP address the socket is bound to (e.g. 127.0.0.1, 0.0.0.0).
     */
    public final String localAddress;

    /**
     * Local TCP port number.
     */
    public final int localPort;

    /**
     * Transport protocol.
     * <p>
     * Currently only {@code "TCP"} is used, but this field exists to
     * allow future extension (e.g. UDP) without changing the key model.
     */
    public final String protocol;

    /**
     * Creates a new socket identity key.
     *
     * @param localAddress local IP address the socket is bound to
     * @param localPort    local port number
     * @param protocol     transport protocol (e.g. TCP)
     */
    public SocketKey(String localAddress, int localPort, String protocol) {
        this.localAddress = localAddress;
        this.localPort = localPort;
        this.protocol = protocol;
    }

    /**
     * Factory method for TCP sockets.
     * <p>
     * This keeps call sites readable and avoids repeating the {@code "TCP"}
     * literal throughout the codebase.
     *
     * @param localAddress local IP address
     * @param localPort    local port number
     * @return a {@code SocketKey} representing a TCP socket
     */
    public static SocketKey tcp(String localAddress, int localPort) {
        return new SocketKey(localAddress, localPort, "TCP");
    }

    /**
     * Two {@code SocketKey} instances are considered equal if they refer
     * to the same protocol, address and port combination.
     * <p>
     * Process-related information is intentionally excluded, as ownership
     * changes are represented separately in the diff model.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SocketKey)) return false;
        SocketKey that = (SocketKey) o;
        return localPort == that.localPort
                && Objects.equals(localAddress, that.localAddress)
                && Objects.equals(protocol, that.protocol);
    }

    /**
     * Hash code consistent with {@link #equals(Object)}, required for
     * correct behavior in hash-based collections.
     */
    @Override
    public int hashCode() {
        return Objects.hash(localAddress, localPort, protocol);
    }

    /**
     * Human-readable representation of the socket identity.
     * <p>
     * Mainly used for logging and debugging purposes.
     */
    @Override
    public String toString() {
        return protocol + " " + localAddress + ":" + localPort;
    }
}
