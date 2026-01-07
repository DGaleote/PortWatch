package com.tss.portwatch.core.os;

public final class OsDetector {
    public enum Os { WINDOWS, LINUX, MAC, UNKNOWN }

    public static Os detect() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return Os.WINDOWS;
        if (os.contains("mac")) return Os.MAC;
        if (os.contains("nix") || os.contains("nux") || os.contains("aix")) return Os.LINUX;
        return Os.UNKNOWN;
    }

    private OsDetector() {}
}
