package id.octanexus.octatycoon.task;

public record JobContext(long nowMillis, GuardLevel guardLevel, int batchDivisor) {}
