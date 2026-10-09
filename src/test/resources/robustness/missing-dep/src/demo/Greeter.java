package demo;

import org.apache.commons.lang3.StringUtils;

/**
 * Uses a library (Apache Commons Lang) that is deliberately NOT supplied:
 * the file is syntactically valid, but some bindings cannot be resolved.
 */
public class Greeter {

    private final Formatter formatter = new Formatter();

    public String greet(String name) {
        String clean = StringUtils.capitalize(name);
        return formatter.decorate(clean);
    }
}
