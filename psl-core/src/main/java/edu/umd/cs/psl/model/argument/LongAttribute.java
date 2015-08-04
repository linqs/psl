package edu.umd.cs.psl.model.argument;

/**
 * An {@link Attribute} that encapsulates a Double.
 */
public class LongAttribute implements Attribute {

    private final Long value;

    /**
     * Constructs a Double attribute from a Double
     *
     * @param value  Double to encapsulate
     */
    public LongAttribute(Long value) {
        this.value = value;
    }

    /**
     * @return the encapsulated Double as a String in single quotes
     */
    @Override
    public String toString() {
        return "'" + value + "'";
    }

    @Override
    public Long getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /**
     * A LongAttribute is equal to another Object if that Object is a LongAttribute
     * and their values are equal.
     */
    @Override
    public boolean equals(Object oth) {
        if (oth==this) return true;
        if (oth==null || !(oth instanceof LongAttribute)) return false;
        return value.equals(((LongAttribute) oth).getValue());
    }

    @Override
    public int compareTo(GroundTerm o) {
        if (o instanceof LongAttribute)
            return value.compareTo(((LongAttribute) o).value);
        else
            return this.getClass().getSimpleName().compareTo(o.getClass().getSimpleName());
    }
}
