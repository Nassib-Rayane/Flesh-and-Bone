package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.DeathCause;
import com.fleshbone.sim.Vitals;

/** Brain health: lost to hypoxia, heatstroke and prolonged cardiac arrest; regrows extremely slowly. */
public final class BrainSystem {
    public static final float HYPOXIA_SPO2 = 60f;
    public static final float HEATSTROKE_TEMP = 41.5f;
    public static final float NATURAL_REGEN = 0.003f;

    private BrainSystem() {}

    public static void tick(Body body, float dt) {
        Vitals v = body.vitals;
        float damage = 0f;
        if (v.spo2 < HYPOXIA_SPO2) {
            damage += (HYPOXIA_SPO2 - v.spo2) * 0.01f;
            if (!v.cardiacArrest) v.pendingCause = body.chem.opiate > 80f ? DeathCause.OVERDOSE : DeathCause.ASPHYXIA;
        }
        if (v.bodyTemp > HEATSTROKE_TEMP) {
            damage += (v.bodyTemp - HEATSTROKE_TEMP) * 0.1f;
            v.pendingCause = DeathCause.HEATSTROKE;
        }
        if (v.cardiacArrest && v.arrestTime > 10f && v.cprPerfusion < 0.3f) damage += 0.1f;

        if (damage > 0f) {
            v.brainHealth = Math.max(0f, v.brainHealth - damage * dt);
        } else if (v.spo2 > 90f && !v.cardiacArrest && v.brainHealth > 0f) {
            // Brain death is irreversible: no regrowth from zero
            v.brainHealth = Math.min(100f, v.brainHealth + NATURAL_REGEN * dt);
        }
    }
}
