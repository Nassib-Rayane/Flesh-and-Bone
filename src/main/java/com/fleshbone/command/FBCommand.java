package com.fleshbone.command;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

import com.fleshbone.FleshBone;
import com.fleshbone.attach.FBAttachments;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.injury.Injury;
import com.fleshbone.sim.injury.InjuryKind;
import com.fleshbone.sim.system.CirculationSystem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /fleshbone status|heal|injure|set|fracture|arrest|restart}: inspect and manipulate bodies (operators only). */
@EventBusSubscriber(modid = FleshBone.MODID)
public final class FBCommand {
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(
            value -> Component.translatable("fleshbone.command.unknown", String.valueOf(value)));

    /** Whole-body values settable with {@code /fleshbone set}. */
    private static final Map<String, BiConsumer<Body, Float>> STATS = Map.ofEntries(
            Map.entry("blood", (b, v) -> b.vitals.bloodVolume = v),
            Map.entry("consciousness", (b, v) -> b.vitals.consciousness = v),
            Map.entry("brain", (b, v) -> b.vitals.brainHealth = v),
            Map.entry("fibrillation", (b, v) -> b.vitals.fibrillation = v),
            Map.entry("spo2", (b, v) -> b.vitals.spo2 = v),
            Map.entry("heart_rate", (b, v) -> b.vitals.heartRate = v),
            Map.entry("hemothorax", (b, v) -> b.vitals.hemothorax = v),
            Map.entry("viscosity", (b, v) -> b.vitals.viscosity = v),
            Map.entry("shock", (b, v) -> b.vitals.shock = v),
            Map.entry("adrenaline", (b, v) -> b.vitals.adrenaline = v),
            Map.entry("sepsis", (b, v) -> b.vitals.sepsis = v),
            Map.entry("immunity", (b, v) -> b.vitals.immunity = v),
            Map.entry("temperature", (b, v) -> b.vitals.bodyTemp = v),
            Map.entry("thirst", (b, v) -> b.needs.thirst = v),
            Map.entry("energy", (b, v) -> b.needs.energy = v),
            Map.entry("stamina", (b, v) -> b.needs.stamina = v),
            Map.entry("sickness", (b, v) -> b.needs.sickness = v),
            Map.entry("dirtiness", (b, v) -> b.needs.dirtiness = v),
            Map.entry("wetness", (b, v) -> b.needs.wetness = v),
            Map.entry("mood", (b, v) -> b.mind.mood = v),
            Map.entry("trauma", (b, v) -> b.mind.trauma = v),
            Map.entry("opiate", (b, v) -> b.chem.opiate = v));

    private static final SuggestionProvider<CommandSourceStack> PARTS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(BodyPart.VALUES).map(BodyPart::serializedName), builder);
    private static final SuggestionProvider<CommandSourceStack> KINDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(InjuryKind.values()).map(k -> k.name().toLowerCase(Locale.ROOT)), builder);
    private static final SuggestionProvider<CommandSourceStack> STAT_NAMES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(STATS.keySet(), builder);

    private FBCommand() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fleshbone")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> status(ctx, EntityArgument.getPlayer(ctx, "target")))))
                .then(Commands.literal("heal")
                        .executes(ctx -> forEach(ctx, java.util.List.of(ctx.getSource().getPlayerOrException()), Body::healFully))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> forEach(ctx, EntityArgument.getPlayers(ctx, "targets"), Body::healFully))))
                .then(Commands.literal("injure")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("part", StringArgumentType.word()).suggests(PARTS)
                                        .then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS)
                                                .then(Commands.argument("severity", FloatArgumentType.floatArg(0f, 500f))
                                                        .executes(FBCommand::injure))))))
                .then(Commands.literal("fracture")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("part", StringArgumentType.word()).suggests(PARTS)
                                        .executes(ctx -> {
                                            BodyPart part = part(ctx);
                                            return forEach(ctx, EntityArgument.getPlayers(ctx, "targets"), b -> b.part(part).fracture = 1f);
                                        }))))
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("stat", StringArgumentType.word()).suggests(STAT_NAMES)
                                        .then(Commands.argument("value", FloatArgumentType.floatArg())
                                                .executes(FBCommand::set)))))
                .then(Commands.literal("arrest")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> forEach(ctx, EntityArgument.getPlayers(ctx, "targets"), CirculationSystem::stopHeart))))
                .then(Commands.literal("restart")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> forEach(ctx, EntityArgument.getPlayers(ctx, "targets"), CirculationSystem::restartHeart)))));
    }

    private static BodyPart part(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "part");
        BodyPart part = BodyPart.byName(name);
        if (part == null) throw UNKNOWN.create(name);
        return part;
    }

    private static int injure(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BodyPart part = part(ctx);
        String kindName = StringArgumentType.getString(ctx, "kind");
        InjuryKind kind;
        try {
            kind = InjuryKind.valueOf(kindName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw UNKNOWN.create(kindName);
        }
        float severity = FloatArgumentType.getFloat(ctx, "severity");
        return forEach(ctx, EntityArgument.getPlayers(ctx, "targets"),
                b -> b.injure(Injury.of(part, kind, severity), ThreadLocalRandom.current()));
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String stat = StringArgumentType.getString(ctx, "stat");
        BiConsumer<Body, Float> setter = STATS.get(stat);
        if (setter == null) throw UNKNOWN.create(stat);
        float value = FloatArgumentType.getFloat(ctx, "value");
        return forEach(ctx, EntityArgument.getPlayers(ctx, "targets"), b -> setter.accept(b, value));
    }

    private static int forEach(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, java.util.function.Consumer<Body> action) {
        for (ServerPlayer player : players) {
            Body body = player.getData(FBAttachments.BODY);
            action.accept(body);
            body.derived.compute(body);
            player.getData(FBAttachments.RUNTIME).dirty = true;
            player.syncData(FBAttachments.BODY);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("fleshbone.command.done", players.size()), true);
        return players.size();
    }

    private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        Body body = player.getData(FBAttachments.BODY);
        var v = body.vitals;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "%s — blood %.2f L (%s), BP %.0f/%.0f, HR %.0f, SpO2 %.0f%%, resp %.1f, fib %.0f%s%n"
                        + "consciousness %.0f%% (cap %.0f), brain %.1f%%, pain %.0f, shock %.0f, bleeding %.3f L/min (+%.3f internal)",
                player.getName().getString(), v.bloodVolume, v.bloodType.display(), v.systolic, v.diastolic, v.heartRate, v.spo2,
                v.respRate, v.fibrillation, v.cardiacArrest ? " CARDIAC ARREST" : "",
                v.consciousness, body.derived.consciousnessCap, v.brainHealth, body.derived.totalPain, v.shock,
                body.derived.externalBleed, body.derived.internalBleed));
        for (BodyPart part : BodyPart.VALUES) {
            PartState s = body.part(part);
            boolean healthy = s.skin >= 100f && s.muscle >= 100f && s.bleed <= 0f && s.internalBleed <= 0f && !s.isFractured()
                    && !s.isDislocated() && s.shrapnel == 0 && s.infection <= 0f && !s.tourniquet && !s.amputated && s.pain < 0.5f;
            if (healthy) continue;
            sb.append(String.format(Locale.ROOT, "%n  %s: skin %.0f, muscle %.0f, bleed %.3f L/min, pain %.0f%s%s%s%s%s",
                    part.serializedName(), s.skin, s.muscle, body.derived.partBleed[part.ordinal()], body.derived.partPain[part.ordinal()],
                    s.isFractured() ? String.format(Locale.ROOT, ", FRACTURE %.0f%%", s.fracture * 100f) : "",
                    s.isDislocated() ? ", DISLOCATED" : "",
                    s.shrapnel > 0 ? ", shrapnel x" + s.shrapnel : "",
                    s.internalBleed > 0f ? String.format(Locale.ROOT, ", internal %.3f L/min", body.derived.partInternal[part.ordinal()]) : "",
                    s.tourniquet ? ", TOURNIQUET" : ""));
        }
        String text = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
