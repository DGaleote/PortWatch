package com.tss.portwatch.core.exec;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CommandRunner {

    public record Result(int exitCode, String stdout, String stderr) {}

    public static Result run(List<String> cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);

        Process p = pb.start();
        String out = readAll(p.getInputStream());
        String err = readAll(p.getErrorStream());
        int code = p.waitFor();

        return new Result(code, out, err);
    }

    private static String readAll(InputStream is) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    private CommandRunner() {}
}
