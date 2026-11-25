package com.sapidblue.ledger.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    public static final int MAX_BODY_BYTES = 1024 * 1024;

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            throw new PayloadTooLargeException("Request body exceeds maximum allowed size of " + MAX_BODY_BYTES + " bytes");
        }
        this.cachedBody = readBounded(request.getInputStream(), MAX_BODY_BYTES);
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int total = 0;
        int bytesRead;
        while ((bytesRead = input.read(chunk)) != -1) {
            total += bytesRead;
            if (total > maxBytes) {
                throw new PayloadTooLargeException("Request body exceeds maximum allowed size of " + maxBytes + " bytes");
            }
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    public String getBodyAsString() {
        return new String(this.cachedBody, StandardCharsets.UTF_8);
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(this.cachedBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() {
                return byteStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    public static class PayloadTooLargeException extends RuntimeException {
        public PayloadTooLargeException(String message) {
            super(message);
        }
    }
}
