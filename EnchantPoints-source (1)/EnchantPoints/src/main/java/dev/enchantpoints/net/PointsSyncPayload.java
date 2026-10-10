package dev.enchantpoints.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client. The client only displays; it never calculates. */
public record PointsSyncPayload(int total, int max, int yellowAtPercent, boolean showHud) implements CustomPayload {
    public static final CustomPayload.Id<PointsSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of("enchantpoints", "sync"));
    public static final PacketCodec<RegistryByteBuf, PointsSyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, PointsSyncPayload::total,
            PacketCodecs.VAR_INT, PointsSyncPayload::max,
            PacketCodecs.VAR_INT, PointsSyncPayload::yellowAtPercent,
            PacketCodecs.BOOLEAN, PointsSyncPayload::showHud,
            PointsSyncPayload::new);

    @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
