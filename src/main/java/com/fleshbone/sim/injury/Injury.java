package com.fleshbone.sim.injury;

import com.fleshbone.sim.BodyPart;

/**
 * One trauma hitting one body part.
 *
 * @param severity      trauma intensity (vanilla damage × damage scale, after armor)
 * @param contamination extra germs carried by the source (a zombie's claws, a rusty blade...), 0–100
 * @param shrapnel      fragments to embed if the kind rolls shrapnel (0 = one)
 * @param venom         venom dose injected (spiders, bees)
 */
public record Injury(BodyPart part, InjuryKind kind, float severity, float contamination, int shrapnel, float venom) {
    public static Injury of(BodyPart part, InjuryKind kind, float severity) {
        return new Injury(part, kind, severity, 0f, 0, 0f);
    }

    public Injury withSeverity(float newSeverity) {
        return new Injury(part, kind, newSeverity, contamination, shrapnel, venom);
    }

    public Injury withContamination(float extra) {
        return new Injury(part, kind, severity, extra, shrapnel, venom);
    }

    public Injury withVenom(float dose) {
        return new Injury(part, kind, severity, contamination, shrapnel, dose);
    }
}
