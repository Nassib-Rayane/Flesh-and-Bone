package com.fleshbone.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The 13 simulated body parts. Shins include the feet, hands are separate from forearms.
 * Parts form a tree rooted at the thorax, used for tourniquets (distal chain) and infection spread (adjacency).
 */
public enum BodyPart {
    HEAD(Region.HEAD, Side.CENTER),
    THORAX(Region.TORSO, Side.CENTER),
    ABDOMEN(Region.TORSO, Side.CENTER),
    UPPER_ARM_L(Region.ARM, Side.LEFT),
    FOREARM_L(Region.ARM, Side.LEFT),
    HAND_L(Region.HAND, Side.LEFT),
    UPPER_ARM_R(Region.ARM, Side.RIGHT),
    FOREARM_R(Region.ARM, Side.RIGHT),
    HAND_R(Region.HAND, Side.RIGHT),
    THIGH_L(Region.LEG, Side.LEFT),
    SHIN_L(Region.LEG, Side.LEFT),
    THIGH_R(Region.LEG, Side.RIGHT),
    SHIN_R(Region.LEG, Side.RIGHT);

    public enum Region { HEAD, TORSO, ARM, HAND, LEG }

    public enum Side { LEFT, CENTER, RIGHT }

    public static final BodyPart[] VALUES = values();
    public static final int COUNT = VALUES.length;

    private final Region region;
    private final Side side;
    private final String serializedName;
    private BodyPart parent;
    private final List<BodyPart> children = new ArrayList<>(2);
    private List<BodyPart> distal = List.of();
    private List<BodyPart> adjacent = List.of();

    BodyPart(Region region, Side side) {
        this.region = region;
        this.side = side;
        this.serializedName = name().toLowerCase(Locale.ROOT);
    }

    static {
        link(HEAD, THORAX);
        link(ABDOMEN, THORAX);
        link(UPPER_ARM_L, THORAX);
        link(FOREARM_L, UPPER_ARM_L);
        link(HAND_L, FOREARM_L);
        link(UPPER_ARM_R, THORAX);
        link(FOREARM_R, UPPER_ARM_R);
        link(HAND_R, FOREARM_R);
        link(THIGH_L, ABDOMEN);
        link(SHIN_L, THIGH_L);
        link(THIGH_R, ABDOMEN);
        link(SHIN_R, THIGH_R);
        for (BodyPart part : VALUES) {
            List<BodyPart> chain = new ArrayList<>();
            collectDistal(part, chain);
            part.distal = Collections.unmodifiableList(chain);
            Set<BodyPart> adj = new LinkedHashSet<>(part.children);
            if (part.parent != null) adj.add(part.parent);
            part.adjacent = List.copyOf(adj);
        }
    }

    private static void link(BodyPart child, BodyPart parent) {
        child.parent = parent;
        parent.children.add(child);
    }

    private static void collectDistal(BodyPart part, List<BodyPart> out) {
        out.add(part);
        for (BodyPart child : part.children) collectDistal(child, out);
    }

    public Region region() { return region; }

    public Side side() { return side; }

    /** Lower-case name used in commands, saves and translation keys ({@code upper_arm_l}). */
    public String serializedName() { return serializedName; }

    /** The part this one hangs off, or {@code null} for the thorax. */
    public BodyPart parent() { return parent; }

    public List<BodyPart> children() { return Collections.unmodifiableList(children); }

    /** This part followed by every part further from the torso (a tourniquet here stops bleeding on all of them). */
    public List<BodyPart> distal() { return distal; }

    /** Parent and children: where infections spread. */
    public List<BodyPart> adjacent() { return adjacent; }

    public boolean isLeg() { return region == Region.LEG; }

    public boolean isArm() { return region == Region.ARM || region == Region.HAND; }

    public boolean isLimb() { return isArm() || isLeg(); }

    public boolean isTorso() { return region == Region.TORSO; }

    /** Head, thorax and abdomen can bleed internally. */
    public boolean canBleedInternally() { return region == Region.HEAD || region == Region.TORSO; }

    /** Tourniquets go on upper arms, forearms, thighs and shins. */
    public boolean canHoldTourniquet() { return region == Region.ARM || region == Region.LEG; }

    /** Every part has a joint that can dislocate except the abdomen (head = jaw, thorax = spine). */
    public boolean canDislocate() { return this != ABDOMEN; }

    /** Amputation is possible on limbs only. */
    public boolean canAmputate() { return isLimb(); }

    public BodyPart mirror() {
        return switch (this) {
            case UPPER_ARM_L -> UPPER_ARM_R;
            case FOREARM_L -> FOREARM_R;
            case HAND_L -> HAND_R;
            case UPPER_ARM_R -> UPPER_ARM_L;
            case FOREARM_R -> FOREARM_L;
            case HAND_R -> HAND_L;
            case THIGH_L -> THIGH_R;
            case SHIN_L -> SHIN_R;
            case THIGH_R -> THIGH_L;
            case SHIN_R -> SHIN_L;
            default -> this;
        };
    }

    public static BodyPart byName(String name) {
        for (BodyPart part : VALUES) {
            if (part.serializedName.equals(name)) return part;
        }
        return null;
    }

    /** The upper arm, forearm and hand of one side. */
    public static List<BodyPart> arm(Side side) {
        return side == Side.LEFT ? List.of(UPPER_ARM_L, FOREARM_L, HAND_L) : List.of(UPPER_ARM_R, FOREARM_R, HAND_R);
    }

    /** The thigh and shin of one side. */
    public static List<BodyPart> leg(Side side) {
        return side == Side.LEFT ? List.of(THIGH_L, SHIN_L) : List.of(THIGH_R, SHIN_R);
    }
}
