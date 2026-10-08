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
        assertEquals("found 3 cans. at olazábal's!",
                VoiceRenderer.render(voice, "Found {n} cans. At OLAZÁBAL'S!", Map.of("n", "3")));
    }

    @Test
    public void slangReplacesWholeWordsOnly() {
        VoiceDef voice = TestContent.get().voice("jerga");
        assertEquals("It's real easy, but the mess is elsewhere. Veryyy.",
                VoiceRenderer.render(voice, "It's very easy, but the problem is elsewhere. Veryyy.", Map.of()));
    }

    @Test
    public void theOperatorsNameFillsInWhereverItFits() {
        VoiceDef voice = TestContent.get().voice("formal");
        try {
            VoiceRenderer.setOperatorName("Sam");
            assertEquals("Copy that, Sam.", VoiceRenderer.render(voice, "Copy that, {operator}.", Map.of()));
            assertEquals("Copy that, Lee.", VoiceRenderer.render(voice, "Copy that, {operator}.", Map.of("operator", "Lee")));
            VoiceRenderer.setOperatorName("  ");
            assertEquals("Copy that, Operator.", VoiceRenderer.render(voice, "Copy that, {operator}.", Map.of()));
        } finally {
            VoiceRenderer.setOperatorName(null);
        }
    }
}
