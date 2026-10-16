package com.fleshbone.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.fleshbone.FleshBone;
import com.fleshbone.attach.FBAttachments;
import com.fleshbone.damage.FBDamageTypes;
import com.fleshbone.event.BodyTicker;
import com.fleshbone.item.FBItems;
import com.fleshbone.item.TreatmentService;
import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.DeathCause;
import com.fleshbone.sim.PartState;
import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** In-game integration tests: {@code gradlew runGameTestServer} or {@code /test runall} in a dev client. */
@EventBusSubscriber(modid = FleshBone.MODID)
public final class FBGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, FleshBone.MODID);
    private static final List<DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>> TESTS = new ArrayList<>();
    /** Freshly joined players ignore damage until their client has loaded; wait for things to settle. */
    private static final int SETTLE_TICKS = 5;

    static {
        test("fall_hurts_legs_not_hearts", FBGameTests::fallHurtsLegsNotHearts);
        test("zombie_claws_cut_one_part", FBGameTests::zombieClawsCutOnePart);
        test("helmet_protects_only_the_head", FBGameTests::helmetProtectsOnlyTheHead);
        test("vanilla_healing_regrows_tissue", FBGameTests::vanillaHealingRegrowsTissue);
        test("bleeding_out_kills_with_message", FBGameTests::bleedingOutKillsWithMessage);
        test("totem_pulls_back_from_death", FBGameTests::totemPullsBackFromDeath);
        test("bandage_slows_bleeding_and_wears_out", FBGameTests::bandageSlowsBleedingAndWearsOut);
        test("medics_need_to_be_close", FBGameTests::medicsNeedToBeClose);
        test("recipes_and_loot_load", FBGameTests::recipesAndLootLoad);
        test("blood_bag_moves_blood_between_players", FBGameTests::bloodBagMovesBloodBetweenPlayers);
        test("cpr_keeps_blood_flowing", FBGameTests::cprKeepsBloodFlowing);
        test("shocking_a_healthy_heart_is_dangerous", FBGameTests::shockingAHealthyHeartIsDangerous);
        test("data_maps_load_and_campfires_warm", FBGameTests::dataMapsLoadAndCampfiresWarm);
        test("clerics_sell_medical_supplies", FBGameTests::clericsSellMedicalSupplies);
        test("meals_feed_hunger_and_weight", FBGameTests::mealsFeedHungerAndWeight);
        test("carried_weight_counts_the_inventory", FBGameTests::carriedWeightCountsTheInventory);
        test("push_ups_train_strength", FBGameTests::pushUpsTrainStrength);
        test("sleeping_on_the_ground_and_pain_wakes", FBGameTests::sleepingOnTheGroundAndPainWakes);
        test("unconscious_medic_cannot_treat", FBGameTests::unconsciousMedicCannotTreat);
        test("bare_hand_glass_cuts", FBGameTests::bareHandGlassCuts);
        test("dry_bushes_scratch_legs", FBGameTests::dryBushesScratchLegs);
        test("poison_effect_sickens", FBGameTests::poisonEffectSickens);
        test("doctors_take_the_cabinet_and_sell_supplies", FBGameTests::doctorsTakeTheCabinetAndSellSupplies);
        test("flint_and_steel_cauterizes", FBGameTests::flintAndSteelCauterizes);
        test("botched_procedures_harm_the_patient", FBGameTests::botchedProceduresHarmThePatient);
    }

    private FBGameTests() {}

    private static void test(String name, Consumer<GameTestHelper> function) {
        TESTS.add(FUNCTIONS.register(name, () -> function));
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(FleshBone.id("default"));
        for (DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> holder : TESTS) {
            event.registerTest(holder.getId(), new FunctionGameTestInstance(holder.getKey(),
                    new TestData<>(environment, Identifier.withDefaultNamespace("empty"), 100, 0, true)));
        }
    }

    // ------------------------------------------------------------------------------------------------ tests

    private static void fallHurtsLegsNotHearts(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            float health = player.getHealth();
            player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().fall(), 7f);
            Body body = player.getData(FBAttachments.BODY);
            helper.assertTrue(player.getHealth() == health, "vanilla health must not move");
            helper.assertTrue(body.part(BodyPart.SHIN_L).muscle < 100f && body.part(BodyPart.SHIN_R).muscle < 100f, "both shins take the landing");
            helper.assertTrue(body.part(BodyPart.HEAD).muscle == 100f, "a 10-block fall does not reach the head");
            remove(player);
            helper.succeed();
        });
    }

    private static void zombieClawsCutOnePart(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 1, 1);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(zombie), 3f);
            Body body = player.getData(FBAttachments.BODY);
            int injured = 0;
            PartState hit = null;
            for (BodyPart part : BodyPart.VALUES) {
                PartState s = body.part(part);
                if (s.skin < 100f || s.muscle < 100f) {
                    injured++;
                    hit = s;
                }
            }
            helper.assertTrue(injured == 1, "a single claw swipe hurts exactly one part, got " + injured);
            helper.assertTrue(hit != null && hit.bleed > 0f, "zombie claws cut and make it bleed");
            helper.assertTrue(hit.contamination >= 30f, "rotting claws contaminate the wound");
            zombie.discard();
            remove(player);
            helper.succeed();
        });
    }

    private static void helmetProtectsOnlyTheHead(GameTestHelper helper) {
        ServerPlayer bare = survivalPlayer(helper);
        ServerPlayer helmeted = survivalPlayer(helper);
        helmeted.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            var stalactite = helper.getLevel().damageSources().source(DamageTypes.FALLING_STALACTITE);
            bare.hurtServer(helper.getLevel(), stalactite, 6f);
            helmeted.hurtServer(helper.getLevel(), stalactite, 6f);
            float bareLoss = 100f - bare.getData(FBAttachments.BODY).part(BodyPart.HEAD).muscle;
            float helmetLoss = 100f - helmeted.getData(FBAttachments.BODY).part(BodyPart.HEAD).muscle;
            helper.assertTrue(bareLoss > 0f, "the stalactite hits the head");
            helper.assertTrue(helmetLoss < bareLoss * 0.5f, "a diamond helmet stops most of it: " + helmetLoss + " vs " + bareLoss);
            remove(bare);
            remove(helmeted);
            helper.succeed();
        });
    }

    private static void vanillaHealingRegrowsTissue(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            Body body = player.getData(FBAttachments.BODY);
            body.part(BodyPart.THIGH_L).muscle = 40f;
            player.heal(4f);
            helper.assertTrue(body.part(BodyPart.THIGH_L).muscle > 40f, "healing goes into the body");
            remove(player);
            helper.succeed();
        });
    }

    private static void bleedingOutKillsWithMessage(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            player.getData(FBAttachments.BODY).vitals.bloodVolume = 0.5f;
            BodyTicker.tickPlayer(player);
            helper.assertTrue(player.isDeadOrDying(), "exsanguination kills");
            var source = player.getLastDamageSource();
            helper.assertTrue(source != null && source.is(FBDamageTypes.key(DeathCause.HEMORRHAGE)), "killed by the hemorrhage damage type");
            boolean rightMessage = source.getLocalizedDeathMessage(player).getContents() instanceof TranslatableContents contents
                    && contents.getKey().equals("death.attack.fleshbone.hemorrhage");
            helper.assertTrue(rightMessage, "death message says the player bled out");
            remove(player);
            helper.succeed();
        });
    }

    private static void totemPullsBackFromDeath(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            Body body = player.getData(FBAttachments.BODY);
            body.vitals.brainHealth = 0f;
            BodyTicker.tickPlayer(player);
            helper.assertTrue(player.isAlive(), "the totem saves the player");
            helper.assertTrue(body.vitals.brainHealth >= 60f, "and the body is stabilized");
            remove(player);
            helper.succeed();
        });
    }

    private static void bandageSlowsBleedingAndWearsOut(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            Body body = player.getData(FBAttachments.BODY);
            body.part(BodyPart.THIGH_R).skin = 30f;
            body.part(BodyPart.THIGH_R).bleed = 40f;
            body.derived.compute(body);
            float before = body.derived.partBleed[BodyPart.THIGH_R.ordinal()];
            ItemStack bandage = new ItemStack(FBItems.BANDAGE.get());
            boolean ok = TreatmentService.treat(player, player, bandage, BodyPart.THIGH_R, 0.85f);
            float after = body.derived.partBleed[BodyPart.THIGH_R.ordinal()];
            helper.assertTrue(ok, "the bandage can be applied");
            helper.assertTrue(after < before * 0.4f, "bleeding drops a lot: " + before + " -> " + after);
            helper.assertTrue(bandage.getDamageValue() == 4, "one dressing uses a third of the roll");
            remove(player);
            helper.succeed();
        });
    }

    private static void medicsNeedToBeClose(GameTestHelper helper) {
        ServerPlayer medic = survivalPlayer(helper);
        ServerPlayer patient = survivalPlayer(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            patient.snapTo(medic.getX() + 10, medic.getY(), medic.getZ(), 0f, 0f);
            Body body = patient.getData(FBAttachments.BODY);
            body.part(BodyPart.SHIN_L).fracture = 1f;
            boolean ok = TreatmentService.treat(medic, patient, new ItemStack(FBItems.SPLINT.get()), BodyPart.SHIN_L, 1f);
            helper.assertFalse(ok, "10 blocks away is too far to splint someone");
            patient.snapTo(medic.getX() + 2, medic.getY(), medic.getZ(), 0f, 0f);
            ok = TreatmentService.treat(medic, patient, new ItemStack(FBItems.SPLINT.get()), BodyPart.SHIN_L, 1f);
            helper.assertTrue(ok && body.part(BodyPart.SHIN_L).splinted, "2 blocks away works");
            remove(medic);
            remove(patient);
            helper.succeed();
        });
    }

    private static void recipesAndLootLoad(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (String recipe : List.of("bandage", "antiseptic", "morphine", "sterile_bandage_from_campfire", "tweezers")) {
            var key = net.minecraft.resources.ResourceKey.create(Registries.RECIPE, FleshBone.id(recipe));
            helper.assertTrue(server.getRecipeManager().byKey(key).isPresent(), "recipe " + recipe + " loaded");
        }
        var loot = net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE, FleshBone.id("chests/medical_supplies"));
        helper.assertTrue(server.reloadableRegistries().getLootTable(loot) != net.minecraft.world.level.storage.loot.LootTable.EMPTY,
                "medical supplies loot table loaded");
        helper.succeed();
    }

    private static void bloodBagMovesBloodBetweenPlayers(GameTestHelper helper) {
        ServerPlayer donor = survivalPlayer(helper);
        ServerPlayer recipient = survivalPlayer(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            Body donorBody = donor.getData(FBAttachments.BODY);
            Body recipientBody = recipient.getData(FBAttachments.BODY);
            donorBody.vitals.bloodType = com.fleshbone.sim.BloodType.O_NEG;
            recipientBody.vitals.bloodType = com.fleshbone.sim.BloodType.AB_POS;
            recipientBody.vitals.bloodVolume = 4f;
            recipient.snapTo(donor.getX() + 1, donor.getY(), donor.getZ(), 0f, 0f);
            donor.getInventory().clearContent();
            ItemStack empty = new ItemStack(FBItems.EMPTY_BLOOD_BAG.get());
            helper.assertTrue(TreatmentService.treat(donor, donor, empty, null, 1f), "drawing your own blood works");
            helper.assertTrue(Math.abs(donorBody.vitals.bloodVolume - 4.55f) < 0.01f, "the donor gave half a litre");
            ItemStack bag = ItemStack.EMPTY;
            for (int slot = 0; slot < donor.getInventory().getContainerSize(); slot++) {
                if (donor.getInventory().getItem(slot).is(FBItems.BLOOD_BAG.get())) bag = donor.getInventory().getItem(slot);
            }
            helper.assertTrue(!bag.isEmpty(), "the medic receives a filled blood bag");
            helper.assertTrue(bag.get(com.fleshbone.item.FBDataComponents.BLOOD_TYPE.get()) == com.fleshbone.sim.BloodType.O_NEG,
                    "the bag carries the donor's blood group");
            helper.assertTrue(TreatmentService.treat(donor, recipient, bag, null, 1f), "O- can go to anyone");
            helper.assertTrue(Math.abs(recipientBody.vitals.bloodVolume - 4.45f) < 0.01f, "the recipient got the blood");
            remove(donor);
            remove(recipient);
            helper.succeed();
        });
    }

    private static void cprKeepsBloodFlowing(GameTestHelper helper) {
        ServerPlayer medic = survivalPlayer(helper);
        ServerPlayer patient = survivalPlayer(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            patient.snapTo(medic.getX() + 1, medic.getY(), medic.getZ(), 0f, 0f);
            Body body = patient.getData(FBAttachments.BODY);
            com.fleshbone.sim.system.CirculationSystem.stopHeart(body);
            TreatmentService.cpr(medic, patient, 0.9f);
            helper.assertTrue(body.vitals.cprPerfusion >= 0.9f || !body.vitals.cardiacArrest, "compressions move the blood");
            remove(medic);
            remove(patient);
            helper.succeed();
        });
    }

    private static void shockingAHealthyHeartIsDangerous(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            ItemStack defibrillator = new ItemStack(FBItems.DEFIBRILLATOR.get());
            TreatmentService.treat(player, player, defibrillator, null, 1f);
            Body body = player.getData(FBAttachments.BODY);
            helper.assertTrue(body.vitals.fibrillation >= 40f, "a shock on a healthy heart makes it fibrillate");
            helper.assertTrue(defibrillator.getDamageValue() == 1, "each shock uses a charge");
            remove(player);
            helper.succeed();
        });
    }

    private static void dataMapsLoadAndCampfiresWarm(GameTestHelper helper) {
        helper.assertTrue(new ItemStack(Items.POTION).getData(com.fleshbone.data.FBDataMaps.DRINKS) != null, "drinks data map loaded");
        helper.assertTrue(new ItemStack(Items.ROTTEN_FLESH).getData(com.fleshbone.data.FBDataMaps.NUTRITION) != null, "nutrition loaded");
        helper.assertTrue(new ItemStack(Items.ANVIL).getData(com.fleshbone.data.FBDataMaps.ITEM_WEIGHTS) != null, "item weights loaded");
        helper.assertTrue(new ItemStack(Items.LEATHER_CHESTPLATE).getData(com.fleshbone.data.FBDataMaps.INSULATION) != null, "insulation loaded");
        net.minecraft.core.BlockPos fire = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        com.fleshbone.attach.BodyRuntime runtime = new com.fleshbone.attach.BodyRuntime();
        com.fleshbone.event.EnvironmentSampler.heatAround(helper.getLevel(), fire.above(), runtime);
        float before = runtime.heatStrength;
        helper.setBlock(new net.minecraft.core.BlockPos(1, 1, 1), net.minecraft.world.level.block.Blocks.CAMPFIRE);
        com.fleshbone.event.EnvironmentSampler.heatAround(helper.getLevel(), fire.above(), runtime);
        helper.assertTrue(runtime.heatStrength > before + 0.3f && runtime.heatTarget > 30f,
                "a lit campfire warms the air around it: " + before + " -> " + runtime.heatStrength + " toward " + runtime.heatTarget);
        helper.succeed();
    }

    private static void clericsSellMedicalSupplies(GameTestHelper helper) {
        var trades = helper.getLevel().registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
        var bandage = net.minecraft.resources.ResourceKey.create(Registries.VILLAGER_TRADE, FleshBone.id("cleric/1/bandage"));
        helper.assertTrue(trades.get(bandage).isPresent(), "the bandage trade is registered");
        var tag = net.minecraft.tags.TagKey.create(Registries.VILLAGER_TRADE, net.minecraft.resources.Identifier.withDefaultNamespace("cleric/level_1"));
        boolean tagged = trades.get(tag).map(set -> set.stream().anyMatch(h -> h.is(bandage))).orElse(false);
        helper.assertTrue(tagged, "and offered by level 1 clerics");
        helper.succeed();
    }

    /** A steak goes into the C:U hunger (5 per food point + 1.5 per saturation point) and onto the scales. */
    private static void mealsFeedHungerAndWeight(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            Body body = player.getData(FBAttachments.BODY);
            body.needs.hunger = 40f;
            float weight = body.needs.weight;
            ItemStack steak = new ItemStack(Items.COOKED_BEEF);
            ItemStack left = steak.finishUsingItem(helper.getLevel(), player);
            net.neoforged.neoforge.event.EventHooks.onItemUseFinish(player, new ItemStack(Items.COOKED_BEEF), 0, left);
            helper.assertTrue(body.needs.hunger > 95f && body.needs.hunger < 101f, "8 food + 12.8 saturation -> about 59 hunger: " + body.needs.hunger);
            helper.assertTrue(body.needs.weight > weight + 0.3f, "a meal puts on weight: " + weight + " -> " + body.needs.weight);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(player.getFoodData().getFoodLevel() == BodyTicker.LOCKED_FOOD, "the vanilla food bar stays locked");
                helper.assertTrue(body.needs.hunger < 101f, "the meal is not counted twice: " + body.needs.hunger);
                remove(player);
                helper.succeed();
            });
        });
    }

    /** Armor, an anvil and a stack of cobblestone weigh what they should and overload the body. */
    private static void carriedWeightCountsTheInventory(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        player.getInventory().add(new ItemStack(Items.ANVIL));
        player.getInventory().add(new ItemStack(Items.COBBLESTONE, 64));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            BodyTicker.tickPlayer(player);
            Body body = player.getData(FBAttachments.BODY);
            float expected = 25f + 0.3f + 0.6f * 6f + 64 * com.fleshbone.data.ItemWeights.BLOCK;
            helper.assertTrue(Math.abs(body.needs.carried - expected) < 0.5f, "carried " + body.needs.carried + " kg, expected " + expected);
            helper.assertTrue(body.derived.overload > 0.1f, "past 25 kg the body is overloaded: " + body.derived.overload);
            remove(player);
            helper.succeed();
        });
    }

    /** Push-ups from the health panel: strength experience, stamina spent, lying pose. */
    private static void pushUpsTrainStrength(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            player.setOnGround(true);
            com.fleshbone.effect.GameplayEffects.startExercise(player, com.fleshbone.sim.Environment.Exercise.PUSHUPS);
            for (int tick = 0; tick < 40; tick++) BodyTicker.tickPlayer(player);
            {
                Body body = player.getData(FBAttachments.BODY);
                helper.assertTrue(body.needs.exercise == com.fleshbone.sim.Environment.Exercise.PUSHUPS.ordinal(), "working out");
                helper.assertTrue(body.skills.xp(com.fleshbone.sim.Skills.Stat.STRENGTH) > 0.7f,
                        "0.5 strength XP a second: " + body.skills.xp(com.fleshbone.sim.Skills.Stat.STRENGTH));
                helper.assertTrue(body.needs.stamina < 99f, "push-ups cost stamina: " + body.needs.stamina);
                helper.assertTrue(player.getForcedPose() == net.minecraft.world.entity.Pose.SWIMMING, "lying down for push-ups");
                com.fleshbone.effect.GameplayEffects.startExercise(player, com.fleshbone.sim.Environment.Exercise.NONE);
                remove(player);
                helper.succeed();
            }
        });
    }

    /** A tired body lies down where it stands; pain wakes it up. */
    private static void sleepingOnTheGroundAndPainWakes(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            Body body = player.getData(FBAttachments.BODY);
            com.fleshbone.effect.GameplayEffects.trySleep(player);
            helper.assertTrue(!body.needs.asleep, "not tired: no sleep");
            body.needs.energy = 30f;
            com.fleshbone.effect.GameplayEffects.trySleep(player);
            helper.assertTrue(body.needs.asleep, "tired: falls asleep on the ground");
            for (int tick = 0; tick < 20; tick++) BodyTicker.tickPlayer(player);
            helper.assertTrue(body.needs.energy > 30f, "sleep restores energy: " + body.needs.energy);
            helper.assertTrue(body.derived.consciousnessCap <= 10f && body.vitals.consciousness < 100f, "asleep: consciousness sinks toward 10 %");
            body.part(BodyPart.FOREARM_L).pain = 70f;
            body.derived.compute(body);
            BodyTicker.tickPlayer(player);
            helper.assertTrue(!body.needs.asleep, "pain wakes the body up");
            remove(player);
            helper.succeed();
        });
    }

    /** Out cold, nobody bandages anything, not even themselves. */
    private static void unconsciousMedicCannotTreat(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            Body body = player.getData(FBAttachments.BODY);
            body.part(BodyPart.FOREARM_L).skin = 50f;
            body.part(BodyPart.FOREARM_L).bleed = 30f;
            body.vitals.consciousness = 10f;
            ItemStack bandage = new ItemStack(FBItems.BANDAGE.get());
            helper.assertFalse(TreatmentService.treat(player, player, bandage, BodyPart.FOREARM_L, 1f), "an unconscious body cannot treat");
            helper.assertTrue(!body.part(BodyPart.FOREARM_L).isBandaged(), "no bandage applied");
            remove(player);
            helper.succeed();
        });
    }

    /** Breaking glass with a bare fist cuts the hand. */
    private static void bareHandGlassCuts(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        net.minecraft.core.BlockPos glass = new net.minecraft.core.BlockPos(2, 1, 1);
        helper.setBlock(glass, net.minecraft.world.level.block.Blocks.GLASS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.gameMode.destroyBlock(helper.absolutePos(glass));
            Body body = player.getData(FBAttachments.BODY);
            BodyPart hand = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? BodyPart.HAND_L : BodyPart.HAND_R;
            helper.assertTrue(body.part(hand).skin < 100f, "the glass cut the hand: " + body.part(hand).skin);
            remove(player);
            helper.succeed();
        });
    }

    /** Wading through a dead bush scratches unprotected legs. */
    private static void dryBushesScratchLegs(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        helper.setBlock(new net.minecraft.core.BlockPos(1, 0, 1), net.minecraft.world.level.block.Blocks.SAND);
        helper.setBlock(new net.minecraft.core.BlockPos(1, 1, 1), net.minecraft.world.level.block.Blocks.DEAD_BUSH);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            // Pace back and forth inside the bush, as a real client moves (the server never sees its velocity)
            Vec3 start = player.position();
            // 30 rolls at 30 %: practically certain
            for (int tick = 0; tick < 300; tick++) {
                player.setPos(start.x + (tick % 2 == 0 ? -0.1 : 0.1), start.y, start.z);
                BodyTicker.tickPlayer(player);
            }
            Body body = player.getData(FBAttachments.BODY);
            float legs = body.part(BodyPart.SHIN_L).skin + body.part(BodyPart.SHIN_R).skin + body.part(BodyPart.THIGH_L).skin + body.part(BodyPart.THIGH_R).skin;
            helper.assertTrue(legs < 400f, "scratched legs: " + legs);
            remove(player);
            helper.succeed();
        });
    }

    /** The vanilla Poison effect turns the stomach. */
    private static void poisonEffectSickens(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 400, 1));
            for (int tick = 0; tick < 40; tick++) BodyTicker.tickPlayer(player);
            Body body = player.getData(FBAttachments.BODY);
            helper.assertTrue(body.needs.sickness > 0.3f, "poisoned: " + body.needs.sickness);
            remove(player);
            helper.succeed();
        });
    }

    /** The doctor profession exists, its cabinet is a job site, and doctors sell the mod's supplies. */
    private static void doctorsTakeTheCabinetAndSellSupplies(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var professions = access.lookupOrThrow(Registries.VILLAGER_PROFESSION);
        var doctor = professions.get(FleshBone.id("doctor")).orElse(null);
        helper.assertTrue(doctor != null, "the doctor profession is registered");
        var pois = access.lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE);
        var poi = pois.get(com.fleshbone.village.FBVillagers.DOCTOR_POI_KEY).orElse(null);
        helper.assertTrue(poi != null && poi.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE), "the cabinet is a job site");
        helper.assertTrue(poi.value().is(com.fleshbone.village.FBVillagers.MEDICINE_CABINET.get().defaultBlockState()), "for the cabinet block");
        net.minecraft.world.entity.npc.villager.Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 2, 1, 2);
        villager.setVillagerData(villager.getVillagerData().withProfession(doctor));
        boolean supplies = villager.getOffers().stream().anyMatch(offer ->
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()).getNamespace().equals(FleshBone.MODID)
                        || net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(offer.getCostA().getItem()).getNamespace().equals("minecraft"));
        helper.assertTrue(!villager.getOffers().isEmpty() && supplies, "a level 1 doctor has trades: " + villager.getOffers().size());
        helper.succeed();
    }

    /** Flint and steel from the health panel sears a bleeding wound shut. */
    private static void flintAndSteelCauterizes(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            Body body = player.getData(FBAttachments.BODY);
            body.part(BodyPart.THIGH_L).skin = 40f;
            body.part(BodyPart.THIGH_L).bleed = 50f;
            ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
            helper.assertTrue(TreatmentService.treat(player, player, flint, BodyPart.THIGH_L, 0.8f), "cauterized");
            helper.assertTrue(body.part(BodyPart.THIGH_L).bleed == 0f, "bleeding stopped");
            helper.assertTrue(body.part(BodyPart.THIGH_L).skin < 40f, "burnt skin");
            helper.assertTrue(flint.getDamageValue() == 1, "the flint and steel wore");
            remove(player);
            helper.succeed();
        });
    }

    /** Mistakes made during a procedure reach the patient, even when the medic gives up (and keeps the bandage). */
    private static void botchedProceduresHarmThePatient(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            Body body = player.getData(FBAttachments.BODY);
            PartState thigh = body.part(BodyPart.THIGH_L);
            thigh.skin = 60f;
            thigh.bleed = 10f;
            ItemStack bandage = new ItemStack(FBItems.BANDAGE.get());
            com.fleshbone.sim.procedure.Mistakes mistakes = new com.fleshbone.sim.procedure.Mistakes();
            mistakes.pain = 2;
            mistakes.tear = 2;
            mistakes.dirt = 2;
            float pain = thigh.pain;
            helper.assertFalse(TreatmentService.treat(player, player, bandage, BodyPart.THIGH_L, -1f, 0, mistakes.pack()), "given up");
            helper.assertTrue(bandage.getDamageValue() == 0 && !thigh.isBandaged(), "nothing used, nothing applied");
            helper.assertTrue(thigh.pain >= pain + 9f, "clumsy hands hurt: " + thigh.pain);
            helper.assertTrue(thigh.skin < 60f && thigh.bleed > 10f, "torn skin bleeds: " + thigh.skin + " / " + thigh.bleed);
            helper.assertTrue(thigh.contamination >= 10f, "dirt pushed in: " + thigh.contamination);
            com.fleshbone.sim.procedure.Mistakes dirty = new com.fleshbone.sim.procedure.Mistakes();
            dirty.dirt = 3;
            float contamination = thigh.contamination;
            helper.assertTrue(TreatmentService.treat(player, player, bandage, BodyPart.THIGH_L, 0.9f, 4, dirty.pack()), "dressed");
            helper.assertTrue(thigh.isBandaged(), "the dressing is on");
            helper.assertTrue(thigh.contamination >= contamination + 14f, "but dirty: " + thigh.contamination);
            remove(player);
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** Like {@link GameTestHelper#makeMockServerPlayerInLevel()} but in survival, so damage and the body simulation apply. */
    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "fleshbone-test"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.markClientLoaded();
        player.setGameMode(GameType.SURVIVAL);
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        player.snapTo(pos.x, pos.y, pos.z, 0f, 0f);
        return player;
    }

    private static void remove(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
