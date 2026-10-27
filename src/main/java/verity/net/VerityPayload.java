package verity.net;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C only: clients cannot set mood, trust, identity, phase, or item permissions. */
public record VerityPayload(UUID requestId, String speech, int mood, int trust, int phase, boolean microphone, boolean narrate) implements CustomPacketPayload {
    public static final Type<VerityPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("verity", "state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VerityPayload> CODEC = CustomPacketPayload.codec(
            (p, b) -> {
                b.writeUUID(p.requestId()); b.writeUtf(p.speech(), 4096);
                b.writeVarInt(p.mood()); b.writeVarInt(p.trust()); b.writeVarInt(p.phase()); b.writeBoolean(p.microphone()); b.writeBoolean(p.narrate());
            }, b -> new VerityPayload(b.readUUID(), b.readUtf(4096), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean()));
    @Override public Type<VerityPayload> type() { return TYPE; }
}
