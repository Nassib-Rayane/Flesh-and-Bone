package com.fleshbone.sim;

/**
 * Single description of the body's persistent fields, shared by saving (ValueInput/ValueOutput) and network sync.
 * A writer returns the value it was given; a reader ignores it and returns what it read.
 * Every state class implements {@code visit(StateVisitor)} as a list of {@code field = v.f("key", field);} lines,
 * so the save format and the network format can never drift apart.
 */
public interface StateVisitor {
    float f(String key, float value);

    int i(String key, int value);

    boolean b(String key, boolean value);

    <E extends Enum<E>> E e(String key, E value, Class<E> type);

    /** Enter a named group (a body part, the vitals...). */
    void push(String key);

    void pop();
}
