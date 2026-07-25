package com.mineui.serverutils.http;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Requires a bearer token on every request. The token is compared in constant
 * time to avoid leaking it via timing. When no token is configured the filter
 * is not installed at all (see {@link ApiHandlers}); the server only binds a
 * non-loopback interface when a token is present, so an unauthenticated route
 * is never reachable off-host.
 */
final class AuthFilter extends Filter {
    private final byte[] expected;

    AuthFilter(String token) {
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (!isAuthorized(header)) {
            byte[] body = "{\"ok\":false,\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            return;
        }
        chain.doFilter(exchange);
    }

    private boolean isAuthorized(String header) {
        if (header == null) {
            return false;
        }
        String prefix = "Bearer ";
        if (!header.startsWith(prefix)) {
            return false;
        }
        byte[] provided = header.substring(prefix.length()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }

    @Override
    public String description() {
        return "MineUI bearer-token authentication";
    }
}
