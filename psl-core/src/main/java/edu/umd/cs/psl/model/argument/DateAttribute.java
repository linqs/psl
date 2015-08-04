package edu.umd.cs.psl.model.argument;

import org.joda.time.DateTime;

/**
 * An {@link Attribute} that encapsulates a Date.
 */
public class DateAttribute implements Attribute{

    private final DateTime value;

    /**
     * Constructs a Date attribute from a Date
     *
     * @param value  Date to encapsulate
     */
    public DateAttribute(DateTime value) {
        this.value = value;
    }

    /**
     * @return the encapsulated Date as a String in single quotes
     */
    @Override
    public String toString() {
        return "'" + value.toString() + "'";
    }

    @Override
    public DateTime getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /**
     * A DateAttribute is equal to another Object if that Object is a DateAttribute
     * and their values are equal.
     */
    @Override
    public boolean equals(Object oth) {
        if (oth==this) return true;
        if (oth==null || !(oth instanceof DateAttribute)) return false;
        return value == ((DateAttribute) oth).value;
    }

    @Override
    public int compareTo(GroundTerm o) {
        if (o instanceof DateAttribute)
            return value.compareTo(((DateAttribute) o).value);
        else
            return this.getClass().getSimpleName().compareTo(o.getClass().getSimpleName());
    }
}
