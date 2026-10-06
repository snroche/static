package com.estatica.domain.scouts;

import com.estatica.core.GameRandom;
import com.estatica.domain.content.Balance;
import com.estatica.domain.content.GameContent;
import com.estatica.domain.content.PeopleDef;
import com.estatica.domain.content.ScoutTemplate;
import com.estatica.domain.content.TraitDef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a new survivor out of parts: a background, two visible traits and a hidden one,
 * a voice that fits, tastes and habits from those, and a name nobody in the bunker has had.
 * Nobody repeats the background, trait pair or voice of the people around them.
 * Deterministic for a given {@link GameRandom} state.
 */
public final class PersonaGenerator {

    /** Generated ids start with this, so they never collide with content ids. */
    public static final String ID_PREFIX = "p-";

    private static final int HABITS = 3;
    private static final int LIKES = 2;
    private static final int TRAIT_ATTEMPTS = 30;

    private final GameContent content;

    public PersonaGenerator(GameContent content) {
        this.content = content;
    }

    /**
     * @param id        unique id for the person (rescue key)
     * @param bandIndex where they were heard (0 = short band); farther means higher ceilings
     * @param crew      who they shouldn't resemble: the living crew and pending rescues
     * @param recent    the latest recruits, newest first (alive or not)
     * @param usedNames every name the bunker has known
     */
    public ScoutTemplate generate(String id, int bandIndex, Collection<ScoutTemplate> crew,
                                  List<ScoutTemplate> recent, Set<String> usedNames, GameRandom rng) {
        Balance.People balance = content.getBalance().getPeople();
        List<ScoutTemplate> around = new ArrayList<>(crew);
        around.addAll(recent.subList(0, Math.min(balance.getRecentToAvoid(), recent.size())));

        PeopleDef.Background background = pickBackground(around, rng);
        List<String> traits = pickTraits(around, rng);
        String hidden = pickHidden(traits, rng);
        String voice = pickVoice(background, traits, recent, rng);
        String name = pickName(usedNames, balance, rng);

        List<String> likes = pickSome(likePool(background, traits), LIKES, Set.of(), rng);
        List<String> dislikes = pickSome(dislikePool(background, traits), 1, new HashSet<>(likes), rng);
        List<ScoutTemplate.Habit> habits = new ArrayList<>();
        List<String> habitTexts = pickSome(habitPool(background, traits), HABITS, Set.of(), rng);
        for (int i = 0; i < habitTexts.size(); i++) {
            habits.add(new ScoutTemplate.Habit(habitTexts.get(i), i + 1));
        }
        List<String> rescueLines = background.getRescueLines().isEmpty()
                ? List.of() : rng.pick(background.getRescueLines());
        String bio = background.getBios().isEmpty() ? null : rng.pick(background.getBios());

        return ScoutTemplate.generated(id, name, voice, bio, background.getId(),
                stats(background, bandIndex, balance, rng), traits, hidden, likes, dislikes,
                rescueLines, habits);
    }

    private PeopleDef.Background pickBackground(List<ScoutTemplate> around, GameRandom rng) {
        Set<String> taken = new HashSet<>();
        for (ScoutTemplate t : around) {
            if (t.getBackground() != null) {
                taken.add(t.getBackground());
            }
        }
        List<PeopleDef.Background> fresh = new ArrayList<>();
        for (PeopleDef.Background b : content.getPeople().getBackgrounds()) {
            if (!taken.contains(b.getId())) {
                fresh.add(b);
            }
        }
        return rng.pick(fresh.isEmpty() ? content.getPeople().getBackgrounds() : fresh);
    }

    private List<TraitDef> personalityTraits() {
        List<TraitDef> out = new ArrayList<>();
        for (TraitDef t : content.getTraits().values()) {
            if (t.getKind() == TraitDef.Kind.PERSONALITY) {
                out.add(t);
            }
        }
        return out;
    }

    /** Two visible traits that don't clash, as a pair nobody around already has. */
    private List<String> pickTraits(List<ScoutTemplate> around, GameRandom rng) {
        Set<Set<String>> pairs = new HashSet<>();
        for (ScoutTemplate t : around) {
            pairs.add(new HashSet<>(t.getTraits()));
        }
        List<TraitDef> pool = personalityTraits();
        List<String> pair = null;
        for (int attempt = 0; attempt < TRAIT_ATTEMPTS; attempt++) {
            TraitDef a = rng.pick(pool);
            TraitDef b = rng.pick(pool);
            if (a == b || a.clashesWith(b)) {
                continue;
            }
            pair = List.of(a.getId(), b.getId());
            if (!pairs.contains(new HashSet<>(pair))) {
                return pair;
            }
        }
        if (pair != null) {
            return pair;
        }
        // Tiny pools only: the first two that get along.
        for (TraitDef a : pool) {
            for (TraitDef b : pool) {
                if (a != b && !a.clashesWith(b)) {
                    return List.of(a.getId(), b.getId());
                }
            }
        }
        throw new IllegalStateException("traits.json: no two personality traits get along");
    }

    private String pickHidden(List<String> visible, GameRandom rng) {
        List<String> options = new ArrayList<>();
        for (TraitDef t : personalityTraits()) {
            if (!t.isHideable() || visible.contains(t.getId())) {
                continue;
            }
            boolean clashes = false;
            for (String v : visible) {
                clashes |= t.clashesWith(content.trait(v));
            }
            if (!clashes) {
                options.add(t.getId());
            }
        }
        return options.isEmpty() ? null : rng.pick(options);
    }

    /** A voice that fits the background, leaning toward the traits, unlike the last recruit's. */
    private String pickVoice(PeopleDef.Background background, List<String> traits, List<ScoutTemplate> recent,
                             GameRandom rng) {
        List<String> fits = background.getVoices().isEmpty()
                ? new ArrayList<>(content.getVoices().keySet()) : new ArrayList<>(background.getVoices());
        if (fits.size() > 1 && !recent.isEmpty()) {
            fits.remove(recent.get(0).getVoice());
        }
        List<String> weighted = new ArrayList<>(fits);
        for (String traitId : traits) {
            for (String v : content.trait(traitId).getVoices()) {
                if (fits.contains(v)) {
                    weighted.add(v);
                    weighted.add(v);
                }
            }
        }
        return rng.pick(weighted);
    }

    private static String pickName(Set<String> used, Balance.People balance, PeopleDef people, GameRandom rng) {
        List<String> pool = rng.chance(balance.getNicknameChance()) && !people.getNicknames().isEmpty()
                ? people.getNicknames() : people.getNames();
        List<String> free = new ArrayList<>();
        for (String n : pool) {
            if (!used.contains(n)) {
                free.add(n);
            }
        }
        if (free.isEmpty()) {
            for (String n : people.getNames()) {
                if (!used.contains(n)) {
                    free.add(n);
                }
            }
        }
        if (!free.isEmpty()) {
            return rng.pick(free);
        }
        // Everyone's been through here already: tell them apart with an initial.
        String base = rng.pick(people.getNames());
        for (char c = 'A'; c <= 'Z'; c++) {
            String candidate = base + " " + c + ".";
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
        return base;
    }

    private String pickName(Set<String> used, Balance.People balance, GameRandom rng) {
        return pickName(used, balance, content.getPeople(), rng);
    }

    private Map<String, int[]> stats(PeopleDef.Background background, int bandIndex, Balance.People balance,
                                     GameRandom rng) {
        int max = content.getBalance().getScouts().getStatMax();
        Map<String, int[]> stats = new LinkedHashMap<>();
        for (StatType type : StatType.values()) {
            int base = rng.nextIntInclusive(balance.getStatBase()[0], balance.getStatBase()[1]);
            if (type.key().equals(background.getPrimaryStat())) {
                base += balance.getPrimaryBonus();
            } else if (type.key().equals(background.getSecondaryStat())) {
                base += balance.getSecondaryBonus();
            }
            int ceiling = base + rng.nextIntInclusive(balance.getCeilingGain()[0], balance.getCeilingGain()[1])
                    + bandIndex * balance.getBandCeilingBonus();
            ceiling = Math.min(max, ceiling);
            base = Math.max(1, Math.min(base, ceiling - 1));
            stats.put(type.key(), new int[]{base, ceiling});
        }
        return stats;
    }

    private List<String> likePool(PeopleDef.Background background, List<String> traits) {
        List<String> pool = new ArrayList<>(background.getLikes());
        for (String t : traits) {
            pool.addAll(content.trait(t).getLikes());
        }
        return pool;
    }

    private List<String> dislikePool(PeopleDef.Background background, List<String> traits) {
        List<String> pool = new ArrayList<>(background.getDislikes());
        for (String t : traits) {
            pool.addAll(content.trait(t).getDislikes());
        }
        return pool;
    }

    private List<String> habitPool(PeopleDef.Background background, List<String> traits) {
        List<String> pool = new ArrayList<>();
        for (ScoutTemplate.Habit h : background.getHabits()) {
            pool.add(h.getText());
        }
        for (String t : traits) {
            for (ScoutTemplate.Habit h : content.trait(t).getHabits()) {
                pool.add(h.getText());
            }
        }
        return pool;
    }

    /** Up to {@code count} distinct entries, skipping {@code exclude}. */
    private static List<String> pickSome(List<String> pool, int count, Set<String> exclude, GameRandom rng) {
        List<String> left = new ArrayList<>();
        for (String s : pool) {
            if (!exclude.contains(s) && !left.contains(s)) {
                left.add(s);
            }
        }
        List<String> out = new ArrayList<>();
        while (out.size() < count && !left.isEmpty()) {
            out.add(left.remove(rng.nextInt(left.size())));
        }
        return out;
    }
}
