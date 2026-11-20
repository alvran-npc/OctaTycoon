package id.octanexus.octatycoon.task;

public enum GuardLevel {
    GREEN("green"), YELLOW("yellow"), ORANGE("orange"), RED("red");
    private final String id;
    GuardLevel(String id) { this.id = id; }
    public String id() { return id; }
    public static GuardLevel from(String input) {
        for (GuardLevel value : values()) if (value.id.equalsIgnoreCase(input)) return value;
        throw new IllegalArgumentException("Unknown guard level: " + input);
    }
}
