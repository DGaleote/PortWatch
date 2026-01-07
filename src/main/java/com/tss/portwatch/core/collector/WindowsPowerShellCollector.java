package com.tss.portwatch.core.collector;

import com.tss.portwatch.core.exec.CommandRunner;

import java.util.List;

public class WindowsPowerShellCollector implements ListenerCollector {

    @Override
    public String collectTcpListenersJson() throws Exception {
        String ps = """
$ErrorActionPreference='Stop';

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
} | ConvertTo-Json -Depth 3

                """;

        var result = CommandRunner.run(List.of(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy", "Bypass",
                "-Command", ps
        ));

        if (result.exitCode() != 0) {
            throw new RuntimeException("PowerShell failed: " + result.exitCode() + "\n" + result.stderr());
        }
        return result.stdout();
    }
}
