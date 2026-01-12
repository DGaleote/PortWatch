package com.tss.portwatch.core.exec;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

public final class CommandRunner {

    public record Result(int exitCode, String stdout, String stderr) {}

    public static Result run(List<String> cmd) throws IOException, InterruptedException {
        // timeout por defecto “razonable” para evitar cuelgues
        return run(cmd, Duration.ofSeconds(15));
    }

    public static Result run(List<String> cmd, Duration timeout) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);

        Process p = pb.start();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> outF = pool.submit(() -> readAll(p.getInputStream()));
            Future<String> errF = pool.submit(() -> readAll(p.getErrorStream()));

            boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new RuntimeException("Command timeout after " + timeout + ": " + String.join(" ", cmd));
            }

            int code = p.exitValue();
            String out = getFuture(outF);
            String err = getFuture(errF);

            return new Result(code, out, err);
        } finally {
            pool.shutdownNow();
        }
    }

    private static String getFuture(Future<String> f) {
        try {
            return f.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return ""; // si algo falla al leer, preferimos no petar el programa entero por esto
        }
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
