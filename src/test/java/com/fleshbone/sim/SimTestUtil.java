package com.fleshbone.sim;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

final class SimTestUtil {
    static final float DT = 0.05f;
    /** Default rules without the last stand roll, so dying bodies die deterministically. */
    static final SimSettings SETTINGS = new SimSettings(5f, 1f, 30f * 60f, 12f * 60f, 1f, 1f, 1f, 1f,
            true, true, true, true, true, true, true, true, false);
    /** Hunger, thirst and sleepiness frozen: for timing tests that must not starve, dehydrate or fall asleep. */
    static final SimSettings STEADY = new SimSettings(5f, 1f, 30f * 60f, 12f * 60f, 0f, 0f, 0f, 1f,
            true, true, true, true, true, true, true, true, false);

    private SimTestUtil() {}

    static Body body() {
        return Body.create(new Random(1));
    }

    static Environment env() {
        Environment env = new Environment();
        env.dt = DT;
        return env;
    }

    /** Runs the body for {@code seconds}, or until {@code stop} matches; returns the simulated time elapsed. */
    static float run(Body body, Environment env, SimSettings settings, float seconds, Predicate<Body> stop) {
        Random rng = new Random(7);
        float t = 0f;
        while (t < seconds) {
            body.tick(env, settings, rng);
            t += env.dt;
            if (stop != null && stop.test(body)) return t;
        }
        return t;
    }

    static float run(Body body, Environment env, float seconds, Predicate<Body> stop) {
        return run(body, env, SETTINGS, seconds, stop);
    }

    static float run(Body body, Environment env, float seconds) {
        return run(body, env, SETTINGS, seconds, null);
    }

    /** Round-trips a body through the {@link StateVisitor} format with a flat key/value map. */
    static Body copy(Body source) {
        Map<String, Object> data = new HashMap<>();
        source.visit(new MapVisitor(data, true));
        Body target = new Body();
        target.visit(new MapVisitor(data, false));
        target.derived.compute(target);
        return target;
    }

    private static final class MapVisitor implements StateVisitor {
        private final Map<String, Object> data;
        private final boolean write;
        private String prefix = "";

        MapVisitor(Map<String, Object> data, boolean write) {
            this.data = data;
            this.write = write;
        }

        private Object io(String key, Object value) {
            if (write) {
                data.put(prefix + key, value);
                return value;
            }
            return data.getOrDefault(prefix + key, value);
        }

        @Override public float f(String key, float value) { return (Float) io(key, value); }

        @Override public int i(String key, int value) { return (Integer) io(key, value); }

        @Override public boolean b(String key, boolean value) { return (Boolean) io(key, value); }

        @Override
        @SuppressWarnings("unchecked")
        public <E extends Enum<E>> E e(String key, E value, Class<E> type) { return (E) io(key, value); }

        @Override public void push(String key) { prefix = prefix + key + "."; }

        @Override
        public void pop() {
            int cut = prefix.lastIndexOf('.', prefix.length() - 2);
            prefix = cut < 0 ? "" : prefix.substring(0, cut + 1);
        }
    }
}
