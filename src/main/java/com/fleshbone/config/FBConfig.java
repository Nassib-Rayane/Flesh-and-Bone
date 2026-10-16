package com.fleshbone.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * SERVER config holds gameplay values (per world, synced to clients); CLIENT config holds HUD and visual preferences.
 */
public final class FBConfig {
    private FBConfig() {}

    // ---------------------------------------------------------------- SERVER
    private static final ModConfigSpec.Builder SERVER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue SIMULATION_SPEED;
    public static final ModConfigSpec.BooleanValue PAUSE_IN_CREATIVE;
    public static final ModConfigSpec.DoubleValue DAMAGE_SCALE;
    public static final ModConfigSpec.BooleanValue PER_PART_ARMOR;
    public static final ModConfigSpec.DoubleValue HEALING_SPEED;
    public static final ModConfigSpec.DoubleValue FRACTURE_HEAL_MINUTES;
    public static final ModConfigSpec.DoubleValue DISLOCATION_HEAL_MINUTES;
    public static final ModConfigSpec.BooleanValue HEALTH_CHIP;
    public static final ModConfigSpec.BooleanValue HUNGER;
    public static final ModConfigSpec.DoubleValue HUNGER_SPEED;
    public static final ModConfigSpec.DoubleValue THIRST_SPEED;
    public static final ModConfigSpec.DoubleValue ENERGY_SPEED;
    public static final ModConfigSpec.DoubleValue INFECTION_SPEED;
    public static final ModConfigSpec.BooleanValue ENCUMBRANCE;
    public static final ModConfigSpec.DoubleValue CARRY_CAPACITY;
    public static final ModConfigSpec.DoubleValue ITEM_WEIGHT_SCALE;
    public static final ModConfigSpec.BooleanValue KEEP_SKILLS_ON_DEATH;
    public static final ModConfigSpec.BooleanValue LAST_STAND;
    public static final ModConfigSpec.BooleanValue BRAIN_EPISODES;
    public static final ModConfigSpec.BooleanValue INFECTIONS;
    public static final ModConfigSpec.BooleanValue THIRST;
    public static final ModConfigSpec.BooleanValue TEMPERATURE;
    public static final ModConfigSpec.BooleanValue ENERGY;
    public static final ModConfigSpec.BooleanValue STAMINA;
    public static final ModConfigSpec.BooleanValue MOOD;
    public static final ModConfigSpec.BooleanValue OPIOID_DEPENDENCE;
    public static final ModConfigSpec.DoubleValue DRUG_STRENGTH;
    public static final ModConfigSpec.DoubleValue DEPENDENCE_SPEED;

    static {
        SERVER.push("general");
        SIMULATION_SPEED = SERVER
                .comment("Multiplier applied to every body process (bleeding, healing, needs...). 1.0 = real time like Casualties: Unknown.")
                .defineInRange("simulationSpeed", 1.0, 0.1, 10.0);
        PAUSE_IN_CREATIVE = SERVER
                .comment("Freeze the body simulation for players in creative or spectator mode.")
                .define("pauseInCreative", true);
        HEALTH_CHIP = SERVER
                .comment("Everyone has a health chip: exact values (heart rate, pressure, temperature, weight...) are always shown.",
                        "Set to false for a hardcore game where only a vitals monitor gives numbers and the body has to be read from symptoms.")
                .define("healthChip", true);
        SERVER.pop();

        SERVER.push("needs");
        HUNGER = SERVER
                .comment("Casualties: Unknown hunger (0-125) and body weight replace the vanilla food bar. If false, vanilla hunger works as usual.")
                .define("hunger", true);
        HUNGER_SPEED = SERVER
                .comment("Multiplier for hunger and weight loss. 1.0 = 2.6 points a minute like Casualties: Unknown.")
                .defineInRange("hungerSpeed", 1.0, 0.0, 10.0);
        THIRST_SPEED = SERVER
                .comment("Multiplier for thirst. 1.0 = about 3.5 points a minute at rest.")
                .defineInRange("thirstSpeed", 1.0, 0.0, 10.0);
        ENERGY_SPEED = SERVER
                .comment("Multiplier for sleepiness and sleep recovery. 1.0 = a full bar lasts about 24 minutes awake (Casualties: Unknown).")
                .defineInRange("energySpeed", 1.0, 0.0, 10.0);
        SERVER.pop();

        SERVER.push("skills");
        KEEP_SKILLS_ON_DEATH = SERVER
                .comment("Strength, resilience and intelligence survive death.")
                .define("keepSkillsOnDeath", true);
        SERVER.pop();

        SERVER.push("encumbrance");
        ENCUMBRANCE = SERVER
                .comment("Carried weight slows you down and tires you out past your capacity.")
                .define("encumbrance", true);
        CARRY_CAPACITY = SERVER
                .comment("Kilograms an average body (strength and resilience 10, healthy weight, fed) carries without penalty.")
                .defineInRange("carryCapacity", 25.0, 1.0, 1000.0);
        ITEM_WEIGHT_SCALE = SERVER
                .comment("Multiplier for every item weight (see the fleshbone:item_weights data map).")
                .defineInRange("itemWeightScale", 1.0, 0.0, 10.0);
        SERVER.pop();

        SERVER.push("damage");
        DAMAGE_SCALE = SERVER
                .comment("Injury severity points per point of vanilla damage (a zombie hit of 3 damage gives severity 15 by default).")
                .defineInRange("damageScale", 5.0, 0.1, 50.0);
        PER_PART_ARMOR = SERVER
                .comment("Armor only protects the body parts it covers (helmet = head, boots = shins...). If false, vanilla armor reduction applies to every hit.")
                .define("perPartArmor", true);
        SERVER.pop();

        SERVER.push("healing");
        HEALING_SPEED = SERVER
                .comment("Multiplier for natural skin and muscle regeneration.")
                .defineInRange("healingSpeed", 1.0, 0.0, 20.0);
        FRACTURE_HEAL_MINUTES = SERVER
                .comment("Real-time minutes for an untreated fracture to heal (a splint makes it 2.5x faster).")
                .defineInRange("fractureHealMinutes", 30.0, 0.5, 600.0);
        DISLOCATION_HEAL_MINUTES = SERVER
                .comment("Real-time minutes for an untreated dislocation to heal on its own.")
                .defineInRange("dislocationHealMinutes", 12.0, 0.5, 600.0);
        SERVER.pop();

        SERVER.push("systems");
        INFECTIONS = SERVER.comment("Wounds can get infected, infections spread and cause sepsis.").define("infections", true);
        INFECTION_SPEED = SERVER
                .comment("Multiplier for infection growth. 1.0 = 7.2 % a minute at 100 % immunity (Casualties: Unknown).")
                .defineInRange("infectionSpeed", 1.0, 0.0, 10.0);
        LAST_STAND = SERVER.comment("A dying body may get one last stand per life, likelier in a good mood.").define("lastStand", true);
        BRAIN_EPISODES = SERVER.comment("A damaged brain misfires: hallucinations, mirrored controls, dropped items...").define("brainEpisodes", true);
        THIRST = SERVER.comment("Thirst: drink water, or dehydrate.").define("thirst", true);
        TEMPERATURE = SERVER.comment("Body temperature follows the weather, biomes, heat sources and clothing.").define("temperature", true);
        ENERGY = SERVER.comment("Sleepiness: sleep, or pass out from exhaustion.").define("energy", true);
        STAMINA = SERVER.comment("Stamina: sprinting, swimming, fighting and mining tire you out.").define("stamina", true);
        MOOD = SERVER.comment("Mood and trauma.").define("mood", true);
        OPIOID_DEPENDENCE = SERVER.comment("Repeated opioid (and caffeine) use builds tolerance, dependence and withdrawal.").define("opioidDependence", true);
        DRUG_STRENGTH = SERVER
                .comment("Multiplier for every drug dose (morphine, painkillers, epinephrine, caffeine...), effects and side effects alike.")
                .defineInRange("drugStrength", 1.0, 0.1, 5.0);
        DEPENDENCE_SPEED = SERVER
                .comment("Multiplier for how fast tolerance and dependence build up. 1.0 = three or four morphine shots within an hour are enough to be hooked.")
                .defineInRange("dependenceSpeed", 1.0, 0.0, 10.0);
        SERVER.pop();
    }

    public static final ModConfigSpec SERVER_SPEC = SERVER.build();

    // ---------------------------------------------------------------- CLIENT
    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();

    public enum UniHealthMode { FULL, COMPACT, HIDDEN }

    public static final ModConfigSpec.EnumValue<UniHealthMode> UNI_HEALTH;
    public static final ModConfigSpec.DoubleValue UNI_HEALTH_SCALE;
    public static final ModConfigSpec.DoubleValue HUD_SCALE;
    public static final ModConfigSpec.BooleanValue SHOW_BODY_DOLL;
    public static final ModConfigSpec.BooleanValue SHOW_MOODLES;
    public static final ModConfigSpec.IntValue MAX_MOODLES;
    public static final ModConfigSpec.DoubleValue SCREEN_EFFECTS;
    public static final ModConfigSpec.BooleanValue CAMERA_EFFECTS;
    public static final ModConfigSpec.BooleanValue BLUR;
    public static final ModConfigSpec.BooleanValue HEARTBEAT;
    public static final ModConfigSpec.BooleanValue PANEL_PAUSES_GAME;
    public static final ModConfigSpec.BooleanValue MINIGAMES;

    static {
        CLIENT.push("hud");
        UNI_HEALTH = CLIENT
                .comment("UNI-HEALTH readout in the top-left corner: FULL (every value and a limb), COMPACT (heart, pressure, oxygen, blood, temperature) or HIDDEN.",
                        "The U key cycles through the modes in game.")
                .defineEnum("uniHealth", UniHealthMode.FULL);
        UNI_HEALTH_SCALE = CLIENT
                .comment("Scale of the UNI-HEALTH readout on the HUD.")
                .defineInRange("uniHealthScale", 0.75, 0.4, 2.0);
        HUD_SCALE = CLIENT
                .comment("Scale of the body silhouette in the corner of the screen.")
                .defineInRange("hudScale", 1.25, 0.5, 4.0);
        SHOW_BODY_DOLL = CLIENT
                .comment("Show the body silhouette in the bottom-left corner.")
                .define("showBodyDoll", true);
        SHOW_MOODLES = CLIENT
                .comment("Show status icons (moodles) on the right side of the screen.")
                .define("showMoodles", true);
        MAX_MOODLES = CLIENT
                .comment("Maximum number of moodles shown on the HUD (the health panel lists them all).")
                .defineInRange("maxMoodles", 10, 1, 40);
        PANEL_PAUSES_GAME = CLIENT
                .comment("Pause single-player while the health panel is open. Casualties: Unknown keeps time running.")
                .define("panelPausesGame", false);
        MINIGAMES = CLIENT
                .comment("Treat wounds through hands-on minigames from the health panel. If false, treatments simply take time.")
                .define("minigames", true);
        CLIENT.pop();

        CLIENT.push("effects");
        SCREEN_EFFECTS = CLIENT
                .comment("Strength of pain, blood loss and fainting screen overlays (0 disables them).")
                .defineInRange("screenEffects", 1.0, 0.0, 1.5);
        CAMERA_EFFECTS = CLIENT
                .comment("Camera sway and shaking when confused, concussed or in shock. Turn off if it makes you motion sick.")
                .define("cameraEffects", true);
        BLUR = CLIENT
                .comment("Blur the screen when about to faint.")
                .define("blur", true);
        HEARTBEAT = CLIENT
                .comment("Hear your heartbeat when it races or when unconscious.")
                .define("heartbeat", true);
        CLIENT.pop();
    }

    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();
}
