package com.estatica.domain.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** Every new scheme decodes what it encodes. */
public class CiphersTest {

    private static final String TEXT = "MEET AT THE DAM AT NIGHT";

    @Test
    public void atbashIsItsOwnInverse() {
        assertEquals("NVVG", Ciphers.atbash("MEET"));
        assertEquals(TEXT, Ciphers.atbash(Ciphers.atbash(TEXT)));
    }

    @Test
    public void tapCodeKnocksRowThenColumn() {
        // H is row 2, column 3.
        assertEquals("·· ···", Ciphers.tap("H"));
        assertEquals('H', Ciphers.tapLetter(2, 3));
        assertEquals('C', Ciphers.tapLetter(1, 3));
        assertTrue(Ciphers.tap("OK GO").contains(" / "));
    }

    @Test
    public void vigenereRoundTrip() {
        String c = Ciphers.vigenere(TEXT, "SILENCE", false);
        assertEquals(TEXT, Ciphers.vigenere(c, "silence", true));
        assertEquals("LXFOPV", Ciphers.vigenere("ATTACK", "LEMON", false).substring(0, 6));
    }

    @Test
    public void base64RoundTrip() {
        String c = Ciphers.base64(TEXT);
        assertEquals(TEXT.toLowerCase(), Ciphers.unbase64(c));
        assertNull(Ciphers.unbase64("not base64!!"));
    }

    @Test
    public void playfairThroughNatoRoundTrip() {
        String plain = "SUPPLIES AT THE DAM";
        String sent = Ciphers.toNato(Ciphers.playfair(Cipher.normalize(plain), "HARRIER", false));
        String back = Ciphers.playfair(Ciphers.fromNato(sent), "HARRIER", true);
        // Playfair loses spaces and puts an X between doubled letters (PP, and the TT of "AT THE").
        assertEquals("SUPXPLIESATXTHEDAM", back);
    }

    @Test
    public void oneTimePadRoundTrip() {
        String pad = "12930 48211 70533 91847 26519 30772 85106 44398 61025 79314";
        String c = Ciphers.numbers(TEXT, pad);
        assertEquals(TEXT, Ciphers.unnumbers(c, pad));
    }

    @Test
    public void bookCipherRoundTrip() {
        List<List<String>> pages = List.of(
                List.of("the night is long", "meet me at the dam"),
                List.of("bring the codebook"));
        String c = Ciphers.book("MEET AT THE DAM", pages);
        assertEquals("1-2-1 1-2-3 1-1-1 1-2-5", c);
        assertEquals("MEET AT THE DAM", Ciphers.unbook(c, pages));
        assertNull(Ciphers.book("UNKNOWN WORD", pages));
    }
}
