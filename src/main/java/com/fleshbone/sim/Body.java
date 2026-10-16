package com.fleshbone.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import com.fleshbone.sim.injury.Injury;
import com.fleshbone.sim.injury.InjuryApplier;
import com.fleshbone.sim.system.BleedingSystem;
import com.fleshbone.sim.system.BrainSystem;
import com.fleshbone.sim.system.ChemistrySystem;
import com.fleshbone.sim.system.CirculationSystem;
import com.fleshbone.sim.system.ConsciousnessSystem;
import com.fleshbone.sim.system.HealingSystem;
import com.fleshbone.sim.system.HygieneSystem;
import com.fleshbone.sim.system.InfectionSystem;
import com.fleshbone.sim.system.MetabolismSystem;
import com.fleshbone.sim.system.MindSystem;
import com.fleshbone.sim.system.PainSystem;
import com.fleshbone.sim.system.RespirationSystem;
import com.fleshbone.sim.system.TemperatureSystem;

/**
 * The whole simulated body. Pure Java: the game feeds it an {@link Environment} every tick and reads back its state,
 * derived values and events.
 */
public final class Body {
    public static final int DATA_VERSION = 1;

    private final PartState[] parts = new PartState[BodyPart.COUNT];
    public final Vitals vitals = new Vitals();
    public final Needs needs = new Needs();
    public final Mind mind = new Mind();
    public final Chemistry chem = new Chemistry();
    public final Derived derived = new Derived();
    /** Vanilla food exhaustion owed by healing and blood regeneration, drained by the game each tick. */
    public float exhaustionDebt;
    private final List<BodyEvent> events = new ArrayList<>();

    public Body() {
        for (int i = 0; i < parts.length; i++) parts[i] = new PartState();
        derived.compute(this);
    }

    public static Body create(RandomGenerator rng) {
        Body body = new Body();
        body.vitals.bloodType = BloodType.random(rng);
        return body;
    }

    public PartState part(BodyPart part) {
        return parts[part.ordinal()];
    }

    /** True if a tourniquet sits on this part or on any part between it and the torso. */
    public boolean isTourniquetted(BodyPart part) {
        for (BodyPart p = part; p != null; p = p.parent()) {
            if (parts[p.ordinal()].tourniquet) return true;
        }
        return false;
    }

    public void tick(Environment env, SimSettings settings, RandomGenerator rng) {
        float dt = env.dt;
        if (dt <= 0f) return;
        derived.compute(this);
        ChemistrySystem.tick(this, dt, settings.drugs());
        PainSystem.decay(this, dt);
        BleedingSystem.tick(this, env, dt);
        CirculationSystem.tick(this, env, dt, rng);
        RespirationSystem.tick(this, env, dt);
        BrainSystem.tick(this, dt);
        if (settings.infection()) InfectionSystem.tick(this, env, dt, rng);
        HygieneSystem.tick(this, env, dt);
        TemperatureSystem.tick(this, env, settings.temperature(), dt);
        MetabolismSystem.tick(this, env, settings, dt, rng);
        HealingSystem.tick(this, env, settings, dt);
        derived.compute(this);
        PainSystem.tickShock(this, dt);
        MindSystem.tick(this, env, settings, dt);
        ConsciousnessSystem.tick(this, env, dt);
    }

    public void injure(Injury injury, RandomGenerator rng) {
        InjuryApplier.apply(this, injury, rng);
        derived.compute(this);
    }

    public void injureAll(List<Injury> injuries, RandomGenerator rng) {
        for (Injury injury : injuries) InjuryApplier.apply(this, injury, rng);
        derived.compute(this);
    }

    /** @return the cause of death, or {@code null} while alive */
    public DeathCause deathCause() {
        return DeathCheck.check(this);
    }

    public boolean isIncapacitated() {
        return vitals.consciousness < ConsciousnessSystem.INCAPACITATED;
    }

    public boolean isUnconscious() {
        return vitals.consciousness < ConsciousnessSystem.UNCONSCIOUS;
    }

    public void emit(BodyEvent.Type type, BodyPart part) {
        if (events.size() < 64) events.add(new BodyEvent(type, part));
    }

    public List<BodyEvent> drainEvents() {
        if (events.isEmpty()) return List.of();
        List<BodyEvent> out = List.copyOf(events);
        events.clear();
        return out;
    }

    /**
     * Restores {@code points} of tissue, most damaged parts first: muscle up to its cap, then skin.
     * Used for vanilla healing (regeneration potions, golden apples...).
     */
    public void regenerate(float points) {
        for (int pass = 0; pass < 4 && points > 0.01f; pass++) {
            int damaged = 0;
            for (PartState s : parts) {
                if (!s.amputated && (s.muscle < s.maxMuscle() || s.skin < 100f)) damaged++;
            }
            if (damaged == 0) break;
            float share = points / damaged;
            for (PartState s : parts) {
                if (s.amputated) continue;
                float muscle = Math.min(share, Math.max(0f, s.maxMuscle() - s.muscle));
                s.muscle += muscle;
                float skin = Math.min(share - muscle, 100f - s.skin);
                s.skin += skin;
                points -= muscle + skin;
            }
        }
        derived.compute(this);
    }

    /** Pulled back from the brink (totem of undying): restarts the heart, stops the worst bleeding, restores blood and oxygen. */
    public void stabilize() {
        Vitals v = vitals;
        v.headDestroyed = false;
        v.bloodVolume = Math.max(v.bloodVolume, 4.2f);
        v.cardiacArrest = false;
        v.arrestTime = 0f;
        v.fibrillation = 0f;
        v.heartRate = Math.max(v.heartRate, 90f);
        v.systolic = Math.max(v.systolic, 105f);
        v.diastolic = v.systolic * (2f / 3f);
        v.respRate = Math.max(v.respRate, 14f);
        v.spo2 = Math.max(v.spo2, 95f);
        v.hemothorax = 0f;
        v.embolism = false;
        v.brainHealth = Math.max(v.brainHealth, 60f);
        v.consciousness = Math.max(v.consciousness, 60f);
        v.shock = 0f;
        v.sepsis = Math.min(v.sepsis, 20f);
        v.bodyTemp = Math.min(Math.max(v.bodyTemp, 35.5f), 38.5f);
        for (BodyPart part : BodyPart.VALUES) {
            PartState s = parts[part.ordinal()];
            s.bleed *= 0.2f;
            s.internalBleed = 0f;
            s.pain *= 0.5f;
            if (part == BodyPart.HEAD) s.muscle = Math.max(s.muscle, 40f);
            if (part == BodyPart.THORAX) s.muscle = Math.max(s.muscle, 30f);
        }
        needs.thirst = Math.max(needs.thirst, 40f);
        needs.energy = Math.max(needs.energy, 40f);
        derived.compute(this);
    }

    /** Back to a healthy body (keeps the blood type). */
    public void healFully() {
        for (PartState part : parts) part.reset();
        vitals.reset();
        needs.reset();
        mind.reset();
        chem.reset();
        exhaustionDebt = 0f;
        events.clear();
        derived.compute(this);
    }

    public void visit(StateVisitor v) {
        v.i("version", DATA_VERSION);
        for (BodyPart part : BodyPart.VALUES) {
            v.push(part.serializedName());
            parts[part.ordinal()].visit(v);
            v.pop();
        }
        v.push("vitals");
        vitals.visit(v);
        v.pop();
        v.push("needs");
        needs.visit(v);
        v.pop();
        v.push("mind");
        mind.visit(v);
        v.pop();
        v.push("chemistry");
        chem.visit(v);
        v.pop();
        exhaustionDebt = v.f("exhaustion_debt", exhaustionDebt);
    }
}
