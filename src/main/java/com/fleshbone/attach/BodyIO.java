package com.fleshbone.attach;

import java.util.ArrayDeque;
import java.util.Deque;

import com.fleshbone.sim.Body;
import com.fleshbone.sim.StateVisitor;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * {@link StateVisitor} implementations that save the body to world data and send it over the network.
 * Network floats are packed as IEEE half floats: about three significant digits, plenty for display.
 */
public final class BodyIO {
    private BodyIO() {}

    public static void save(Body body, ValueOutput output) {
        body.visit(new SaveVisitor(output));
    }

    public static void load(Body body, ValueInput input) {
        body.visit(new LoadVisitor(input));
        body.derived.compute(body);
    }

    public static void write(Body body, FriendlyByteBuf buf) {
        body.visit(new BufWriter(buf));
    }

    public static void read(Body body, FriendlyByteBuf buf) {
        body.visit(new BufReader(buf));
        body.derived.compute(body);
    }

    private static final class SaveVisitor implements StateVisitor {
        private final Deque<ValueOutput> stack = new ArrayDeque<>();

        SaveVisitor(ValueOutput root) {
            stack.push(root);
        }

        @Override public float f(String key, float value) { stack.peek().putFloat(key, value); return value; }

        @Override public int i(String key, int value) { stack.peek().putInt(key, value); return value; }

        @Override public boolean b(String key, boolean value) { stack.peek().putBoolean(key, value); return value; }

        @Override
        public <E extends Enum<E>> E e(String key, E value, Class<E> type) {
            stack.peek().putString(key, value.name());
            return value;
        }

        @Override public void push(String key) { stack.push(stack.peek().child(key)); }

        @Override public void pop() { stack.pop(); }
    }

    private static final class LoadVisitor implements StateVisitor {
        private final Deque<ValueInput> stack = new ArrayDeque<>();

        LoadVisitor(ValueInput root) {
            stack.push(root);
        }

        @Override public float f(String key, float value) { return stack.peek().getFloatOr(key, value); }

        @Override public int i(String key, int value) { return stack.peek().getIntOr(key, value); }

        @Override public boolean b(String key, boolean value) { return stack.peek().getBooleanOr(key, value); }

        @Override
        public <E extends Enum<E>> E e(String key, E value, Class<E> type) {
            String name = stack.peek().getStringOr(key, value.name());
            try {
                return Enum.valueOf(type, name);
            } catch (IllegalArgumentException unknown) {
                return value;
            }
        }

        @Override public void push(String key) { stack.push(stack.peek().childOrEmpty(key)); }

        @Override public void pop() { stack.pop(); }
    }

    private static final class BufWriter implements StateVisitor {
        private final FriendlyByteBuf buf;

        BufWriter(FriendlyByteBuf buf) {
            this.buf = buf;
        }

        @Override public float f(String key, float value) { buf.writeShort(Float.floatToFloat16(value)); return value; }

        @Override public int i(String key, int value) { buf.writeVarInt(value); return value; }

        @Override public boolean b(String key, boolean value) { buf.writeBoolean(value); return value; }

        @Override public <E extends Enum<E>> E e(String key, E value, Class<E> type) { buf.writeEnum(value); return value; }

        @Override public void push(String key) { }

        @Override public void pop() { }
    }

    private static final class BufReader implements StateVisitor {
        private final FriendlyByteBuf buf;

        BufReader(FriendlyByteBuf buf) {
            this.buf = buf;
        }

        @Override public float f(String key, float value) { return Float.float16ToFloat(buf.readShort()); }

        @Override public int i(String key, int value) { return buf.readVarInt(); }

        @Override public boolean b(String key, boolean value) { return buf.readBoolean(); }

        @Override public <E extends Enum<E>> E e(String key, E value, Class<E> type) { return buf.readEnum(type); }

        @Override public void push(String key) { }

        @Override public void pop() { }
    }
}
