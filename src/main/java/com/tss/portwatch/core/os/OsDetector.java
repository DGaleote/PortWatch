package com.tss.portwatch.core.os;

/**
 * Utility class responsible for detecting the current operating system.
 *
 * It translates JVM-provided system properties into a small, controlled
 * domain enum (WINDOWS, LINUX, MAC, UNKNOWN) that the rest of the application
 * can rely on without depending directly on platform-specific strings.
 */
public final class OsDetector {

    /**
     * Supported operating system types from the point of view of PortWatch.
     */
    public enum Os {
        WINDOWS,
        LINUX,
        MAC,
        UNKNOWN
    }

    /**
     * Detects the operating system where the JVM is currently running.
     *
     * This method inspects the standard "os.name" JVM system property and
     * normalizes it into one of the Os enum values.
     *
     * @return the detected operating system, or UNKNOWN if it cannot be mapped
     */
    public static Os detect() {
        // JVM-provided operating system name (e.g. "Windows 10", "Mac OS X", "Linux")
        String os = System.getProperty("os.name", "").toLowerCase();

        // Windows detection
        if (os.contains("win")) return Os.WINDOWS;

        // macOS detection
        if (os.contains("mac")) return Os.MAC;

        // Linux and Unix-like systems
        if (os.contains("nix") || os.contains("nux") || os.contains("aix")) return Os.LINUX;

        // Fallback for unsupported or unknown platforms
        return Os.UNKNOWN;
    }

    /**
     * Private constructor to prevent instantiation.
     * This class is intended to be used only in a static way.
     */
    private OsDetector() {}
}
