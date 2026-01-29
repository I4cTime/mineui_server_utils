package com.mineui.serverutils.rcon;

public record RconResponse(boolean ok, String message) {
    public static RconResponse ok(String message) {
        return new RconResponse(true, message == null ? "" : message);
    }

    public static RconResponse error(String message) {
        return new RconResponse(false, message == null ? "Unknown error" : message);
    }
}
