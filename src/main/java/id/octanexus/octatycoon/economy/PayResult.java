package id.octanexus.octatycoon.economy;

public record PayResult(Status status, long amount, String detail) {
    public enum Status { PAID, QUEUED, REJECTED }
}
