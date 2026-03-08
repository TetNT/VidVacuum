package com.tetsoft.controller;

import com.tetsoft.client.VidVacuumClient;
import com.tetsoft.dto.ResponseDto;

import java.io.File;

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
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getName() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(resource);
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
