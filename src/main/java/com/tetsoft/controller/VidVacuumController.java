package com.tetsoft.controller;

import com.tetsoft.client.VidVacuumClient;
import com.tetsoft.dto.ProgressResponseDto;
import com.tetsoft.dto.ResponseDto;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;


@RestController
@CrossOrigin(origins = "*")
public class VidVacuumController {
    
    @Autowired
    private VidVacuumClient vidVacuumClient;
    
    @PostMapping("/download")
    public ResponseEntity<ResponseDto> downloadVideo(
            @RequestParam String url,
            @RequestParam(defaultValue = "mp4") String format) {
        ResponseDto responseDto = vidVacuumClient.downloadVideo(url, format);
        return responseDto.code() == 200 
            ? ResponseEntity.ok(responseDto) 
            : ResponseEntity.status(responseDto.code()).body(responseDto);
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(
            @RequestParam String url,
            @RequestParam(defaultValue = "mp4") String format) {
        Resource resource = vidVacuumClient.getVideoAsResource(url, format);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        File file = ((FileSystemResource) resource).getFile();
        String contentType = getContentType(format);

        String displayName = file.getName();
        String asciiName = displayName.replaceAll("[^\\x20-\\x7E]", "_");
        String encodedName = URLEncoder.encode(displayName, StandardCharsets.UTF_8);

        String disposition = "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encodedName;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(resource);
    }

    @GetMapping("/download/progress")
    public ResponseEntity<ProgressResponseDto> getDownloadProgress() {
        String progress = vidVacuumClient.getLastDownloadProgress();
        String message = vidVacuumClient.getLastDownloadProgressLine();
        boolean downloading = vidVacuumClient.isDownloading();

        if (progress == null || progress.isBlank()) {
            progress = "0%";
        }

        ProgressResponseDto dto = new ProgressResponseDto(progress, message, downloading);
        return ResponseEntity.ok(dto);
    }

    private String getContentType(String format) {
        return switch (format) {
            case "mp3" -> "audio/mpeg";
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            default -> "application/octet-stream";
        };
    }
}
