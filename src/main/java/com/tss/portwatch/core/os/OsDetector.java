package com.tss.portwatch.core.os;

/**
 * Utility class responsible for detecting the operating system
 * on which the JVM is currently running.
 * <p>
 * This class translates JVM-provided system properties into a small,
 * controlled domain enum ({@link Os}) that the rest of the application
 * can rely on without depending directly on platform-specific strings.
 * <p>
 * Centralizing OS detection here avoids scattering string-based checks
 * across the codebase and makes platform-specific behavior explicit.
 */
public final class OsDetector {

    /**
     * Supported operating system types from the point of view of PortWatch.
     * <p>
     * Only operating systems explicitly handled by the application
     * are represented here. All others fall back to {@code UNKNOWN}.
     */
    public enum Os {
        WINDOWS,
        LINUX,
        MAC,
        UNKNOWN
    }

    /**
     * Detects the operating system where the JVM is currently running.
     * <p>
     * This method inspects the standard {@code os.name} JVM system property
     * and normalizes it into one of the supported {@link Os} enum values.
     * <p>
     * The detection is intentionally conservative: if the operating system
     * cannot be confidently mapped, {@link Os#UNKNOWN} is returned so that
     * the caller can decide how to handle unsupported platforms.
     *
     * @return the detected operating system, or {@link Os#UNKNOWN} if it cannot be mapped
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
     * This class is intended to be used in a static-only manner.
     */
    private OsDetector() {
    }
}
