package com.tetsoft.dto;

public record ProgressResponseDto(
        String progress,
        String message,
        boolean downloading
) implements ResponseDto {
    @Override
    public int code() {
        return 200;
    }
}
