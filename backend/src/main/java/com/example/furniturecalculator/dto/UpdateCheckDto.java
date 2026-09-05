package com.example.furniturecalculator.dto;

public record UpdateCheckDto(boolean updateAvailable, String latestVersion, String downloadUrl) {

    public static UpdateCheckDto notAvailable() {
        return new UpdateCheckDto(false, null, null);
    }
}
