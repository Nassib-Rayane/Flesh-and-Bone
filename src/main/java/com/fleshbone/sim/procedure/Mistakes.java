package com.fleshbone.sim.procedure;

/**
 * What went wrong during a procedure, applied by the server once it is done: extra pain (a clumsy hand), tears
 * (skin torn, fresh bleeding) and dirt (germs pushed in).
 */
public final class Mistakes {
    public int pain;
    public int tear;
    public int dirt;

    public int total() {
        return pain + tear + dirt;
    }

    /** Packed into one int for the network (0–255 each). */
    public int pack() {
        return Math.min(255, pain) | Math.min(255, tear) << 8 | Math.min(255, dirt) << 16;
    }

    public static Mistakes unpack(int packed) {
        Mistakes m = new Mistakes();
        m.pain = packed & 0xFF;
        m.tear = (packed >> 8) & 0xFF;
        m.dirt = (packed >> 16) & 0xFF;
        return m;
    }
}
