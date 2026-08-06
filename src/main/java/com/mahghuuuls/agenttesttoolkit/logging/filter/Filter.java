package com.mahghuuuls.agenttesttoolkit.logging.filter;

/**
 * Decides whether an event at a position should be recorded.
 *
 * <p>Evaluated <b>before</b> the record is built, so an excluded event costs a coordinate
 * comparison rather than the string work of assembling a record that is then thrown away.
 *
 * <p>REQ-047 forbids composing filters into logical expressions. One filter per category,
 * replacing any previous one. That is a deliberate ceiling: a filter language would make the
 * question "why is this event missing from my log?" require reasoning about an expression, and
 * that question is exactly what the toolkit exists to make easy.
 *
 * <p>Takes a dimension alongside the coordinates because positions repeat across dimensions.
 * The same x, y, z in the Nether and the Overworld are different places, and a filter that
 * ignored that would admit events from a dimension the operator was not looking at.
 */
public interface Filter {

    /** @return true when an event at this position should be recorded. */
    boolean admits(int dimension, double x, double y, double z);

    /**
     * @return a short description for {@code log status}, for example {@code radius=20}.
     *
     * <p>REQ-038 depends on this. An excluded event and an event that never happened look
     * identical in the log, so status must be able to say what is filtering.
     */
    String describe();
}
