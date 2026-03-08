package com.tetsoft.dto;

import lombok.Builder;

@Builder
public record ErrorResponseDto(int code, String message, String errorType) implements ResponseDto {
    
}
