package com.estatica.data.session;

import com.estatica.core.TextTemplate;
import com.estatica.domain.expedition.Mission;
import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The terminal's typed commands beyond help/status: look at the crew, the map, the stores, the
 * radio, and act (send people out, decode, rename). Reads the latest snapshot and calls the
 * session for actions. All wording comes from {@code terminal.json → console}.
 */
public final class ConsoleCommands {

    /** Command → aliases. Order is the order of suggestions. */
    static final Map<String, List<String>> COMMANDS = new LinkedHashMap<>();

    static {
        COMMANDS.put("help", List.of("?", "commands"));
        COMMANDS.put("status", List.of());
        COMMANDS.put("crew", List.of("scouts", "people"));
        COMMANDS.put("scout", List.of("who"));
        COMMANDS.put("send", List.of("go", "dispatch"));
        COMMANDS.put("recall", List.of("back"));
        COMMANDS.put("places", List.of("zones", "where"));
        COMMANDS.put("map", List.of("sectors"));
        COMMANDS.put("inventory", List.of("inv", "stores", "items"));
        COMMANDS.put("power", List.of("watts"));
        COMMANDS.put("food", List.of("rations"));
        COMMANDS.put("weather", List.of("sky"));
        COMMANDS.put("radio", List.of("transmissions", "messages"));
        COMMANDS.put("decode", List.of("crack"));
        COMMANDS.put("goals", List.of("objectives"));
        COMMANDS.put("time", List.of("clock"));
        COMMANDS.put("name", List.of("callme"));
        COMMANDS.put("clear", List.of("cls"));
        COMMANDS.put("sound", List.of());
        COMMANDS.put("notifications", List.of());
    }

    private final GameSession session;

    public ConsoleCommands(GameSession session) {
        this.session = session;
    }

    /** The canonical command for what was typed first, or null. */
    public static String command(String word) {
        String w = normalize(word);
        for (Map.Entry<String, List<String>> e : COMMANDS.entrySet()) {
            if (e.getKey().equals(w) || e.getValue().contains(w)) {
                return e.getKey();
            }
        }
        return null;
    }

    static String normalize(String input) {
        String decomposed = Normalizer.normalize(input.trim(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private Map<String, String> console() {
        return session.content().getTerminal().getConsole();
    }

    private String t(String key, Map<String, String> vars) {
        String template = console().get(key);
        return template != null ? TextTemplate.format(template, vars) : key;
    }

    private String t(String key) {
        return t(key, Map.of());
    }

    private static Map<String, String> vars(String... pairs) {
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out.put(pairs[i], pairs[i + 1]);
        }
        return out;
    }

    private static LogLine line(long now, String text) {
        return LogLine.draft(now, LogChannel.SYSTEM, null, text);
    }

    /**
     * Runs a command the session can answer. Returns null for the ones handled elsewhere
     * (help, status, sound, notifications, clear) or unknown words.
     */
    public List<LogLine> run(String input, long now) {
        String trimmed = input.trim();
        String[] parts = trimmed.split("\\s+", 2);
        String cmd = command(parts[0]);
        String rest = parts.length > 1 ? parts[1].trim() : "";
        if (cmd == null) {
            return null;
        }
        SessionSnapshot snap = session.snapshot();
        List<LogLine> out = new ArrayList<>();
        switch (cmd) {
            case "crew":
                crew(snap, now, out);
                break;
            case "scout":
                scout(snap, rest, now, out);
                break;
            case "send":
                return send(snap, rest, now);
            case "recall":
                return recall(snap, rest, now);
            case "places":
                places(snap, now, out);
                break;
            case "map":
                map(snap, now, out);
                break;
            case "inventory":
                inventory(snap, now, out);
                break;
            case "power":
                power(snap, now, out);
                break;
            case "food":
                out.add(line(now, Double.isInfinite(snap.foodDaysLeft) ? t("foodNobody")
                        : t(snap.hungry ? "foodHungry" : "food", vars("days",
                        String.format(Locale.ENGLISH, "%.1f", snap.foodDaysLeft)))));
                break;
            case "weather":
                out.add(line(now, t("weather", vars("weather", snap.weatherName,
                        "daytime", t(snap.base.night ? "night" : "day")))));
                break;
            case "radio":
                radio(snap, now, out);
                break;
            case "decode":
                return decode(snap, rest, now);
            case "goals":
                for (SessionSnapshot.GoalView g : snap.base.goals) {
                    out.add(line(now, t("goal", vars("label", g.label, "progress", String.valueOf(g.progress),
                            "target", String.valueOf(g.target)))));
                }
                break;
            case "time":
                out.add(line(now, t("time", vars("time", String.format(Locale.ENGLISH, "%tR", now),
                        "daytime", t(snap.base.night ? "night" : "day")))));
                break;
            case "name":
                if (rest.isEmpty()) {
                    out.add(line(now, t("usage", vars("usage", "name <what the crew calls you>"))));
                } else {
                    String set = session.setOperatorName(rest);
                    out.add(line(now, TextTemplate.format(session.content().getTerminal().getNameChanged(),
                            vars("operator", set))));
                }
                break;
            default:
                return null; // help, status, sound, notifications, clear: the view model's
        }
        return out;
    }

    private void crew(SessionSnapshot snap, long now, List<LogLine> out) {
        int alive = 0;
        int home = 0;
        for (SessionSnapshot.ScoutView s : snap.scouts) {
            alive += s.dead ? 0 : 1;
            home += !s.dead && s.awayAt == null ? 1 : 0;
        }
        out.add(line(now, t("crewHeader", vars("alive", String.valueOf(alive), "home", String.valueOf(home)))));
        for (SessionSnapshot.ScoutView s : snap.scouts) {
            if (s.dead) {
                out.add(line(now, t("crewDead", vars("name", s.name))));
                continue;
            }
            out.add(line(now, t("crewLine", vars("name", s.name,
                    "where", s.awayAt != null ? s.awayAt : s.status, "mood", s.mood,
                    "stress", String.valueOf(s.stress), "bond", String.valueOf(s.bondLevel),
                    "levels", String.valueOf(s.bondLevels)))));
        }
    }

    private SessionSnapshot.ScoutView findScout(SessionSnapshot snap, String query) {
        String q = normalize(query);
        if (q.isEmpty()) {
            return null;
        }
        for (SessionSnapshot.ScoutView s : snap.scouts) {
            if (normalize(s.name).equals(q)) {
                return s;
            }
        }
        for (SessionSnapshot.ScoutView s : snap.scouts) {
            if (normalize(s.name).startsWith(q)) {
                return s;
            }
        }
        return null;
    }

    private void scout(SessionSnapshot snap, String rest, long now, List<LogLine> out) {
        SessionSnapshot.ScoutView s = findScout(snap, rest);
        if (s == null) {
            out.add(line(now, rest.isEmpty() ? t("usage", vars("usage", "scout <name>"))
                    : t("noSuchScout", vars("input", rest))));
            return;
        }
        out.add(line(now, t("scoutHeader", vars("name", s.name, "status", s.awayAt != null ? s.awayAt : s.status))));
        StringBuilder stats = new StringBuilder();
        for (SessionSnapshot.StatView st : s.stats) {
            if (stats.length() > 0) {
                stats.append(" · ");
            }
            stats.append(st.name).append(' ').append(st.value).append('/').append(st.ceiling);
        }
        out.add(line(now, stats.toString()));
        out.add(line(now, t("scoutVitals", vars("mood", s.mood == null ? "-" : s.mood,
                "stress", String.valueOf(s.stress), "trust", String.valueOf(s.trust),
                "bond", String.valueOf(s.bondLevel), "levels", String.valueOf(s.bondLevels)))));
        if (!s.traits.isEmpty()) {
            out.add(line(now, t("scoutTraits", vars("traits", String.join(", ", s.traits)))));
        }
        if (s.gear != null) {
            out.add(line(now, t("scoutGear", vars("gear", s.gear))));
        }
        if (s.task != null) {
            out.add(line(now, t("scoutTask", vars("task", s.task))));
        }
    }

    /** send mara [+ noor] [to] hospital */
    private List<LogLine> send(SessionSnapshot snap, String rest, long now) {
        List<LogLine> out = new ArrayList<>();
        String[] words = rest.replace("+", " ").replace(",", " ").split("\\s+");
        List<SessionSnapshot.ScoutView> team = new ArrayList<>();
        int i = 0;
        while (i < words.length && team.size() < 2) {
            SessionSnapshot.ScoutView s = words[i].isEmpty() ? null : findScout(snap, words[i]);
            if (s == null) {
                break;
            }
            team.add(s);
            i++;
        }
        if (i < words.length && (normalize(words[i]).equals("to") || normalize(words[i]).equals("and"))) {
            i++;
        }
        String placeQuery = String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length)).trim();
        if (team.isEmpty()) {
            out.add(line(now, rest.isEmpty() ? t("usage", vars("usage", "send <name> [+name] <place>"))
                    : t("noSuchScout", vars("input", words[0]))));
            return out;
        }
        for (SessionSnapshot.ScoutView s : team) {
            if (!s.canDispatch) {
                out.add(line(now, t("cantGo", vars("name", s.name))));
                return out;
            }
        }
        SessionSnapshot.ZoneView zone = findPlace(snap, placeQuery);
        if (zone == null) {
            out.add(line(now, placeQuery.isEmpty() ? t("usage", vars("usage", "send <name> [+name] <place>"))
                    : t("noSuchPlace", vars("input", placeQuery))));
            return out;
        }
        return session.dispatch(team.get(0).id, zone.id, Mission.Type.EXPLORE, null,
                team.size() > 1 ? team.get(1).id : null);
    }

    private SessionSnapshot.ZoneView findPlace(SessionSnapshot snap, String query) {
        String q = normalize(query);
        if (q.isEmpty()) {
            return null;
        }
        for (SessionSnapshot.ZoneView z : snap.zones) {
            if (normalize(z.name).equals(q)) {
                return z;
            }
        }
        for (SessionSnapshot.ZoneView z : snap.zones) {
            if (normalize(z.name).contains(q)) {
                return z;
            }
        }
        return null;
    }

    private List<LogLine> recall(SessionSnapshot snap, String rest, long now) {
        SessionSnapshot.ScoutView s = findScout(snap, rest);
        if (s == null) {
            return List.of(line(now, rest.isEmpty() ? t("usage", vars("usage", "recall <name>"))
                    : t("noSuchScout", vars("input", rest))));
        }
        return session.recall(s.id);
    }

    private void places(SessionSnapshot snap, long now, List<LogLine> out) {
        if (snap.zones.isEmpty()) {
            out.add(line(now, t("placesNone")));
            return;
        }
        out.add(line(now, t("placesHeader")));
        for (SessionSnapshot.ZoneView z : snap.zones) {
            out.add(line(now, t("placeLine", vars("name", z.name, "band", z.band,
                    "danger", String.valueOf(z.danger), "min", String.valueOf(z.minMinutes),
                    "max", String.valueOf(z.maxMinutes),
                    "extra", z.depleted >= 15 ? t("placeDepleted", vars("pct", String.valueOf(z.depleted))) : ""))));
        }
    }

    private void map(SessionSnapshot snap, long now, List<LogLine> out) {
        for (int ring = 0; ring < snap.map.bands.size(); ring++) {
            int total = 0;
            int scouted = 0;
            int heard = 0;
            boolean reachable = false;
            for (SessionSnapshot.SectorView s : snap.map.sectors) {
                if (s.ring == ring) {
                    total++;
                    scouted += s.scouted ? 1 : 0;
                    heard += s.heard && !s.scouted ? 1 : 0;
                    reachable |= s.reachable;
                }
            }
            int places = 0;
            for (SessionSnapshot.PlaceView p : snap.map.places) {
                places += p.ring == ring ? 1 : 0;
            }
            out.add(line(now, t(reachable ? "mapRing" : "mapRingFar", vars("band", snap.map.bands.get(ring),
                    "scouted", String.valueOf(scouted), "total", String.valueOf(total),
                    "heard", String.valueOf(heard), "places", String.valueOf(places)))));
        }
    }

    private void inventory(SessionSnapshot snap, long now, List<LogLine> out) {
        List<String> supplies = new ArrayList<>();
        for (SessionSnapshot.ResourceView r : snap.resources) {
            if (r.amount > 0) {
                supplies.add(r.amount + " " + r.name.toLowerCase(Locale.ROOT));
            }
        }
        out.add(line(now, t("invSupplies", vars("list", supplies.isEmpty() ? t("nothing") : String.join(", ", supplies)))));
        List<String> gear = new ArrayList<>();
        for (SessionSnapshot.ItemView g : snap.base.gearStock) {
            gear.add(g.name);
        }
        out.add(line(now, t("invGear", vars("list", gear.isEmpty() ? t("nothing") : String.join(", ", gear)))));
        List<String> keepsakes = new ArrayList<>();
        for (SessionSnapshot.ItemView i : snap.inventory) {
            keepsakes.add(i.name);
        }
        out.add(line(now, t("invKeepsakes", vars("list", keepsakes.isEmpty() ? t("nothing") : String.join(", ", keepsakes)))));
        List<String> keys = new ArrayList<>();
        for (SessionSnapshot.KeyItemView k : snap.keyItems) {
            keys.add(k.name);
        }
        out.add(line(now, t("invKeys", vars("list", keys.isEmpty() ? t("nothing") : String.join(", ", keys)))));
    }

    private void power(SessionSnapshot snap, long now, List<LogLine> out) {
        out.add(line(now, t(snap.base.blackout ? "powerBlackout" : "power", vars(
                "used", String.valueOf(snap.base.used), "available", String.valueOf(snap.base.available),
                "reserve", String.valueOf(snap.base.reserve), "capacity", String.valueOf(snap.base.reserveCapacity)))));
        for (SessionSnapshot.ConsumerView c : snap.base.consumers) {
            out.add(line(now, t("powerLine", vars("name", c.name, "effective", String.valueOf(c.effective),
                    "max", String.valueOf(c.max)))));
        }
    }

    private void radio(SessionSnapshot snap, long now, List<LogLine> out) {
        if (snap.radio.transmissions.isEmpty()) {
            out.add(line(now, t("radioNone")));
            return;
        }
        out.add(line(now, t("radioHeader", vars("n", String.valueOf(snap.radio.transmissions.size())))));
        int n = 1;
        for (SessionSnapshot.TransmissionView tr : snap.radio.transmissions) {
            out.add(line(now, t("radioLine", vars("n", String.valueOf(n++), "kind", tr.kind, "band", tr.band,
                    "text", tr.cipherText))));
        }
    }

    /** decode 2 the answer */
    private List<LogLine> decode(SessionSnapshot snap, String rest, long now) {
        String[] parts = rest.split("\\s+", 2);
        int n;
        try {
            n = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return List.of(line(now, t("usage", vars("usage", "decode <number> <answer>"))));
        }
        if (n < 1 || n > snap.radio.transmissions.size() || parts.length < 2) {
            return List.of(line(now, t("usage", vars("usage", "decode <number> <answer>  (see: radio)"))));
        }
        return session.decode(snap.radio.transmissions.get(n - 1).id, parts[1]);
    }

    /**
     * Completions for what's being typed: commands first, then names (scout/send/recall), then
     * places (send). With nothing typed, the latest commands from {@code history}.
     */
    public static List<String> suggest(String typed, SessionSnapshot snap, List<String> history) {
        List<String> out = new ArrayList<>();
        String text = typed.replaceAll("^\\s+", "");
        if (text.isEmpty()) {
            for (int i = history.size() - 1; i >= 0 && out.size() < 4; i--) {
                if (!out.contains(history.get(i))) {
                    out.add(history.get(i));
                }
            }
            return out;
        }
        String[] words = text.split("\\s+", -1);
        if (words.length == 1) {
            String w = normalize(words[0]);
            for (String c : COMMANDS.keySet()) {
                if (c.startsWith(w) && !c.equals(w)) {
                    out.add(c + " ");
                }
            }
            return out;
        }
        String cmd = command(words[0]);
        String last = normalize(words[words.length - 1]);
        String head = text.substring(0, text.length() - words[words.length - 1].length());
        if (snap == null || cmd == null) {
            return out;
        }
        boolean wantsName = "scout".equals(cmd) || "recall".equals(cmd)
                || ("send".equals(cmd) && words.length <= 3);
        if (wantsName) {
            for (SessionSnapshot.ScoutView s : snap.scouts) {
                boolean fits = "send".equals(cmd) ? s.canDispatch : !s.dead;
                if (fits && normalize(s.name).startsWith(last) && !text.contains(s.name + " ")) {
                    out.add(head + s.name + " ");
                }
            }
        }
        if ("send".equals(cmd) && words.length >= 3) {
            for (SessionSnapshot.ZoneView z : snap.zones) {
                if (normalize(z.name).startsWith(last) || normalize(z.name).contains(" " + last)) {
                    out.add(head + z.name);
                }
            }
        }
        if ("decode".equals(cmd) && words.length == 2) {
            for (int i = 1; i <= snap.radio.transmissions.size(); i++) {
                out.add(head + i + " ");
            }
        }
        return out.size() > 6 ? out.subList(0, 6) : out;
    }
}
