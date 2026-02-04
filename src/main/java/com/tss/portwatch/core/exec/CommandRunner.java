package com.tss.portwatch.core.exec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Executes external system commands in a controlled way.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Launch a process from a command + arguments</li>
 *   <li>Capture stdout and stderr concurrently (prevents buffer deadlocks)</li>
 *   <li>Enforce a timeout (prevents hanging commands)</li>
 *   <li>Return exit code + output in a single immutable result</li>
 * </ul>
 * <p>
 * This class does not interpret the command output; parsing is done elsewhere.
 */
public final class CommandRunner {

    /**
     * Immutable result of a command execution.
     *
     * @param exitCode process exit code
     * @param stdout   full standard output of the command (UTF-8)
     * @param stderr   full error output of the command (UTF-8)
     */
    public record Result(int exitCode, String stdout, String stderr) {
    }

    /**
     * Executes a command with a default timeout (15 seconds).
     *
     * @param cmd command and arguments
     * @return execution result (exit code + stdout + stderr)
     * @throws IOException          if the process cannot be started or I/O fails
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public static Result run(List<String> cmd) throws IOException, InterruptedException {
        return run(cmd, Duration.ofSeconds(15));
    }

    /**
     * Executes a command with a custom timeout.
     * <p>
     * Stdout and stderr are read in parallel to avoid deadlocks if the process
     * writes enough output to fill OS buffers.
     *
     * @param cmd     command and arguments
     * @param timeout maximum allowed execution time
     * @return execution result (exit code + stdout + stderr)
     * @throws IOException          if the process cannot be started or I/O fails
     * @throws InterruptedException if the calling thread is interrupted while waiting
     * @throws RuntimeException     if the process exceeds the given timeout
     */
    public static Result run(List<String> cmd, Duration timeout) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);

        // Keep stdout and stderr separated so failures can be diagnosed.
        pb.redirectErrorStream(false);

        Process p = pb.start();

        // Read stdout and stderr concurrently.
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

    /**
     * Gets the result of a Future<String> with a small timeout.
     * <p>
     * If reading output fails or blocks, an empty string is returned. Command execution
     * should not fail just because output could not be fully captured.
     */
    private static String getFuture(Future<String> f) {
        try {
            return f.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Reads all text from an InputStream using UTF-8 and returns it.
     *
     * @param is stream to read
     * @return full contents as a String (lines separated by '\n')
     * @throws IOException if reading fails
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
    private CommandRunner() {
    }
}
