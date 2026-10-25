package dev.fireball.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal Minecraft TCP host:
 * - Listens on a port (default 25565)
 * - Answers Server List Ping (status + ping/pong)
 * so the process is a real network host, not just a log message.
 */
public final class MinecraftHost implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MinecraftHost.class);

    private final String mcVersion;
    private final String motd;
    private final String bindAddress;
    private final int port;
    private final int maxPlayers;
    private final AtomicInteger onlinePlayers = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private ExecutorService acceptPool;
    private ExecutorService clientPool;

    public MinecraftHost(String mcVersion, int port, String motd, int maxPlayers) {
        this(mcVersion, "0.0.0.0", port, motd, maxPlayers);
    }

    public MinecraftHost(String mcVersion, String bindAddress, int port, String motd, int maxPlayers) {
        this.mcVersion = mcVersion;
        this.bindAddress = bindAddress == null || bindAddress.isBlank() ? "0.0.0.0" : bindAddress;
        this.port = port;
        this.motd = motd != null ? motd : "Fireball Server";
        this.maxPlayers = maxPlayers > 0 ? maxPlayers : 20;
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(bindAddress, port));

        acceptPool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "fireball-accept");
            t.setDaemon(true);
            return t;
        });
        clientPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fireball-client");
            t.setDaemon(true);
            return t;
        });

        acceptPool.submit(this::acceptLoop);

        LOGGER.info("Minecraft host listening on {}:{}", bindAddress, port);
        LOGGER.info("Version={}, motd=\"{}\", maxPlayers={}", mcVersion, motd, maxPlayers);
        System.out.println();
        System.out.println("Fireball is HOSTING on " + bindAddress + ":" + port);
        System.out.println("  Minecraft version : " + mcVersion);
        System.out.println("  MOTD              : " + motd);
        System.out.println("  Connect / ping this machine on port " + port);
        System.out.println("  Press Ctrl+C to stop.");
        System.out.println();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(10_000);
                clientPool.submit(() -> handleClient(socket));
            } catch (IOException e) {
                if (running.get()) {
                    LOGGER.warn("Accept failed: {}", e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        String remote = String.valueOf(socket.getRemoteSocketAddress());
        try (Socket s = socket;
             InputStream inRaw = s.getInputStream();
             OutputStream outRaw = s.getOutputStream()) {

            DataInputStream in = new DataInputStream(inRaw);
            DataOutputStream out = new DataOutputStream(outRaw);

            // Handshake
            int packetLen = readVarInt(in);
            if (packetLen <= 0 || packetLen > 32767) {
                return;
            }
            int packetId = readVarInt(in);
            if (packetId != 0x00) {
                LOGGER.debug("[{}] Unexpected first packet id {}", remote, packetId);
                return;
            }

            int protocolVersion = readVarInt(in);
            String hostname = readString(in);
            int port = in.readUnsignedShort();
            int nextState = readVarInt(in);

            LOGGER.info("[{}] handshake protocol={} host={} port={} nextState={}",
                    remote, protocolVersion, hostname, port, nextState);

            if (nextState == 1) {
                // Status
                handleStatus(in, out);
            } else if (nextState == 2) {
                // Login — disconnect with a clear message (full play not implemented)
                handleLoginDisconnect(out);
            } else {
                LOGGER.debug("[{}] Unsupported nextState {}", remote, nextState);
            }
        } catch (IOException e) {
            LOGGER.debug("[{}] connection closed: {}", remote, e.getMessage());
        }
    }

    private void handleStatus(DataInputStream in, DataOutputStream out) throws IOException {
        // Status Request (0x00) — empty payload
        int len = readVarInt(in);
        int id = readVarInt(in);
        if (id != 0x00) {
            return;
        }

        String json = statusJson();
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream bodyOut = new DataOutputStream(body);
        writeVarInt(bodyOut, 0x00); // status response
        writeVarInt(bodyOut, jsonBytes.length);
        bodyOut.write(jsonBytes);

        writePacket(out, body.toByteArray());
        out.flush();

        // Optional ping
        try {
            int pingLen = readVarInt(in);
            int pingId = readVarInt(in);
            if (pingId == 0x01) {
                long payload = in.readLong();
                ByteArrayOutputStream pongBody = new ByteArrayOutputStream();
                DataOutputStream pongOut = new DataOutputStream(pongBody);
                writeVarInt(pongOut, 0x01);
                pongOut.writeLong(payload);
                writePacket(out, pongBody.toByteArray());
                out.flush();
            }
        } catch (IOException ignored) {
            // client may disconnect after status
        }
    }

    private void handleLoginDisconnect(DataOutputStream out) throws IOException {
        // LoginDisconnect (0x00) with a chat component reason
        String reason = "{\"text\":\"Fireball Server: login/play is not implemented yet. Status ping works.\"}";
        byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream bodyOut = new DataOutputStream(body);
        writeVarInt(bodyOut, 0x00);
        writeVarInt(bodyOut, reasonBytes.length);
        bodyOut.write(reasonBytes);

        writePacket(out, body.toByteArray());
        out.flush();
    }

    private String statusJson() {
        // Minimal Server List Ping JSON
        String desc = motd.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{"
                + "\"version\":{\"name\":\"Fireball " + mcVersion + "\",\"protocol\":0},"
                + "\"players\":{\"max\":" + maxPlayers + ",\"online\":" + onlinePlayers.get() + ",\"sample\":[]},"
                + "\"description\":{\"text\":\"" + desc + "\"},"
                + "\"favicon\":null,"
                + "\"enforcesSecureChat\":false"
                + "}";
    }

    private static void writePacket(DataOutputStream out, byte[] body) throws IOException {
        writeVarInt(out, body.length);
        out.write(body);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = in.readByte();
            int value = (read & 0b0111_1111);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) {
                throw new IOException("VarInt too big");
            }
        } while ((read & 0b1000_0000) != 0);
        return result;
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        int v = value;
        while (true) {
            if ((v & ~0x7F) == 0) {
                out.writeByte(v);
                return;
            }
            out.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = readVarInt(in);
        if (len < 0 || len > 32767) {
            throw new IOException("Invalid string length: " + len);
        }
        byte[] data = new byte[len];
        in.readFully(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    public int getPort() {
        return port;
    }

    public int getOnlinePlayers() {
        return onlinePlayers.get();
    }

    public boolean isRunning() {
        return running.get();
    }

    public void await() throws InterruptedException {
        while (running.get()) {
            Thread.sleep(200);
        }
    }

    @Override
    public void close() {
        running.set(false);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        if (acceptPool != null) {
            acceptPool.shutdownNow();
        }
        if (clientPool != null) {
            clientPool.shutdownNow();
        }
        LOGGER.info("Minecraft host stopped (port {})", port);
    }
}
