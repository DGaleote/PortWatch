package com.tss.portwatch.core.collector;

/**
 * Strategy interface for collecting TCP listening sockets.
 *
 * Each operating system (Windows, Linux, macOS) provides its own implementation
 * of this interface using the native tools available on that platform
 * (PowerShell, ss, lsof, etc).
 *
 * Implementations are responsible for:
 *  - Executing the appropriate OS-specific command
 *  - Parsing its output
 *  - Returning a JSON representation of the listening sockets
 *
 * The JSON format must be compatible with List<ListeningSocket> so it can be
 * deserialized by the core application using Jackson.
 */
public interface ListenerCollector {

    /**
     * Collects the current set of TCP listening sockets and returns them
     * as a JSON array.
     *
     * The returned JSON must represent a List<ListeningSocket>.
     *
     * @return JSON array of listening sockets
     * @throws Exception if the underlying command fails or output cannot be parsed
     */
    String collectTcpListenersJson() throws Exception;
}
