package com.tss.portwatch.core.exec;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/**
 * Utility class to execute external system commands in a safe and controlled way.
 *
 * Responsibilities:
 *  - Launch a process
 *  - Capture stdout and stderr concurrently
 *  - Enforce a timeout
 *  - Return all results in a single immutable Result record
 *
 * This avoids common pitfalls such as deadlocks caused by full stdout/stderr buffers
 * or hanging external commands.
 */
public final class CommandRunner {

    /**
     * Immutable result of a command execution.
     *
     * @param exitCode process exit code
     * @param stdout   full standard output of the command
     * @param stderr   full error output of the command
     */
    public record Result(int exitCode, String stdout, String stderr) {}

    /**
     * Executes a command with a default timeout.
     *
     * A "reasonable" default timeout is used to prevent the application from
     * hanging indefinitely if a system command misbehaves.
     */
    public static Result run(List<String> cmd) throws IOException, InterruptedException {
        return run(cmd, Duration.ofSeconds(15));
    }

    /**
     * Executes a command with a custom timeout.
     *
     * @param cmd     command and arguments
     * @param timeout maximum allowed execution time
     */
    public static Result run(List<String> cmd, Duration timeout) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);

        // Keep stdout and stderr separated so we can diagnose failures.
        pb.redirectErrorStream(false);

        Process p = pb.start();

        // We read stdout and stderr in parallel to avoid blocking the process
        // if one of the buffers fills up.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> outF = pool.submit(() -> readAll(p.getInputStream()));
            Future<String> errF = pool.submit(() -> readAll(p.getErrorStream()));

            // Wait for the process to finish, but only up to the specified timeout.
            boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                // Kill the process forcibly if it exceeds the timeout.
                p.destroyForcibly();
                throw new RuntimeException("Command timeout after " + timeout + ": " + String.join(" ", cmd));
            }

            int code = p.exitValue();
            String out = getFuture(outF);
            String err = getFuture(errF);

            return new Result(code, out, err);
        } finally {
            // Always stop the thread pool, even if something goes wrong.
            pool.shutdownNow();
        }
    }

    /**
     * Retrieves a Future<String> result with a small timeout.
     *
     * If reading output fails or blocks, we return an empty string instead of
     * crashing the whole application. This keeps PortWatch resilient to
     * partially broken commands.
     */
    private static String getFuture(Future<String> f) {
        try {
            return f.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return ""; // If output cannot be read, do not fail the entire run.
        }
    }

    /**
     * Reads all text from an InputStream using UTF-8 and returns it as a String.
     */
    private static String readAll(InputStream is) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    /**
     * Utility class: no instances allowed.
     */
    private CommandRunner() {}
}
