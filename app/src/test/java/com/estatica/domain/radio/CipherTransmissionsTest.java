package com.estatica.domain.radio;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.core.GameRandom;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.SignalsDef;

import org.junit.Test;

/** Every scheme-bound transmission in content encrypts, hides its text, and takes its answer. */
public class CipherTransmissionsTest {

    @Test
    public void everySchemeEncryptsAndAcceptsTheAnswer() {
        GameContent content = TestContent.get();
        RadioRules rules = new RadioRules(content);
        int checked = 0;
        for (SignalsDef.Transmission def : content.getSignals().getTransmissions()) {
            if (def.getKind() == null) {
                continue;
            }
            String plain = def.getText().replace("{zone}", "the dam");
            Transmission t = rules.encrypt(def, def.getBand() != null ? def.getBand() : "short", null, plain,
                    GameRandom.fromSeed(1), 0);
            assertNotEquals(def.getId(), Cipher.normalize(plain), t.cipherText());
            assertTrue(def.getId(), t.check(plain));
            checked++;
        }
        assertTrue(checked >= 15);
    }

    @Test
    public void oldSavedKindsStillLoad() {
        assertTrue(Transmission.Kind.fromName("CAESAR") == Transmission.Kind.CAESAR);
        assertTrue(Transmission.Kind.fromName("SOMETHING_NEW") == Transmission.Kind.CAESAR);
    }
}
