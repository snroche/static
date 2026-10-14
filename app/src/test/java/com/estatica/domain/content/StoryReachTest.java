package com.estatica.domain.content;

import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** Every fragment of the Voice's story can turn up somewhere, with any crew (no trait gates). */
public class StoryReachTest {

    private final GameContent content = TestContent.get();

    @Test
    public void everyFragmentHasASource() {
        Set<String> sources = new HashSet<>();
        for (SignalsDef.Transmission t : content.getSignals().getTransmissions()) {
            if (t.getPlot() != null) {
                sources.add(t.getPlot());
            }
        }
        for (EventDef e : content.getEvents().values()) {
            for (EventDef.Option o : e.getOptions()) {
                boolean open = o.getRequiresTrait().isEmpty() && o.getRequiresBackground() == null;
                for (EventDef.Outcome out : new EventDef.Outcome[]{o.getSuccess(), o.getFailure()}) {
                    if (open && out != null && out.getPlot() != null) {
                        sources.add(out.getPlot());
                    }
                }
            }
        }
        assertTrue("at least two chapters", content.getStory().getChapters().size() >= 2);
        for (StoryDef.Chapter c : content.getStory().getChapters()) {
            for (StoryDef.Fragment f : c.getFragments()) {
                assertTrue(c.getId() + ": nothing gives " + f.getId(), sources.contains(f.getId()));
            }
        }
    }
}
