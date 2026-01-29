package com.mineui.serverutils.rcon;

import com.mineui.serverutils.config.AppConfig;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

public final class RconClient implements Closeable {
    private static final int PACKET_TYPE_AUTH = 3;
    private static final int PACKET_TYPE_COMMAND = 2;

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    private RconClient(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public static RconClient connect(AppConfig config) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(config.rconHost(), config.rconPort()), config.rconTimeoutMs());
        socket.setSoTimeout(config.rconTimeoutMs());

        RconClient client = new RconClient(socket);
        client.authenticate(config.rconPassword());
        return client;
    }

    public RconResponse command(String command) throws IOException {
        int requestId = ThreadLocalRandom.current().nextInt();
        writePacket(requestId, PACKET_TYPE_COMMAND, command);
        RconPacket response = readPacket();
        if (response == null) {
            return RconResponse.error("No response from RCON server");
        }
        if (response.id != requestId) {
            return RconResponse.error("Mismatched RCON response id");
        }
        return RconResponse.ok(response.payload);
    }

    private void authenticate(String password) throws IOException {
        int requestId = ThreadLocalRandom.current().nextInt();
        writePacket(requestId, PACKET_TYPE_AUTH, password);
        RconPacket response = readPacket();
        if (response == null || response.id == -1) {
            throw new IOException("RCON authentication failed");
        }
    }

    private void writePacket(int id, int type, String payload) throws IOException {
        byte[] payloadBytes = payload == null ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8);
        int length = 4 + 4 + payloadBytes.length + 2;
        writeIntLE(length);
        writeIntLE(id);
        writeIntLE(type);
        out.write(payloadBytes);
        out.writeByte(0);
        out.writeByte(0);
        out.flush();
    }

    private RconPacket readPacket() throws IOException {
        int length;
        try {
            length = readIntLE();
        } catch (IOException ex) {
            return null;
        }
        int id = readIntLE();
        int type = readIntLE();
        int payloadLength = length - 4 - 4 - 2;
        if (payloadLength < 0) {
            throw new IOException("Invalid RCON response length");
        }
        byte[] payloadBytes = new byte[payloadLength];
        in.readFully(payloadBytes);
        in.readByte();
        in.readByte();
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        return new RconPacket(id, type, payload);
    }

    private void writeIntLE(int value) throws IOException {
        out.writeByte(value & 0xFF);
        out.writeByte((value >> 8) & 0xFF);
        out.writeByte((value >> 16) & 0xFF);
        out.writeByte((value >> 24) & 0xFF);
    }

    private int readIntLE() throws IOException {
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        int b3 = in.readUnsignedByte();
        int b4 = in.readUnsignedByte();
        return (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private static final class RconPacket {
        private final int id;
        private final int type;
        private final String payload;

        private RconPacket(int id, int type, String payload) {
            this.id = id;
            this.type = type;
            this.payload = payload;
        }
    }
}
