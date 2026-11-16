package com.example.sos.objects.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FilenameSanitizerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "relatorio.pdf                | relatorio.pdf",
            "../../etc/passwd             | passwd",
            "C:\\Users\\x\\foto.png       | foto.png",
            "..                           | file",
            ".bashrc                      | bashrc",
            "   espaços.txt   | espaços.txt",
            "a\"b<c>d.txt                 | a_b_c_d.txt"
    })
    void sanitizesCommonCases(String raw, String expected) {
        assertEquals(expected.trim(), FilenameSanitizer.sanitize(raw));
    }

    @Test
    void nullOrBlankFallsBack() {
        assertEquals("file", FilenameSanitizer.sanitize(null));
        assertEquals("file", FilenameSanitizer.sanitize("   "));
        assertEquals("file", FilenameSanitizer.sanitize("////"));
    }

    @Test
    void controlCharactersAreRemoved() {
        String result = FilenameSanitizer.sanitize("a\r\nb\u0000c.txt");
        assertFalse(result.contains("\r"));
        assertFalse(result.contains("\n"));
        assertFalse(result.contains("\u0000"));
    }

    @Test
    void longNamesAreTruncatedKeepingTheExtension() {
        String result = FilenameSanitizer.sanitize("a".repeat(400) + ".pdf");
        assertEquals(255, result.length());
        assertTrue(result.endsWith(".pdf"));
    }
}
