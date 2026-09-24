package edu.cse203.qrattendance;

import java.time.Instant;

public record SessionRecord(
        String sessionId,
        String course,
        String section,
        String token,
        Instant startsAt,
        Instant expiresAt,
        String status,
        String facultyName) {

    public boolean isOpenNow() {
        Instant now = Instant.now();
        return "OPEN".equals(status) && !now.isBefore(startsAt) && now.isBefore(expiresAt);
    }

    public String displayStatus() {
        if ("CLOSED".equals(status)) return "Closed";
        return Instant.now().isBefore(expiresAt) ? "Active" : "Expired";
    }
}

