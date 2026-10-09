package demo;

/**
 * This file does NOT compile: a closing brace and a semicolon are missing.
 * An analyzer should report the problem and keep going.
 */
public class Broken {

    public int twice(int value) {
        return Helper.add(value, value)
    }
