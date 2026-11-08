package com.msservices.app.service;

import com.msservices.app.exception.InvalidCookieException;
import java.util.Locale;

// Accepts only the Netscape cookies.txt export that yt-dlp understands. The content is a
// credential, so every message here stays generic and never echoes any part of it.
public final class CookieContentValidator {

    public static final int MAX_COOKIE_CONTENT_LENGTH = 64 * 1024;

    private CookieContentValidator() {
    }

    public static String normalizeAndValidate(String rawCookies) {
        if (rawCookies == null || rawCookies.isBlank()) {
            return null;
        }

        String normalized = rawCookies.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
            normalized = normalized.substring(1);
        }
        normalized = normalized.trim();

        if (normalized.length() > MAX_COOKIE_CONTENT_LENGTH) {
            throw new InvalidCookieException(
                    "The cookies file is too large. The maximum size is 64 KB.");
        }
        if (normalized.indexOf('\0') >= 0) {
            throw new InvalidCookieException("The cookies file contains invalid characters.");
        }

        String firstLine = normalized.lines().findFirst().orElse("");
        if (!firstLine.startsWith("# Netscape HTTP Cookie File")
                && !firstLine.startsWith("# HTTP Cookie File")) {
            throw new InvalidCookieException(
                    "The cookies must be a Netscape cookies.txt file. Export the file from your browser and send its content.");
        }

        String lowerCase = normalized.toLowerCase(Locale.ROOT);
        if (!lowerCase.contains("youtube.com") && !lowerCase.contains("google.com")) {
            throw new InvalidCookieException("The cookies must include YouTube cookies.");
        }

        return normalized + "\n";
    }
}
