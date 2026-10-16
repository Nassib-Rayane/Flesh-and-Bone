package com.fleshbone.network;

import com.fleshbone.FleshBone;
import com.fleshbone.attach.BodyIO;
import com.fleshbone.sim.Body;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Network messages. Part indexes are {@code BodyPart.ordinal()}, -1 meaning the whole body. */
public final class FBPayloads {
    private FBPayloads() {}

    /** Client → server: apply the medical item in inventory {@code slot} to a body part of {@code targetId}. */
    public record Treat(int targetId, int part, int slot, float quality, int amount) implements CustomPacketPayload {
        public static final Type<Treat> TYPE = new Type<>(FleshBone.id("treat"));
        public static final StreamCodec<ByteBuf, Treat> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Treat::targetId,
                ByteBufCodecs.VAR_INT, Treat::part,
                ByteBufCodecs.VAR_INT, Treat::slot,
                ByteBufCodecs.FLOAT, Treat::quality,
                ByteBufCodecs.VAR_INT, Treat::amount,
                Treat::new);

        @Override public Type<Treat> type() { return TYPE; }
    }

    /** Hands-only procedures. */
    public enum Procedure { RESET_DISLOCATION, PULL_SHRAPNEL }

    /** Client → server: perform a procedure on a body part of {@code targetId}. */
    public record DoProcedure(int targetId, int part, int procedure, float quality) implements CustomPacketPayload {
        public static final Type<DoProcedure> TYPE = new Type<>(FleshBone.id("procedure"));
        public static final StreamCodec<ByteBuf, DoProcedure> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, DoProcedure::targetId,
                ByteBufCodecs.VAR_INT, DoProcedure::part,
                ByteBufCodecs.VAR_INT, DoProcedure::procedure,
                ByteBufCodecs.FLOAT, DoProcedure::quality,
                DoProcedure::new);

        @Override public Type<DoProcedure> type() { return TYPE; }
    }

    /** Devices that can be taken off a part. */
    public enum Device { BANDAGE, SPLINT, TOURNIQUET }

    /** Client → server: remove a device from a body part of {@code targetId}. */
    public record RemoveDevice(int targetId, int part, int device) implements CustomPacketPayload {
        public static final Type<RemoveDevice> TYPE = new Type<>(FleshBone.id("remove_device"));
        public static final StreamCodec<ByteBuf, RemoveDevice> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RemoveDevice::targetId,
                ByteBufCodecs.VAR_INT, RemoveDevice::part,
                ByteBufCodecs.VAR_INT, RemoveDevice::device,
                RemoveDevice::new);

        @Override public Type<RemoveDevice> type() { return TYPE; }
    }

    /** Client → server: CPR in progress on {@code targetId}, delivering {@code perfusion} (0–1). Sent about twice a second. */
    public record Cpr(int targetId, float perfusion) implements CustomPacketPayload {
        public static final Type<Cpr> TYPE = new Type<>(FleshBone.id("cpr"));
        public static final StreamCodec<ByteBuf, Cpr> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Cpr::targetId,
                ByteBufCodecs.FLOAT, Cpr::perfusion,
                Cpr::new);

        @Override public Type<Cpr> type() { return TYPE; }
    }

    /** Client → server: the medic closed the patient's panel. */
    public record StopExamining() implements CustomPacketPayload {
        public static final Type<StopExamining> TYPE = new Type<>(FleshBone.id("stop_examining"));
        public static final StreamCodec<ByteBuf, StopExamining> STREAM_CODEC = StreamCodec.unit(new StopExamining());

        @Override public Type<StopExamining> type() { return TYPE; }
    }

    /** Server → client: live copy of another player's body, sent while the medic examines them. */
    public record PatientBody(int targetId, boolean open, Body body) implements CustomPacketPayload {
        public static final Type<PatientBody> TYPE = new Type<>(FleshBone.id("patient_body"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PatientBody> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.targetId());
                    buf.writeBoolean(payload.open());
                    BodyIO.write(payload.body(), buf);
                },
                buf -> {
                    int id = buf.readVarInt();
                    boolean open = buf.readBoolean();
                    Body body = new Body();
                    BodyIO.read(body, buf);
                    return new PatientBody(id, open, body);
                });

        @Override public Type<PatientBody> type() { return TYPE; }
    }

    /**
     * Server → client: a thought of the player's character. Sent as keys so the client can translate it and scramble it
     * when the brain is damaged.
     */
    public record Thought(String key, String partKey) implements CustomPacketPayload {
        public static final Type<Thought> TYPE = new Type<>(FleshBone.id("thought"));
        public static final StreamCodec<ByteBuf, Thought> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Thought::key,
                ByteBufCodecs.STRING_UTF8, Thought::partKey,
                Thought::new);

        @Override public Type<Thought> type() { return TYPE; }
    }

    /** Server → client: the examination ended (patient out of reach, dead or gone). */
    public record PatientGone(int targetId) implements CustomPacketPayload {
        public static final Type<PatientGone> TYPE = new Type<>(FleshBone.id("patient_gone"));
        public static final StreamCodec<ByteBuf, PatientGone> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PatientGone::targetId, PatientGone::new);

        @Override public Type<PatientGone> type() { return TYPE; }
    }
}
