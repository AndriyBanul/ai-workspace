package com.aiworkspace.audio.client;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class PiperSynthesisDeadline implements AutoCloseable {

    private final long startedNanos = System.nanoTime();
    private final long timeoutNanos;
    private final ScheduledFuture<?> timeoutTask;

    PiperSynthesisDeadline(Socket socket, Duration timeout, ScheduledExecutorService scheduler) {
        timeoutNanos = timeout.toNanos();
        timeoutTask = scheduler.schedule(() -> {
            try {
                socket.close();
            } catch (IOException exception) {
                log.warn("Failed to close Piper socket after synthesis deadline", exception);
            }
        }, timeoutNanos, TimeUnit.NANOSECONDS);
    }

    boolean hasExpired() {
        return System.nanoTime() - startedNanos >= timeoutNanos;
    }

    void check() throws IOException {
        if (hasExpired()) {
            throw new IOException("Piper synthesis exceeded the total request deadline");
        }
    }

    @Override
    public void close() {
        timeoutTask.cancel(false);
    }
}
