package com.tss.portwatch.core.collector;

import com.tss.portwatch.core.exec.CommandRunner;

import java.util.List;

/**
 * Windows implementation of {@link ListenerCollector}.
 * <p>
 * Uses PowerShell to query TCP listening sockets via {@code Get-NetTCPConnection}
 * and enriches each entry with process metadata (name and executable path) via CIM/WMI
 * ({@code Win32_Process}).
 * <p>
 * The PowerShell script returns JSON that matches the {@code ListeningSocket} field names
 * (LocalAddress, LocalPort, ProcessId, ProcessName, Path), so the application can deserialize
 * it directly without additional mapping.
 */
public class WindowsPowerShellCollector implements ListenerCollector {

    /**
     * Collects TCP listeners on Windows and returns them as a JSON array.
     * <p>
     * The implementation:
     * <ul>
     *   <li>Lists TCP endpoints in LISTEN state</li>
     *   <li>Resolves the owning process using {@code Get-CimInstance Win32_Process}</li>
     *   <li>Projects each item to a {@code PSCustomObject} with the expected field names</li>
     *   <li>Serializes the final array using {@code ConvertTo-Json}</li>
     * </ul>
     *
     * @return JSON array where each element matches the ListeningSocket structure
     * @throws Exception if the PowerShell command fails or returns a non-zero exit code
     */
    @Override
    public String collectTcpListenersJson() throws Exception {
        /*
         * Self-contained PowerShell script:
         * - $ErrorActionPreference='Stop' makes PowerShell fail fast on unexpected errors.
         * - For each TCP listener, resolve process info from Win32_Process.
         * - ConvertTo-Json -Depth 3 is defensive to avoid truncation issues.
         *
         * Notes:
         * - Some listeners may not resolve process metadata (e.g., access restrictions).
         *   In that case, ProcessName/Path may be null in the output.
         */
        String ps = """
                $ErrorActionPreference='Stop';
                
                @(
                  Get-NetTCPConnection -State Listen |
                  ForEach-Object {
                    $procId = $_.OwningProcess
                    $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$procId" -ErrorAction SilentlyContinue
                
                    [PSCustomObject]@{
                      LocalAddress   = $_.LocalAddress
                      LocalPort      = $_.LocalPort
                      ProcessId      = $procId
                      ProcessName    = $proc.Name
                      Path           = $proc.ExecutablePath
                    }
                  }
                ) | ConvertTo-Json -Depth 3
                """;

        /*
         * Run PowerShell in deterministic mode:
         * -NoProfile: ignore user profile scripts
         * -ExecutionPolicy Bypass: allow execution of the inline script
         */
        var result = CommandRunner.run(List.of(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy", "Bypass",
                "-Command", ps
        ));

        // Non-zero exit code indicates the query failed.
        if (result.exitCode() != 0) {
            throw new RuntimeException("PowerShell failed: " + result.exitCode() + "\n" + result.stderr());
        }

        // PowerShell already returns JSON; forward stdout as-is.
        return result.stdout();
    }
}
