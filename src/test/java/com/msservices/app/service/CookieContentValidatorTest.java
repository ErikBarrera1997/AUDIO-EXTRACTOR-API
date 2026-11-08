package com.msservices.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.msservices.app.exception.InvalidCookieException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CookieContentValidatorTest {

    private static final String VALID =
            "# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\tTRUE\t0\tSID\tsupersecret\n";

    @Test
    @DisplayName("Accepts a valid Netscape export and keeps a trailing newline")
    void acceptsValidNetscapeCookies() {
        String normalized = CookieContentValidator.normalizeAndValidate(VALID);

        assertNotNull(normalized);
        assertTrue(normalized.startsWith("# Netscape HTTP Cookie File\n"));
        assertTrue(normalized.endsWith("\n"));
        assertTrue(normalized.contains("supersecret"));
    }

    @Test
    @DisplayName("Normalizes CRLF and CR line endings to LF")
    void normalizesLineEndings() {
        String crlf = CookieContentValidator.normalizeAndValidate(VALID.replace("\n", "\r\n"));
        String cr = CookieContentValidator.normalizeAndValidate(VALID.replace("\n", "\r"));

        assertFalse(crlf.contains("\r"));
        assertFalse(cr.contains("\r"));
    }

    @Test
    @DisplayName("Accepts the Mozilla HTTP Cookie File header")
    void acceptsMozillaHeader() {
        String normalized = CookieContentValidator.normalizeAndValidate(
                "# HTTP Cookie File\n.youtube.com\tTRUE\t/\tTRUE\t0\tSID\tvalue\n");

        assertNotNull(normalized);
    }

    @Test
    @DisplayName("Strips a UTF-8 byte order mark")
    void stripsByteOrderMark() {
        String normalized = CookieContentValidator.normalizeAndValidate("\uFEFF" + VALID);

        assertEquals("# Netscape HTTP Cookie File", normalized.lines().findFirst().orElse(""));
    }

    @Test
    @DisplayName("Treats null and blank input as no cookies")
    void blankInputMeansNoCookies() {
        assertNull(CookieContentValidator.normalizeAndValidate(null));
        assertNull(CookieContentValidator.normalizeAndValidate("   "));
    }

    @Test
    @DisplayName("Rejects a raw Cookie header instead of a Netscape file")
    void rejectsRawCookieHeader() {
        InvalidCookieException exception = assertThrows(InvalidCookieException.class,
                () -> CookieContentValidator.normalizeAndValidate("SID=supersecret; HSID=other"));

        assertTrue(exception.getMessage().contains("Netscape"));
        assertFalse(exception.getMessage().contains("supersecret"));
    }

    @Test
    @DisplayName("Rejects a cookie file with no YouTube or Google cookies")
    void rejectsUnrelatedCookies() {
        assertThrows(InvalidCookieException.class,
                () -> CookieContentValidator.normalizeAndValidate(
                        "# Netscape HTTP Cookie File\n.example.com\tTRUE\t/\tTRUE\t0\tSID\tvalue\n"));
    }

    @Test
    @DisplayName("Rejects an oversized cookie payload without echoing it")
    void rejectsOversizedCookies() {
        String oversized = "# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\tTRUE\t0\tSID\t"
                + "x".repeat(CookieContentValidator.MAX_COOKIE_CONTENT_LENGTH) + "\n";

        InvalidCookieException exception = assertThrows(InvalidCookieException.class,
                () -> CookieContentValidator.normalizeAndValidate(oversized));

        assertFalse(exception.getMessage().contains("xxxx"));
    }

    @Test
    @DisplayName("Rejects a NUL byte")
    void rejectsNullByte() {
        assertThrows(InvalidCookieException.class,
                () -> CookieContentValidator.normalizeAndValidate(
                        "# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\tTRUE\t0\tSID\tva\0lue\n"));
    }
}
