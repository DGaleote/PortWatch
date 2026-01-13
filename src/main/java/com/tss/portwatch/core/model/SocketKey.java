package com.tss.portwatch.core.model;

import java.util.Objects;

/**
 * Immutable key that uniquely identifies a network listening endpoint.
 *
 * A SocketKey represents the "identity" of a listening socket independently
 * of which process owns it. It is used during snapshot diffing to detect:
 *  - new sockets
 *  - removed sockets
 *  - sockets that remain but changed owner (process)
 */
public final class SocketKey {

    /** Local IP address the socket is bound to. */
    public final String localAddress;

    /** Local TCP port. */
    public final int localPort;

    /** Protocol (currently only "TCP", but designed for future extension). */
    public final String protocol; // currently "TCP"

    public SocketKey(String localAddress, int localPort, String protocol) {
        this.localAddress = localAddress;
        this.localPort = localPort;
        this.protocol = protocol;
    }

    /**
     * Factory method for TCP sockets.
     * Keeps call sites readable and avoids repeating the "TCP" literal.
     */
    public static SocketKey tcp(String localAddress, int localPort) {
        return new SocketKey(localAddress, localPort, "TCP");
    }

    /**
     * Two SocketKey objects are equal if they refer to the same
     * protocol + address + port combination.
     *
     * This allows them to be used as map/set keys during diff computation.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SocketKey)) return false;
        SocketKey that = (SocketKey) o;
        return localPort == that.localPort &&
                Objects.equals(localAddress, that.localAddress) &&
                Objects.equals(protocol, that.protocol);
    }

    /**
     * Hash code consistent with equals(), required for correct behavior
     * in hash-based collections.
     */
    @Override
    public int hashCode() {
        return Objects.hash(localAddress, localPort, protocol);
    }

    /**
     * Human-readable representation, used mainly for logging and reporting.
     */
    @Override
    public String toString() {
        return protocol + " " + localAddress + ":" + localPort;
    }
}
