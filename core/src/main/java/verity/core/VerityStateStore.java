package verity.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;
import static verity.core.VerityWorldState.*;

/** Versioned, bounded, checksummed storage. A corrupt save is an error, never a silently empty world. */
public final class VerityStateStore {
    private static final int MAGIC = 0x56525459, VERSION = 1, MAX_BYTES = 16 * 1024 * 1024;
    private final Path file;
    public VerityStateStore(Path file) { this.file = file.toAbsolutePath().normalize(); }

    public synchronized Optional<Snapshot> load() throws IOException {
        if (Files.notExists(file)) return Optional.empty();
        if (!Files.isRegularFile(file)) throw new IOException("VERITY save is not a readable regular file");
        long size = Files.size(file);
        if (size < 44 || size > MAX_BYTES) throw new IOException("Invalid VERITY save size: " + size);
        try (var in = new DataInputStream(Files.newInputStream(file))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("Unsupported VERITY save format");
            int length = in.readInt();
            if (length != size - 44) throw new IOException("Truncated or extended VERITY save");
            byte[] expected = in.readNBytes(32);
            byte[] payload = in.readNBytes(length);
            if (payload.length != length || in.read() != -1 || !MessageDigest.isEqual(expected, digest(payload)))
                throw new IOException("VERITY save checksum mismatch");
            return Optional.of(decode(payload));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid VERITY save contents", e);
        }
    }

    public synchronized void save(Snapshot snapshot) throws IOException {
        byte[] payload = encode(snapshot);
        if (payload.length > MAX_BYTES - 44) throw new IOException("VERITY save exceeds 16 MiB limit");
        Path parent = file.getParent();
        if (!Files.exists(parent)) {
            try { Files.createDirectories(parent, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))); }
            catch (UnsupportedOperationException e) { Files.createDirectories(parent); }
        }
        // Require an atomic replace on this filesystem. Do not truncate the previous save.
        Path temp;
        try { temp = Files.createTempFile(parent, ".verity-", ".tmp", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))); }
        catch (UnsupportedOperationException e) { temp = Files.createTempFile(parent, ".verity-", ".tmp"); }
        try {
            try (FileChannel channel = FileChannel.open(temp, WRITE, TRUNCATE_EXISTING)) {
                ByteBuffer header = ByteBuffer.allocate(44).putInt(MAGIC).putInt(VERSION).putInt(payload.length).put(digest(payload));
                header.flip();
                while (header.hasRemaining()) channel.write(header);
                ByteBuffer data = ByteBuffer.wrap(payload);
                while (data.hasRemaining()) channel.write(data);
                channel.force(true);
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
    }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void nullableUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeBoolean(id != null); if (id != null) uuid(out, id);
    }
    private static UUID nullableUuid(DataInputStream in) throws IOException { return in.readBoolean() ? uuid(in) : null; }
    private static int count(DataInputStream in, int limit) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > limit) throw new IOException("Invalid VERITY collection size");
        return n;
    }
    private static <T extends Enum<T>> T value(DataInputStream in, Class<T> type) throws IOException {
        try { return Enum.valueOf(type, in.readUTF()); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid VERITY enum", e); }
    }
    private static byte[] encode(Snapshot s) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            uuid(out, s.worldId()); out.writeInt(s.mood()); out.writeUTF(s.phase().name());
            nullableUuid(out, s.entityId()); nullableUuid(out, s.followTarget()); out.writeLong(s.storyProgress());
            out.writeInt(s.profiles().size());
            for (var p : s.profiles().values()) {
                uuid(out, p.uuid()); out.writeUTF(p.name()); out.writeInt(p.trust()); out.writeBoolean(p.starterIssued());
                out.writeLong(p.deaths()); out.writeLong(p.respawns()); out.writeLong(p.lastItemAt());
                out.writeInt(p.memories().size());
                for (var m : p.memories()) {
                    uuid(out, m.requestId()); out.writeLong(m.timestamp()); out.writeUTF(m.visibility().name());
                    out.writeUTF(m.input()); out.writeUTF(m.reply());
                }
                out.writeInt(p.processedRequests().size());
                for (var id : p.processedRequests()) uuid(out, id);
            }
            out.writeInt(s.grants().size());
            for (var g : s.grants()) {
                uuid(out, g.id()); uuid(out, g.player()); out.writeUTF(g.kind().name()); out.writeUTF(g.itemId());
                out.writeInt(g.amount()); out.writeLong(g.createdAt());
            }
        }
        return bytes.toByteArray();
    }
    private static Snapshot decode(byte[] bytes) throws IOException {
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            UUID world = uuid(in); int mood = in.readInt(); Phase phase = value(in, Phase.class);
            UUID entity = nullableUuid(in), follow = nullableUuid(in); long progress = in.readLong();
            Map<UUID, ProfileSnapshot> profiles = new HashMap<>();
            int playerCount = count(in, 10000);
            for (int i = 0; i < playerCount; i++) {
                UUID id = uuid(in); String name = in.readUTF(); int trust = in.readInt(); boolean starter = in.readBoolean();
                long deaths = in.readLong(), respawns = in.readLong(), lastItemAt = in.readLong();
                List<Memory> memories = new ArrayList<>();
                int memoryCount = count(in, 200);
                for (int j = 0; j < memoryCount; j++) {
                    memories.add(new Memory(uuid(in), in.readLong(), value(in, Visibility.class), in.readUTF(), in.readUTF()));
                }
                List<UUID> requests = new ArrayList<>();
                int requestCount = count(in, 256);
                for (int j = 0; j < requestCount; j++) requests.add(uuid(in));
                ProfileSnapshot p = new ProfileSnapshot(id, name, trust, starter, deaths, respawns, lastItemAt, memories, requests);
                if (profiles.put(id, p) != null) throw new IOException("Duplicate player UUID in save");
            }
            List<Grant> grants = new ArrayList<>();
            int grantCount = count(in, 10000);
            for (int i = 0; i < grantCount; i++) {
                grants.add(new Grant(uuid(in), uuid(in), value(in, GrantKind.class), in.readUTF(), in.readInt(), in.readLong()));
            }
            if (in.read() != -1) throw new IOException("Unexpected trailing save data");
            return new Snapshot(world, mood, phase, entity, follow, progress, profiles, grants);
        }
    }
}
