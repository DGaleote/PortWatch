package com.tss.portwatch.core.model;

import java.util.Objects;

public final class SocketKey {
    public final String localAddress;
    public final int localPort;
    public final String protocol; // de momento "TCP"

    public SocketKey(String localAddress, int localPort, String protocol) {
        this.localAddress = localAddress;
        this.localPort = localPort;
        this.protocol = protocol;
    }

    public static SocketKey tcp(String localAddress, int localPort) {
        return new SocketKey(localAddress, localPort, "TCP");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SocketKey)) return false;
        SocketKey that = (SocketKey) o;
        return localPort == that.localPort &&
                Objects.equals(localAddress, that.localAddress) &&
                Objects.equals(protocol, that.protocol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(localAddress, localPort, protocol);
    }

    @Override
    public String toString() {
        return protocol + " " + localAddress + ":" + localPort;
    }
}
