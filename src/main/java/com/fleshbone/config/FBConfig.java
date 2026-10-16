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
    public static final ModConfigSpec.BooleanValue INFECTIONS;
    public static final ModConfigSpec.BooleanValue THIRST;
    public static final ModConfigSpec.BooleanValue TEMPERATURE;
    public static final ModConfigSpec.BooleanValue ENERGY;
    public static final ModConfigSpec.BooleanValue STAMINA;
    public static final ModConfigSpec.BooleanValue MOOD;
    public static final ModConfigSpec.BooleanValue OPIOID_DEPENDENCE;

    static {
        SERVER.push("general");
        SIMULATION_SPEED = SERVER
                .comment("Multiplier applied to every body process (bleeding, healing, needs...). 1.0 = real time like Casualties: Unknown.")
                .defineInRange("simulationSpeed", 1.0, 0.1, 10.0);
        PAUSE_IN_CREATIVE = SERVER
                .comment("Freeze the body simulation for players in creative or spectator mode.")
                .define("pauseInCreative", true);
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
        THIRST = SERVER.comment("Thirst: drink water, or dehydrate.").define("thirst", true);
        TEMPERATURE = SERVER.comment("Body temperature follows the weather, biomes, heat sources and clothing.").define("temperature", true);
        ENERGY = SERVER.comment("Sleepiness: sleep, or pass out from exhaustion.").define("energy", true);
        STAMINA = SERVER.comment("Stamina: sprinting, swimming, fighting and mining tire you out.").define("stamina", true);
        MOOD = SERVER.comment("Mood and trauma.").define("mood", true);
        OPIOID_DEPENDENCE = SERVER.comment("Repeated opioid use builds tolerance, dependence and withdrawal.").define("opioidDependence", true);
        SERVER.pop();
    }

    public static final ModConfigSpec SERVER_SPEC = SERVER.build();

    // ---------------------------------------------------------------- CLIENT
    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue HUD_SCALE;
    public static final ModConfigSpec.BooleanValue SHOW_BODY_DOLL;
    public static final ModConfigSpec.BooleanValue SHOW_MOODLES;
    public static final ModConfigSpec.IntValue MAX_MOODLES;
    public static final ModConfigSpec.BooleanValue ALWAYS_SHOW_VITALS;
    public static final ModConfigSpec.DoubleValue SCREEN_EFFECTS;
    public static final ModConfigSpec.BooleanValue CAMERA_EFFECTS;
    public static final ModConfigSpec.BooleanValue BLUR;
    public static final ModConfigSpec.BooleanValue HEARTBEAT;
    public static final ModConfigSpec.BooleanValue PANEL_PAUSES_GAME;
    public static final ModConfigSpec.BooleanValue MINIGAMES;

    static {
        CLIENT.push("hud");
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
        ALWAYS_SHOW_VITALS = CLIENT
                .comment("Show exact vital signs (blood pressure, heart rate, SpO2...) without a vitals monitor.")
                .define("alwaysShowVitals", false);
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
