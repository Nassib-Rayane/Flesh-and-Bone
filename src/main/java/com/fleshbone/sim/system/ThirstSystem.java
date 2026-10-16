package com.fleshbone.sim.system;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.BodyPart;
import com.fleshbone.sim.Environment;
import com.fleshbone.sim.Needs;
import com.fleshbone.sim.PartState;
import com.fleshbone.sim.SimSettings;

/**
 * Thirst, after Casualties: Unknown: −1/17 point per second at 37 °C, faster when hot (+50 % per °C) and twice as
 * fast while above 100. Sweating costs extra water (see the temperature system).
 * <ul>
 *   <li>&gt;100 slaked (pressure up), &gt;125 overhydrated, &gt;175 water intoxication: headache, brain damage,
 *       fibrillation.</li>
 *   <li>≤55 dehydrated, ≤35 parched (chest pain), ≤0 vessels clamp down and the heart races, then the organs fail.</li>
 * </ul>
 */
public final class ThirstSystem {
    public static final float DRAIN = 1f / 17f;
    public static final float SLAKED = 100f;
    public static final float OVERHYDRATED = 125f;
    public static final float WATER_INTOXICATION = 175f;
    public static final float DEHYDRATED = 55f;
    public static final float PARCHED = 35f;

    private ThirstSystem() {}

    public static void tick(Body body, Environment env, SimSettings settings, float dt) {
        Needs n = body.needs;
        if (!settings.thirst()) {
            n.thirst = SLAKED;
            return;
        }
        float heat = 1f + 0.5f * Math.max(0f, body.vitals.bodyTemp - TemperatureSystem.NORMAL);
        float rate = DRAIN * settings.thirstSpeed() * heat * (n.thirst > SLAKED ? 2f : 1f) * (body.isAsleep(env) ? 0.5f : 1f)
                * (1f + body.chem.caffeine / 200f);
        n.thirst = Math.max(Needs.MIN_THIRST, n.thirst - rate * dt);

        if (n.thirst <= PARCHED) {
            PartState thorax = body.part(BodyPart.THORAX);
            if (thorax.pain < 15f) thorax.pain = Math.min(15f, thorax.pain + dt);
        }
        if (n.thirst > WATER_INTOXICATION) {
            PartState head = body.part(BodyPart.HEAD);
            if (head.pain < 50f) head.pain = Math.min(50f, head.pain + 2.5f * dt);
        }
    }
}
