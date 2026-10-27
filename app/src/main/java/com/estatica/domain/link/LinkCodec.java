package com.estatica.domain.link;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Packets to codes and back. A code is a small binary frame (magic, version, type, a deflated
 * body) with a check value, written in Crockford base32 in groups of five, like a numbers
 * station: {@code 7KQ2M X9FAD 31ZTE …}. Players' codes carry a salted CRC that catches typos
 * and casual tampering; broadcasts carry an ECDSA signature only the developers can make.
 *
 * <p>Pure Java: the broadcast tool ({@code tools/link_broadcast.sh}) compiles this package as is.
 */
public final class LinkCodec {

    /** Why a code didn't come through. */
    public enum Error { GARBLED, DAMAGED, NEWER, FORGED }

    public static final class Decoded {
        public final LinkPacket packet;
        public final Error error;

        private Decoded(LinkPacket packet, Error error) {
            this.packet = packet;
            this.error = error;
        }

        public boolean ok() {
            return packet != null;
        }
    }

    public static final int VERSION = 1;
    private static final int MAGIC = 0xE5;
    private static final int FLAG_DEFLATED = 1;
    private static final byte[] SALT = "estatica/link/v1".getBytes(StandardCharsets.US_ASCII);
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    /** No body is anywhere near this: anything bigger is junk (or a zip bomb). */
    private static final int MAX_BODY = 16 * 1024;

    private LinkCodec() {
    }

    // ---- Encoding ----

    /** A player's code. */
    public static String encode(LinkPacket packet) {
        byte[] frame = frame(packet);
        return group(base32(concat(frame, crc(frame))));
    }

    /** A developer broadcast, signed with the private key kept outside the repo. */
    public static String encodeSigned(LinkPacket packet, PrivateKey key) throws GeneralSecurityException {
        byte[] frame = frame(packet);
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(key);
        s.update(frame);
        byte[] sig = s.sign();
        byte[] trailer = new byte[sig.length + 1];
        trailer[0] = (byte) sig.length;
        System.arraycopy(sig, 0, trailer, 1, sig.length);
        return group(base32(concat(frame, trailer)));
    }

    private static byte[] frame(LinkPacket p) {
        byte[] body = body(p);
        byte[] packed = deflate(body);
        boolean deflated = packed.length < body.length;
        byte[] payload = deflated ? packed : body;
        byte[] frame = new byte[4 + payload.length];
        frame[0] = (byte) MAGIC;
        frame[1] = (byte) VERSION;
        frame[2] = (byte) p.getType().code;
        frame[3] = (byte) (deflated ? FLAG_DEFLATED : 0);
        System.arraycopy(payload, 0, frame, 4, payload.length);
        return frame;
    }

    private static byte[] body(LinkPacket p) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            UUID id = UUID.fromString(p.getId());
            out.writeLong(id.getMostSignificantBits());
            out.writeLong(id.getLeastSignificantBits());
            out.writeUTF(p.getCallsign());
            out.writeUTF(p.getSender());
            out.writeInt((int) p.getCreatedMinutes());
            switch (p.getType()) {
                case MESSAGE:
                    out.writeUTF(p.scheme);
                    out.writeByte(p.shift);
                    out.writeUTF(p.text);
                    break;
                case DROP:
                    writeAmounts(out, p);
                    out.writeByte(p.gear.size());
                    for (LinkPacket.Gear g : p.gear) {
                        out.writeUTF(g.gearId);
                        out.writeUTF(g.rarity);
                        out.writeByte(g.condition);
                    }
                    out.writeUTF(p.text);
                    break;
                case WANDERER:
                case LAST_WORDS:
                    out.writeUTF(p.name);
                    out.writeBoolean(p.female);
                    out.writeByte(p.stats.size());
                    for (java.util.Map.Entry<String, Integer> e : p.stats.entrySet()) {
                        out.writeUTF(e.getKey());
                        out.writeByte(e.getValue());
                    }
                    out.writeByte(p.traits.size());
                    for (String t : p.traits) {
                        out.writeUTF(t);
                    }
                    out.writeUTF(p.text);
                    break;
                case BROADCAST:
                    out.writeUTF(p.title);
                    out.writeUTF(p.text);
                    writeAmounts(out, p);
                    out.writeUTF(p.lore);
                    out.writeInt(p.expiresDay);
                    break;
                default:
                    break;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    private static void writeAmounts(DataOutputStream out, LinkPacket p) throws IOException {
        out.writeByte(p.resources.size());
        for (java.util.Map.Entry<String, Integer> e : p.resources.entrySet()) {
            out.writeUTF(e.getKey());
            out.writeShort(e.getValue());
        }
    }

    // ---- Decoding ----

    /** A player's code or a broadcast; {@code broadcastKey} is the X.509 public key, base64. */
    public static Decoded decode(String code, String broadcastKey) {
        byte[] raw = unbase32(code);
        if (raw == null || raw.length < 8 || (raw[0] & 0xFF) != MAGIC) {
            return new Decoded(null, Error.GARBLED);
        }
        if ((raw[1] & 0xFF) > VERSION) {
            return new Decoded(null, Error.NEWER);
        }
        LinkPacket.Type type = LinkPacket.Type.fromCode(raw[2] & 0xFF);
        if (type == null) {
            return new Decoded(null, Error.GARBLED);
        }
        byte[] frame;
        if (type == LinkPacket.Type.BROADCAST) {
            frame = verified(raw, broadcastKey);
            if (frame == null) {
                return new Decoded(null, Error.FORGED);
            }
        } else {
            frame = Arrays.copyOf(raw, raw.length - 4);
            if (!Arrays.equals(crc(frame), Arrays.copyOfRange(raw, raw.length - 4, raw.length))) {
                return new Decoded(null, Error.DAMAGED);
            }
        }
        byte[] payload = Arrays.copyOfRange(frame, 4, frame.length);
        if ((frame[3] & FLAG_DEFLATED) != 0) {
            payload = inflate(payload);
            if (payload == null) {
                return new Decoded(null, Error.GARBLED);
            }
        }
        try {
            return new Decoded(parse(type, payload), null);
        } catch (IOException | RuntimeException e) {
            return new Decoded(null, Error.GARBLED);
        }
    }

    /**
     * The signed frame, or null. The trailer is [length][DER signature]; a DER ECDSA P-256
     * signature is at most 73 bytes (usually 70 to 72), so its length byte is found by trying each.
     */
    private static byte[] verified(byte[] raw, String broadcastKey) {
        if (broadcastKey == null || broadcastKey.isEmpty()) {
            return null;
        }
        try {
            PublicKey key = KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(broadcastKey)));
            for (int len = 8; len <= 73; len++) {
                int start = raw.length - len;
                if (start - 1 < 4 || (raw[start - 1] & 0xFF) != len) {
                    continue;
                }
                byte[] frame = Arrays.copyOf(raw, start - 1);
                Signature s = Signature.getInstance("SHA256withECDSA");
                s.initVerify(key);
                s.update(frame);
                try {
                    if (s.verify(Arrays.copyOfRange(raw, start, raw.length))) {
                        return frame;
                    }
                } catch (java.security.SignatureException e) {
                    // Not a signature at this length: try the next.
                }
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    private static LinkPacket parse(LinkPacket.Type type, byte[] payload) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        String id = new UUID(in.readLong(), in.readLong()).toString();
        String callsign = in.readUTF();
        String sender = in.readUTF();
        long created = in.readInt() & 0xFFFFFFFFL;
        LinkPacket p = new LinkPacket(id, type, callsign, sender, created);
        switch (type) {
            case MESSAGE:
                p.scheme = in.readUTF();
                p.shift = in.readByte();
                p.text = in.readUTF();
                break;
            case DROP: {
                readAmounts(in, p);
                int n = in.readUnsignedByte();
                for (int i = 0; i < n; i++) {
                    p.gear.add(new LinkPacket.Gear(in.readUTF(), in.readUTF(), in.readUnsignedByte()));
                }
                p.text = in.readUTF();
                break;
            }
            case WANDERER:
            case LAST_WORDS: {
                p.name = in.readUTF();
                p.female = in.readBoolean();
                int n = in.readUnsignedByte();
                for (int i = 0; i < n; i++) {
                    p.stats.put(in.readUTF(), (int) in.readByte());
                }
                n = in.readUnsignedByte();
                for (int i = 0; i < n; i++) {
                    p.traits.add(in.readUTF());
                }
                p.text = in.readUTF();
                break;
            }
            case BROADCAST:
                p.title = in.readUTF();
                p.text = in.readUTF();
                readAmounts(in, p);
                p.lore = in.readUTF();
                p.expiresDay = in.readInt();
                break;
            default:
                break;
        }
        return p;
    }

    private static void readAmounts(DataInputStream in, LinkPacket p) throws IOException {
        int n = in.readUnsignedByte();
        for (int i = 0; i < n; i++) {
            p.resources.put(in.readUTF(), (int) in.readShort());
        }
    }

    // ---- Bytes ----

    private static byte[] crc(byte[] frame) {
        CRC32 crc = new CRC32();
        crc.update(SALT);
        crc.update(frame);
        long v = crc.getValue();
        return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] data) {
        Inflater inflater = new Inflater(true);
        // "nowrap" wants one extra byte past the end of the stream.
        inflater.setInput(Arrays.copyOf(data, data.length + 1));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    return null;
                }
                out.write(buffer, 0, n);
                if (out.size() > MAX_BODY) {
                    return null;
                }
            }
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }

    // ---- Crockford base32 ----

    static String base32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET.charAt((buffer >>> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    /** Lenient: any case, spaces, dashes and line breaks ignored, O read as 0 and I/L as 1. */
    static byte[] unbase32(String code) {
        if (code == null) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : code.toUpperCase(java.util.Locale.ROOT).toCharArray()) {
            if (Character.isWhitespace(c) || c == '-') {
                continue;
            }
            if (c == 'O') {
                c = '0';
            } else if (c == 'I' || c == 'L') {
                c = '1';
            }
            int v = ALPHABET.indexOf(c);
            if (v < 0) {
                return null;
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >>> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    /** "7KQ2M X9FAD 31ZTE". */
    static String group(String code) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < code.length(); i += 5) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(code, i, Math.min(code.length(), i + 5));
        }
        return out.toString();
    }
}
