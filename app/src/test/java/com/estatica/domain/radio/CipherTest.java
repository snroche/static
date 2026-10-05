package com.estatica.domain.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CipherTest {

    @Test
    public void normalizeStripsAccentsSymbolsAndExtraSpaces() {
        assertEquals("EL SILENCIO EMPEZO A LAS", Cipher.normalize("  El Silencio  empezó, a las 3:10 "));
        assertEquals("ESCUELA N", Cipher.normalize("Escuela N.º 14"));
        assertEquals("NANDU", Cipher.normalize("Ñandú"));
    }

    @Test
    public void caesarRoundTripsForEveryShift() {
        String plain = "DEJAMOS PROVISIONES EN EL HOSPITAL";
        for (int shift = -30; shift <= 30; shift++) {
            assertEquals(plain, Cipher.caesar(Cipher.caesar(plain, shift), -shift));
        }
        assertEquals("BCD ZAB", Cipher.caesar("ABC YZA", 1));
    }

    @Test
    public void morseEncodesLettersAndWords() {
        assertEquals("··· ——— ···", Cipher.toMorse("SOS"));
        assertEquals("·— / —···", Cipher.toMorse("A B"));
        for (char c = 'A'; c <= 'Z'; c++) {
            assertEquals(Character.valueOf(c), Cipher.letter(Cipher.code(c)));
        }
        assertNull(Cipher.letter("······"));
    }

    @Test
    public void answersMatchIgnoringCaseAccentsAndSpacing() {
        assertTrue(Cipher.matches("  el silencio empezó a las tres y diez ", "EL SILENCIO EMPEZO A LAS TRES Y DIEZ"));
        assertFalse(Cipher.matches("EL SILENCIO", "EL SILENCIO EMPEZO"));
    }

    @Test
    public void transmissionChecksTheDecodedText() {
        Transmission caesar = Transmission.create("t", Transmission.Kind.CAESAR, "No respondan a la voz", 7, "medium", null, 0);
        assertEquals("UV YLZWVUKHU H SH CVG", caesar.cipherText());
        assertTrue(caesar.check(Cipher.caesar(caesar.cipherText(), -7)));
        Transmission morse = Transmission.create("t", Transmission.Kind.MORSE, "SOS", 0, "short", null, 0);
        assertEquals("··· ——— ···", morse.cipherText());
        assertTrue(morse.check("sos"));
    }
}
