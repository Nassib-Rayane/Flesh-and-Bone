package com.fleshbone.attach;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import com.fleshbone.FleshBone;
import com.fleshbone.sim.Body;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class FBAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, FleshBone.MODID);

    /**
     * The simulated body. Saved with the player, synced to its owner only, and not copied on death: a respawned player
     * gets a fresh body.
     */
    public static final Supplier<AttachmentType<Body>> BODY = ATTACHMENT_TYPES.register("body", () ->
            AttachmentType.builder(() -> Body.create(ThreadLocalRandom.current()))
                    .serialize(new IAttachmentSerializer<Body>() {
                        @Override
                        public Body read(IAttachmentHolder holder, ValueInput input) {
                            Body body = new Body();
                            BodyIO.load(body, input);
                            return body;
                        }

                        @Override
                        public boolean write(Body body, ValueOutput output) {
                            BodyIO.save(body, output);
                            return true;
                        }
                    })
                    .sync(new AttachmentSyncHandler<Body>() {
                        @Override
                        public void write(RegistryFriendlyByteBuf buf, Body body, boolean initialSync) {
                            BodyIO.write(body, buf);
                        }

                        @Override
                        public Body read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, Body previousValue) {
                            Body body = previousValue != null ? previousValue : new Body();
                            BodyIO.read(body, buf);
                            return body;
                        }

                        @Override
                        public boolean sendToPlayer(IAttachmentHolder holder, ServerPlayer to) {
                            return holder == to;
                        }
                    })
                    .build());

    /** Items crafted once and depth reached, for intelligence experience. Saved, kept on death, never synced. */
    public static final Supplier<AttachmentType<Knowledge>> KNOWLEDGE = ATTACHMENT_TYPES.register("knowledge", () ->
            AttachmentType.builder(Knowledge::new)
                    .serialize(new IAttachmentSerializer<Knowledge>() {
                        @Override
                        public Knowledge read(IAttachmentHolder holder, ValueInput input) {
                            Knowledge knowledge = new Knowledge();
                            knowledge.load(input);
                            return knowledge;
                        }

                        @Override
                        public boolean write(Knowledge knowledge, ValueOutput output) {
                            knowledge.save(output);
                            return true;
                        }
                    })
                    .copyOnDeath()
                    .build());

    /** Transient server-side helper state. */
    public static final Supplier<AttachmentType<BodyRuntime>> RUNTIME = ATTACHMENT_TYPES.register("body_runtime", () ->
            AttachmentType.builder(BodyRuntime::new).build());

    private FBAttachments() {}

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }
}
