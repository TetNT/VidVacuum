package com.tetsoft.dto;

import lombok.Builder;

@Builder
public record SuccessResponseDto(String requestedVideoUrl, String requestedExtension, String originalDownloadedFileName, String resultFileName, boolean hadToConvertExtensions, String downloadUrl) implements ResponseDto {
        @Override
        public int code() {
            return 200;
        }    
}
