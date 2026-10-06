package com.estatica.domain.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CipherTest {

    @Test
    public void normalizeStripsAccentsSymbolsAndExtraSpaces() {
        assertEquals("THE SILENCE BEGAN AT", Cipher.normalize("  The Silence  began, at 3:10 "));
        assertEquals("SCHOOL NO", Cipher.normalize("School No. 14"));
        assertEquals("OLAZABAL NANDU", Cipher.normalize("Olazábal Ñandú"));
    }

    @Test
    public void caesarRoundTripsForEveryShift() {
        String plain = "WE LEFT SUPPLIES AT THE HOSPITAL";
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
        assertTrue(Cipher.matches("  the silence began at three ten ", "THE SILENCE BEGAN AT THREE TEN"));
        assertFalse(Cipher.matches("THE SILENCE", "THE SILENCE BEGAN"));
    }

    @Test
    public void transmissionChecksTheDecodedText() {
        Transmission caesar = Transmission.create("t", Transmission.Kind.CAESAR, "Do not answer the voice", 7, "medium", null, 0);
        assertEquals("KV UVA HUZDLY AOL CVPJL", caesar.cipherText());
        assertTrue(caesar.check(Cipher.caesar(caesar.cipherText(), -7)));
        Transmission morse = Transmission.create("t", Transmission.Kind.MORSE, "SOS", 0, "short", null, 0);
        assertEquals("··· ——— ···", morse.cipherText());
        assertTrue(morse.check("sos"));
    }
}
