package com.aiworkspace.audio.client;

import com.aiworkspace.audio.models.WyomingEventHeader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class PiperTestServer implements AutoCloseable {

    private final ServerSocket server;
    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicReference<Socket> activeSocket = new AtomicReference<>();
    private final Future<?> completion;

    PiperTestServer(int connections, SocketHandler handler) throws IOException {
        server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        server.setSoTimeout(5_000);
        completion = worker.submit(() -> {
            for (int index = 0; index < connections; index++) {
                try (Socket socket = server.accept()) {
                    activeSocket.set(socket);
                    socket.setSoTimeout(5_000);
                    readRequest(socket);
                    handler.handle(socket, index);
                } finally {
                    activeSocket.set(null);
                }
            }
            return null;
        });
    }

    String host() {
        return server.getInetAddress().getHostAddress();
    }

    int port() {
        return server.getLocalPort();
    }

    void awaitCompletion() throws Exception {
        completion.get(5, TimeUnit.SECONDS);
    }

    private void readRequest(Socket socket) throws IOException {
        var input = socket.getInputStream();
        var line = new ByteArrayOutputStream();
        int next;
        while ((next = input.read()) != '\n') {
            if (next < 0) {
                throw new IOException("Client did not send a complete request header");
            }
            line.write(next);
        }
        var header = new ObjectMapper().readValue(line.toByteArray(), WyomingEventHeader.class);
        if (input.readNBytes(header.dataLength()).length != header.dataLength()) {
            throw new IOException("Client did not send complete request data");
        }
    }

    @Override
    public void close() throws IOException {
        try {
            server.close();
            Socket socket = activeSocket.get();
            if (socket != null) {
                socket.close();
            }
        } finally {
            worker.shutdownNow();
        }
    }

    @FunctionalInterface
    interface SocketHandler {
        void handle(Socket socket, int index) throws Exception;
    }
}
