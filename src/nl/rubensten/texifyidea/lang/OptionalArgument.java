package nl.rubensten.texifyidea.lang;

/**
 * @author Sten Wessel
 */
public class OptionalArgument extends Argument {
    OptionalArgument(String name) {
        super(name);
    }

    @Override
    public String toString() {
        return "[" + getName() + "]";
    }
}