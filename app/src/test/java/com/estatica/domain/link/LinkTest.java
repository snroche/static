package com.estatica.domain.link;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.estatica.TestContent;
import com.estatica.domain.content.GameContent;

import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LinkTest {

    private static final long NOW = 1_790_000_000_000L;

    private static LinkCodec.Decoded roundTrip(LinkPacket p) {
        return LinkCodec.decode(LinkCodec.encode(p), "");
    }

    @Test
    public void messageSurvivesTheTrip() {
        LinkPacket p = LinkPacket.message("LU4QXR", "Santi", NOW, "Meet at the dam, ñandú.", "CAESAR", 7);
        LinkCodec.Decoded d = roundTrip(p);
        assertTrue(d.ok());
        assertEquals(p.getId(), d.packet.getId());
        assertEquals("LU4QXR", d.packet.getCallsign());
        assertEquals("Santi", d.packet.getSender());
        assertEquals("Meet at the dam, ñandú.", d.packet.getText());
        assertEquals("CAESAR", d.packet.getScheme());
        assertEquals(7, d.packet.getShift());
        assertEquals(NOW / 60_000L, d.packet.getCreatedMinutes());
    }

    @Test
    public void dropAndPersonSurviveTheTrip() {
        Map<String, Integer> res = new LinkedHashMap<>();
        res.put("scrap", 10);
        res.put("rations", 3);
        LinkPacket drop = LinkPacket.drop("LU1AAA", "", NOW, res,
                List.of(new LinkPacket.Gear("casco", "RARE", 64)), "for the kids");
        LinkPacket got = roundTrip(drop).packet;
        assertEquals(res, got.getResources());
        assertEquals("casco", got.getGear().get(0).gearId);
        assertEquals("RARE", got.getGear().get(0).rarity);
        assertEquals(64, got.getGear().get(0).condition);
        assertEquals("for the kids", got.getText());

        Map<String, Integer> stats = Map.of("wits", 6, "nerve", 3);
        LinkPacket person = LinkPacket.person(LinkPacket.Type.WANDERER, "LU1AAA", "", NOW, "Ana", true, stats,
                List.of("brave"), "We crossed the river at night.");
        LinkPacket back = roundTrip(person).packet;
        assertEquals(LinkPacket.Type.WANDERER, back.getType());
        assertEquals("Ana", back.getName());
        assertTrue(back.isFemale());
        assertEquals(stats, back.getStats());
        assertEquals(List.of("brave"), back.getTraits());
    }

    @Test
    public void codesAreGroupedAndForgiving() {
        LinkPacket p = LinkPacket.message("LU4QXR", "", NOW, "hello", "PLAIN", 0);
        String code = LinkCodec.encode(p);
        assertTrue(code.matches("[0-9A-Z]{1,5}( [0-9A-Z]{1,5})*"));
        // Lower case, dashes, line breaks, and O for 0 all read the same.
        String sloppy = code.toLowerCase().replace(' ', '-').replace('0', 'o') + "\n";
        LinkCodec.Decoded d = LinkCodec.decode(sloppy, "");
        assertTrue(d.ok());
        assertEquals("hello", d.packet.getText());
    }

    @Test
    public void typosAndJunkAreCaught() {
        String code = LinkCodec.encode(LinkPacket.message("LU4QXR", "", NOW, "hello there", "PLAIN", 0));
        char[] chars = code.toCharArray();
        int i = chars.length / 2;
        chars[i] = chars[i] == 'A' ? 'B' : 'A';
        LinkCodec.Decoded typo = LinkCodec.decode(new String(chars), "");
        assertFalse(typo.ok());
        assertEquals(LinkCodec.Error.DAMAGED, typo.error);
        assertEquals(LinkCodec.Error.GARBLED, LinkCodec.decode("hello world!", "").error);
        assertEquals(LinkCodec.Error.GARBLED, LinkCodec.decode("", "").error);
    }

    @Test
    public void newerCodesSayToUpdate() {
        byte[] raw = LinkCodec.unbase32(LinkCodec.encode(LinkPacket.message("LU4QXR", "", NOW, "x", "PLAIN", 0)));
        raw[1] = (byte) (LinkCodec.VERSION + 1);
        assertEquals(LinkCodec.Error.NEWER, LinkCodec.decode(LinkCodec.base32(raw), "").error);
    }

    @Test
    public void broadcastsNeedTheDevelopersKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair ours = gen.generateKeyPair();
        KeyPair theirs = gen.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(ours.getPublic().getEncoded());
        LinkPacket b = LinkPacket.broadcast("00000000-0000-0000-0000-000000000001", NOW, "TEST", "line one\nline two",
                Map.of("batteries", 2), "lore", 20_000);

        LinkCodec.Decoded good = LinkCodec.decode(LinkCodec.encodeSigned(b, ours.getPrivate()), publicKey);
        assertTrue(good.ok());
        assertEquals("TEST", good.packet.getTitle());
        assertEquals("line one\nline two", good.packet.getText());
        assertEquals(20_000, good.packet.getExpiresDay());

        LinkCodec.Decoded forged = LinkCodec.decode(LinkCodec.encodeSigned(b, theirs.getPrivate()), publicKey);
        assertEquals(LinkCodec.Error.FORGED, forged.error);
        // A broadcast made like a player's code (CRC only) isn't one either.
        assertEquals(LinkCodec.Error.FORGED, LinkCodec.decode(LinkCodec.encode(b), publicKey).error);
    }

    @Test
    public void theShippedKeyIsValid() {
        GameContent content = TestContent.get();
        String key = content.getBalance().getLink().getBroadcastKey();
        assertFalse(key.isEmpty());
        // A broadcast not signed with it fails as forged, not as a bad key or a crash.
        LinkPacket b = LinkPacket.broadcast("00000000-0000-0000-0000-000000000002", NOW, "T", "x", Map.of(), "", 0);
        assertEquals(LinkCodec.Error.FORGED, LinkCodec.decode(LinkCodec.encode(b), key).error);
    }

    @Test
    public void callsignsAreStableAndLookArgentine() {
        String a = Callsign.of("3f1c9a2e-0000-4000-8000-000000000001");
        assertEquals(a, Callsign.of("3F1C9A2E-0000-4000-8000-000000000001"));
        assertTrue(a, a.matches("LU[1-9][A-Z]{3}"));
        assertNotEquals(a, Callsign.of("3f1c9a2e-0000-4000-8000-000000000002"));
    }

    @Test
    public void sanitizeTrimsWhatComesIn() {
        LinkRules rules = new LinkRules(TestContent.get());
        int max = rules.balance().getDropMaxUnits();
        Map<String, Integer> res = new LinkedHashMap<>();
        res.put("scrap", max);
        res.put("rations", 50);
        res.put("unobtainium", 5);
        LinkPacket drop = LinkPacket.drop("LU1AAA", "", NOW, res,
                List.of(new LinkPacket.Gear("no_such_gear", "RARE", 50)), "");
        LinkPacket clean = rules.sanitize(drop);
        assertNotNull(clean);
        assertEquals(Map.of("scrap", max), clean.getResources());
        assertTrue(clean.getGear().isEmpty());

        // Nothing usable: no packet.
        assertNull(rules.sanitize(LinkPacket.drop("LU1AAA", "", NOW, Map.of("unobtainium", 3), List.of(), "")));
        assertNull(rules.sanitize(LinkPacket.message("LU1AAA", "", NOW, "   ", "PLAIN", 0)));

        String longText = "x".repeat(1000);
        LinkPacket msg = rules.sanitize(LinkPacket.message("LU1AAA", "", NOW, longText, "VIGENERE", 40));
        assertEquals(rules.balance().getMessageMaxChars(), msg.getText().length());
        assertEquals(LinkRules.PLAIN, msg.getScheme());
    }

    @Test
    public void dropLimits() {
        LinkRules rules = new LinkRules(TestContent.get());
        int max = rules.balance().getDropMaxUnits();
        assertTrue(rules.dropAllowed(Map.of("scrap", max), 0));
        assertFalse(rules.dropAllowed(Map.of("scrap", max + 1), 0));
        assertFalse(rules.dropAllowed(Map.of(), 0));
        assertTrue(rules.dropAllowed(Map.of(), 1));
        assertFalse(rules.dropAllowed(Map.of(), rules.balance().getDropMaxGear() + 1));
    }

    @Test
    public void wanderersGiveByTheirBestStat() {
        LinkRules rules = new LinkRules(TestContent.get());
        LinkPacket strong = LinkPacket.person(LinkPacket.Type.WANDERER, "LU1AAA", "", NOW, "Beto", false,
                Map.of("endurance", 8, "wits", 2), List.of(), "");
        assertEquals("endurance", LinkRules.bestStat(strong));
        assertFalse(rules.wandererGift(strong).isEmpty());
        LinkPacket clever = LinkPacket.person(LinkPacket.Type.WANDERER, "LU1AAA", "", NOW, "Irene", true,
                Map.of("endurance", 2, "wits", 9), List.of(), "");
        assertEquals("CACHE", rules.wandererIntel(clever));
    }

    @Test
    public void harderSchemesPayMore() {
        GameContent content = TestContent.get();
        LinkRules rules = new LinkRules(content);
        java.util.Map<String, java.util.Map<String, Integer>> rewards = content.getBalance().getLink().getDecodeRewards();
        // Every scheme a message can go in pays something, except plain, and each pays at least the one before.
        int previous = 0;
        for (String scheme : LinkRules.MESSAGE_SCHEMES) {
            if (LinkRules.PLAIN.equals(scheme)) {
                assertTrue(rules.decodeReward(scheme, com.estatica.core.GameRandom.fromSeed(1)).isEmpty());
                continue;
            }
            assertTrue(scheme, rewards.containsKey(scheme));
            int most = LinkRules.units(rewards.get(scheme));
            assertTrue(scheme, most >= previous);
            previous = most;
            for (int seed = 0; seed < 20; seed++) {
                Map<String, Integer> got = rules.decodeReward(scheme, com.estatica.core.GameRandom.fromSeed(seed));
                assertFalse(got.isEmpty());
                got.forEach((k, v) -> assertTrue(v >= 1 && v <= rewards.get(scheme).get(k)));
            }
        }
    }

    @Test
    public void wanderersWaitAtTheFarthestPlace() {
        Map<String, Integer> bands = Map.of("a", 0, "b", 2, "c", 1, "d", 2);
        assertEquals("b", LinkRules.waitingZone(List.of("a", "b", "c", "d"), bands));
        assertNull(LinkRules.waitingZone(List.of(), bands));
    }

    @Test
    public void giftsAndDropResourcesAreRealContent() {
        GameContent content = TestContent.get();
        for (String id : content.getBalance().getLink().getDropResources()) {
            assertNotNull(id, content.getResources().get(id));
        }
        for (Map<String, Integer> gift : content.getBalance().getLink().getWanderer().getGifts().values()) {
            for (String id : gift.keySet()) {
                assertNotNull(id, content.getResources().get(id));
            }
        }
        for (String key : List.of("messagePlain", "messageCiphered", "drop", "wandererHeard", "wandererMet",
                "lastWords", "broadcast", "garbled", "damaged", "newer", "forged", "own", "used", "limit",
                "expired", "invalid", "labelMessage", "labelDrop", "waitingRow", "plain", "tradeDone", "jointSet",
                "decodeReward")) {
            assertTrue(key, content.getTerminal().getLink().containsKey(key));
        }
    }
}
