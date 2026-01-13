package com.tss.portwatch.core.collector;

import com.tss.portwatch.core.exec.CommandRunner;

import java.util.List;

/**
 * Windows implementation of ListenerCollector.
 *
 * Uses PowerShell to query the system TCP listeners via Get-NetTCPConnection
 * and enrich each entry with process metadata (name + executable path) via CIM/WMI.
 *
 * Output is produced directly as JSON to match the ListeningSocket structure.
 */
public class WindowsPowerShellCollector implements ListenerCollector {

    @Override
    public String collectTcpListenersJson() throws Exception {
        // We build a self-contained PowerShell script that:
        //  1) lists TCP listeners (LISTEN state)
        //  2) maps each listener to a PSCustomObject matching ListeningSocket fields
        //  3) converts the result to JSON
        //
        // Notes:
        //  - $ErrorActionPreference='Stop' forces PowerShell to fail fast on errors
        //  - Get-CimInstance is used to obtain process name/path (more reliable than parsing tasklist)
        //  - ConvertTo-Json -Depth 3 avoids truncation for nested objects (defensive)
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

        // Run PowerShell in a "clean" mode:
        //  -NoProfile avoids user profile scripts (faster + deterministic)
        //  -ExecutionPolicy Bypass ensures the inline script can run without policy blocks
        var result = CommandRunner.run(List.of(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy", "Bypass",
                "-Command", ps
        ));

        // Non-zero exit code means PowerShell failed to execute the query.
        if (result.exitCode() != 0) {
            throw new RuntimeException("PowerShell failed: " + result.exitCode() + "\n" + result.stderr());
        }

        // PowerShell already returns JSON, so we forward stdout as-is.
        return result.stdout();
    }
}
