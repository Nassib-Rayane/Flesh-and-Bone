package com.fleshbone.item;

import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.random.RandomGenerator;

import com.fleshbone.sim.BandageType;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.treatment.Treatments;

import net.minecraft.world.item.ItemUseAnimation;

/** The medical actions behind each item. */
public final class MedicalActions {
    private MedicalActions() {}

    /** A part action built from small functions. */
    private record PartAction(int useTicks, ItemUseAnimation animation, Function<Body, BodyPart> targeting,
                              BiPredicate<BodyPart, PartState> allowed, Applier applier, Minigame minigame,
                              float quickQuality) implements MedicalAction {
        @Override public Target target() { return Target.PART; }

        @Override public BodyPart autoTarget(Body body) { return targeting.apply(body); }

        @Override
        public boolean canApply(Body body, BodyPart part) {
            return part != null && allowed.test(part, body.part(part));
        }

        @Override
        public Result apply(Body body, BodyPart part, float quality, int amount, RandomGenerator rng) {
            return applier.apply(body, part, quality, amount, rng);
        }
    }

    private record BodyAction(int useTicks, ItemUseAnimation animation, Applier applier, java.util.function.Predicate<Body> allowed,
                              Minigame minigame) implements MedicalAction {
        BodyAction(int useTicks, ItemUseAnimation animation, Applier applier) {
            this(useTicks, animation, applier, body -> true, Minigame.NONE);
        }

        @Override public Target target() { return Target.BODY; }

        @Override public BodyPart autoTarget(Body body) { return null; }

        @Override public boolean canApply(Body body, BodyPart part) { return allowed.test(body); }

        @Override
        public Result apply(Body body, BodyPart part, float quality, int amount, RandomGenerator rng) {
            return applier.apply(body, null, quality, amount, rng);
        }
    }

    @FunctionalInterface
    private interface Applier {
        MedicalAction.Result apply(Body body, BodyPart part, float quality, int amount, RandomGenerator rng);
    }

    private static MedicalAction.Minigame none() {
        return MedicalAction.Minigame.NONE;
    }

    /** Bandages and rags. {@code length} is how much of the roll one wrap job uses. */
    public static MedicalAction bandage(BandageType type, int length) {
        return new PartAction(type == BandageType.RAG ? 60 : 80, ItemUseAnimation.BRUSH, Treatments::bandageTarget,
                (part, s) -> Treatments.canBandage(s),
                (body, part, quality, amount, rng) -> {
                    Treatments.bandage(body, part, type, quality);
                    // A rag is used up whole; a roll loses one unit per wrap
                    int used = type == BandageType.RAG ? 1 : amount > 0 ? Math.max(2, Math.min(8, amount)) : length;
                    return MedicalAction.Result.ok(used);
                }, MedicalAction.Minigame.BANDAGE, 0.7f);
    }

    public static MedicalAction antiseptic() {
        return new PartAction(40, ItemUseAnimation.BRUSH, Treatments::disinfectTarget,
                (part, s) -> Treatments.canDisinfect(s),
                (body, part, quality, amount, rng) -> {
                    Treatments.disinfect(body, part, Treatments.ANTISEPTIC_SECONDS);
                    return MedicalAction.Result.ok(1);
                }, none(), 1f);
    }

    public static MedicalAction suture() {
        return new PartAction(120, ItemUseAnimation.BRUSH, Treatments::sutureTarget,
                (part, s) -> Treatments.canSuture(s),
                (body, part, quality, amount, rng) -> {
                    Treatments.suture(body, part, quality);
                    return MedicalAction.Result.ok(1);
                }, MedicalAction.Minigame.SUTURE, 0.6f);
    }

    public static MedicalAction tweezers() {
        return new PartAction(60, ItemUseAnimation.BRUSH, Treatments::shrapnelTarget,
                (part, s) -> Treatments.canExtract(s),
                (body, part, quality, amount, rng) -> Treatments.extractShrapnel(body, part, quality, true, rng)
                        ? MedicalAction.Result.ok(1)
                        : new MedicalAction.Result(false, 1, "fleshbone.message.extract_failed"),
                MedicalAction.Minigame.EXTRACT, 0.5f);
    }

    public static MedicalAction tourniquet() {
        return new PartAction(40, ItemUseAnimation.BRUSH, Treatments::tourniquetTarget,
                Treatments::canTourniquet,
                (body, part, quality, amount, rng) -> {
                    Treatments.tourniquet(body, part);
                    return MedicalAction.Result.ok(1);
                }, none(), 1f);
    }

    public static MedicalAction splint() {
        return new PartAction(100, ItemUseAnimation.BRUSH, Treatments::splintTarget,
                (part, s) -> Treatments.canSplint(s),
                (body, part, quality, amount, rng) -> {
                    Treatments.splint(body, part, quality);
                    return MedicalAction.Result.ok(1);
                }, MedicalAction.Minigame.SPLINT, 0.8f);
    }

    public static MedicalAction icePack() {
        return new PartAction(30, ItemUseAnimation.BRUSH, Treatments::mostPainful,
                (part, s) -> Treatments.canChill(s),
                (body, part, quality, amount, rng) -> {
                    Treatments.chill(body, part, Treatments.ICE_PACK_SECONDS);
                    return MedicalAction.Result.ok(1);
                }, none(), 1f);
    }

    public static MedicalAction antibiotics() {
        return new BodyAction(32, ItemUseAnimation.EAT, (body, part, quality, amount, rng) -> {
            Treatments.takeAntibiotics(body, 50f * dose(), rng);
            return MedicalAction.Result.ok(1);
        });
    }

    /** Amputation: health panel only (never chosen automatically), with confirmation and the saw minigame. */
    public static MedicalAction amputation() {
        PartAction base = new PartAction(200, ItemUseAnimation.BRUSH, body -> null, Treatments::canAmputate,
                (body, part, quality, amount, rng) -> {
                    Treatments.amputate(body, part, quality);
                    return MedicalAction.Result.ok(1);
                }, MedicalAction.Minigame.SAW, 0.5f);
        return new MedicalAction() {
            @Override public Target target() { return base.target(); }

            @Override public int useTicks() { return base.useTicks(); }

            @Override public ItemUseAnimation animation() { return base.animation(); }

            @Override public com.fleshbone.sim.BodyPart autoTarget(Body body) { return null; }

            @Override public boolean canApply(Body body, com.fleshbone.sim.BodyPart part) { return base.canApply(body, part); }

            @Override
            public Result apply(Body body, com.fleshbone.sim.BodyPart part, float quality, int amount, RandomGenerator rng) {
                return base.apply(body, part, quality, amount, rng);
            }

            @Override public Minigame minigame() { return base.minigame(); }

            @Override public boolean dangerous() { return true; }
        };
    }

    // ------------------------------------------------------------------------------------------------ heart and blood

    public static MedicalAction defibrillator() {
        return new BodyAction(40, ItemUseAnimation.BLOCK, (body, part, quality, amount, rng) -> {
            Treatments.ShockResult result = Treatments.defibrillate(body, quality, amount <= 0 ? 2 : amount, rng);
            return new MedicalAction.Result(result == Treatments.ShockResult.RESTARTED || result == Treatments.ShockResult.RHYTHM_RESTORED,
                    1, "fleshbone.message.shock." + result.name().toLowerCase(java.util.Locale.ROOT));
        }, body -> true, MedicalAction.Minigame.DEFIB);
    }

    private static MedicalAction injection(java.util.function.Consumer<Body> effect) {
        return new BodyAction(30, ItemUseAnimation.SPEAR, (body, part, quality, amount, rng) -> {
            effect.accept(body);
            return MedicalAction.Result.ok(1);
        });
    }

    public static MedicalAction epinephrine() {
        return injection(Treatments::injectEpinephrine);
    }

    public static MedicalAction antiarrhythmic() {
        return injection(Treatments::injectAntiarrhythmic);
    }

    public static MedicalAction naloxone() {
        return injection(Treatments::injectNaloxone);
    }

    public static MedicalAction antivenom() {
        return new BodyAction(30, ItemUseAnimation.SPEAR, (body, part, quality, amount, rng) -> {
            Treatments.injectAntivenom(body, rng);
            return MedicalAction.Result.ok(1);
        });
    }

    /** Dose multiplier from the server config. */
    private static float dose() {
        return com.fleshbone.config.FBConfig.DRUG_STRENGTH.get().floatValue();
    }

    public static MedicalAction procoagulant() {
        return injection(Treatments::injectProcoagulant);
    }

    public static MedicalAction thrombolytic() {
        return injection(Treatments::injectThrombolytic);
    }

    public static MedicalAction saline() {
        return new BodyAction(80, ItemUseAnimation.BLOCK, (body, part, quality, amount, rng) -> {
            if (quality <= 0f) return MedicalAction.Result.fail("fleshbone.message.no_vein");
            Treatments.infuseSaline(body, 0.35f);
            return MedicalAction.Result.ok(1);
        }, body -> true, MedicalAction.Minigame.INFUSION);
    }

    public static MedicalAction chestDrain() {
        return new BodyAction(100, ItemUseAnimation.BLOCK, (body, part, quality, amount, rng) -> {
            Treatments.chestDrain(body, quality);
            return MedicalAction.Result.ok(1);
        }, Treatments::canDrain, MedicalAction.Minigame.CHEST_DRAIN);
    }

    /** Transfuses the bag; an incompatible donor causes a reaction. */
    public static MedicalAction bloodBag() {
        return new StackAction(100, (body, quality, stack, patient) -> {
            if (quality <= 0f) return MedicalAction.Result.fail("fleshbone.message.no_vein");
            com.fleshbone.sim.BloodType donor = stack.get(FBDataComponents.BLOOD_TYPE.get());
            boolean ok = Treatments.transfuse(body, donor, 0.45f);
            return new MedicalAction.Result(ok, 1, ok ? null : "fleshbone.message.transfusion_reaction");
        }, body -> true);
    }

    /** Draws half a litre into the bag, which keeps the donor's blood group. */
    public static MedicalAction emptyBloodBag() {
        return new StackAction(100, (body, quality, stack, patient) -> {
            if (body.vitals.bloodVolume < 3.6f) return MedicalAction.Result.fail("fleshbone.message.not_enough_blood");
            Treatments.drawBlood(body, 0.45f);
            net.minecraft.world.item.ItemStack filled = new net.minecraft.world.item.ItemStack(FBItems.BLOOD_BAG.get());
            filled.set(FBDataComponents.BLOOD_TYPE.get(), body.vitals.bloodType);
            return new MedicalAction.Result(true, 1, "fleshbone.message.blood_drawn", filled);
        }, body -> body.vitals.bloodVolume >= 3.6f);
    }

    @FunctionalInterface
    private interface StackApplier {
        MedicalAction.Result apply(Body body, float quality, net.minecraft.world.item.ItemStack stack, net.minecraft.server.level.ServerPlayer patient);
    }

    /** Whole-body action that needs the item stack (blood bags). */
    private record StackAction(int useTicks, StackApplier applier, java.util.function.Predicate<Body> allowed) implements MedicalAction {
        @Override public Target target() { return Target.BODY; }

        @Override public Minigame minigame() { return Minigame.INFUSION; }

        @Override public ItemUseAnimation animation() { return ItemUseAnimation.BLOCK; }

        @Override public BodyPart autoTarget(Body body) { return null; }

        @Override public boolean canApply(Body body, BodyPart part) { return allowed.test(body); }

        @Override
        public Result apply(Body body, BodyPart part, float quality, int amount, RandomGenerator rng) {
            return Result.fail("fleshbone.message.cannot_apply");
        }

        @Override
        public Result apply(Body body, BodyPart part, float quality, int amount, RandomGenerator rng,
                            net.minecraft.world.item.ItemStack stack, net.minecraft.server.level.ServerPlayer patient) {
            return applier.apply(body, quality, stack, patient);
        }
    }

    // ------------------------------------------------------------------------------------------------ improvised

    /** Flint and steel or a fire charge on a bleeding wound. */
    public static MedicalAction cauterize() {
        return new PartAction(40, ItemUseAnimation.BRUSH, Treatments::mostBleeding,
                (part, s) -> !s.amputated && s.bleed > 0f && s.skin < 100f,
                (body, part, quality, amount, rng) -> {
                    Treatments.cauterize(body, part, quality);
                    return new MedicalAction.Result(true, 1, "fleshbone.message.cauterized");
                }, MedicalAction.Minigame.CAUTERIZE, 0.4f);
    }

    /** Honey on a wound: a mild antiseptic. Gives the bottle back. */
    public static MedicalAction honey() {
        return new PartAction(40, ItemUseAnimation.BRUSH, Treatments::disinfectTarget,
                (part, s) -> Treatments.canDisinfect(s),
                (body, part, quality, amount, rng) -> {
                    PartState s = body.part(part);
                    s.disinfected = Math.max(s.disinfected, 120f);
                    s.contamination *= 0.5f;
                    body.derived.compute(body);
                    return new MedicalAction.Result(true, 1, null, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE));
                }, none(), 0.8f);
    }

    /** Cobwebs packed into a bleeding wound. */
    public static MedicalAction cobweb() {
        return new PartAction(30, ItemUseAnimation.BRUSH, Treatments::mostBleeding,
                (part, s) -> !s.amputated && s.bleed > 0f && s.skin < 100f,
                (body, part, quality, amount, rng) -> {
                    Treatments.cobweb(body, part);
                    return MedicalAction.Result.ok(1);
                }, none(), 1f);
    }

    /** Paper as a makeshift dressing: worse than a rag. */
    public static MedicalAction paper() {
        return new PartAction(50, ItemUseAnimation.BRUSH, Treatments::bandageTarget,
                (part, s) -> Treatments.canBandage(s),
                (body, part, quality, amount, rng) -> {
                    Treatments.bandage(body, part, BandageType.RAG, quality * 0.6f);
                    return MedicalAction.Result.ok(1);
                }, none(), 0.6f);
    }

    /** A stick as an improvised splint: it holds, but not as well. */
    public static MedicalAction stickSplint() {
        return new PartAction(80, ItemUseAnimation.BRUSH, Treatments::splintTarget,
                (part, s) -> Treatments.canSplint(s),
                (body, part, quality, amount, rng) -> {
                    Treatments.splint(body, part, 0.5f * quality);
                    return MedicalAction.Result.ok(1);
                }, MedicalAction.Minigame.SPLINT, 0.8f);
    }

    /** Snow or ice as a cold compress. */
    public static MedicalAction coldCompress(float seconds) {
        return new PartAction(30, ItemUseAnimation.BRUSH, Treatments::mostPainful,
                (part, s) -> Treatments.canChill(s),
                (body, part, quality, amount, rng) -> {
                    Treatments.chill(body, part, seconds);
                    return MedicalAction.Result.ok(1);
                }, none(), 1f);
    }

    /** Milk as an antidote to venom and food poisoning. Gives the bucket back. */
    public static MedicalAction milk() {
        return new BodyAction(32, ItemUseAnimation.DRINK, (body, part, quality, amount, rng) -> {
            Treatments.antidote(body);
            return new MedicalAction.Result(true, 1, null, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BUCKET));
        }, body -> body.chem.venom > 1f || body.chem.foodPoisoning + body.chem.foodPoisoningDepot > 1f, none());
    }

    private static final java.util.Map<net.minecraft.world.item.Item, MedicalAction> IMPROVISED = new java.util.HashMap<>();

    /** The medical action of a stack: a medical item, or a vanilla item used as an improvised remedy. */
    public static MedicalAction of(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof MedicalItem item) return item.action();
        if (IMPROVISED.isEmpty()) {
            IMPROVISED.put(net.minecraft.world.item.Items.FLINT_AND_STEEL, cauterize());
            IMPROVISED.put(net.minecraft.world.item.Items.FIRE_CHARGE, cauterize());
            IMPROVISED.put(net.minecraft.world.item.Items.HONEY_BOTTLE, honey());
            IMPROVISED.put(net.minecraft.world.item.Items.COBWEB, cobweb());
            IMPROVISED.put(net.minecraft.world.item.Items.PAPER, paper());
            IMPROVISED.put(net.minecraft.world.item.Items.STICK, stickSplint());
            IMPROVISED.put(net.minecraft.world.item.Items.SNOWBALL, coldCompress(60f));
            IMPROVISED.put(net.minecraft.world.item.Items.ICE, coldCompress(120f));
            IMPROVISED.put(net.minecraft.world.item.Items.PACKED_ICE, coldCompress(150f));
            IMPROVISED.put(net.minecraft.world.item.Items.BLUE_ICE, coldCompress(180f));
            IMPROVISED.put(net.minecraft.world.item.Items.MILK_BUCKET, milk());
        }
        return IMPROVISED.get(stack.getItem());
    }

    public static MedicalAction painkillers() {
        return new BodyAction(32, ItemUseAnimation.EAT, (body, part, quality, amount, rng) -> {
            Treatments.takeAnalgesic(body, 45f * dose());
            return MedicalAction.Result.ok(1);
        });
    }

    public static MedicalAction morphine() {
        return new BodyAction(30, ItemUseAnimation.SPEAR, (body, part, quality, amount, rng) -> {
            Treatments.injectOpiate(body, 35f * dose());
            return MedicalAction.Result.ok(1);
        });
    }
}
