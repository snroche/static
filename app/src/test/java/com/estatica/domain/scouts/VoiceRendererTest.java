package com.estatica.domain.scouts;

import static org.junit.Assert.assertEquals;

import com.estatica.TestContent;
import com.estatica.domain.content.VoiceDef;

import org.junit.Test;

import java.util.Map;

public class VoiceRendererTest {

    @Test
    public void lowercaseVoice() {
        VoiceDef voice = TestContent.get().voice("minusculas");
        assertEquals("encontré 3 latas. ¡Ñandú!".toLowerCase(),
                VoiceRenderer.render(voice, "Encontré {n} latas. ¡ÑANDÚ!", Map.of("n", "3")));
    }

    @Test
    public void slangReplacesWholeWordsOnly() {
        VoiceDef voice = TestContent.get().voice("jerga");
        assertEquals("Es re fácil, pero el quilombo es otro. Muyyy.",
                VoiceRenderer.render(voice, "Es muy fácil, pero el problema es otro. Muyyy.", Map.of()));
    }
}
