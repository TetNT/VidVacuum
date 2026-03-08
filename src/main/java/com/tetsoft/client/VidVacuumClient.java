package com.tetsoft.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import com.tetsoft.dto.ErrorResponseDto;
import com.tetsoft.dto.ResponseDto;
import com.tetsoft.dto.SuccessResponseDto;
import com.tetsoft.exception.NotFoundException;
import com.tetsoft.exception.PrivateVideoException;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

@Component
public class VidVacuumClient {
    
    @Value("${yt-dlp.path:yt-dlp}")
    private String ytDlpPath;
    
    @Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;
    
    @Value("${download.output-path:./downloads}")
    private String outputPath;

    private String destinationCueLine = "[download] Destination:";
    private String mergingFormatsCueLine = "[Merger] Merging formats into";
    private String alreadyDownloadedCueLine = "has already been downloaded";
    private String notFoundCueLine = "HTTP Error 404: Not Found";
    private String privateVideoCueLine = "Private video. Sign in if you've been granted access to this video.";

    public Resource getVideoAsResource(String url, String format) {
        ResponseDto responseDto = downloadVideo(url, format);
        if (responseDto instanceof SuccessResponseDto) {
            SuccessResponseDto successResponseDto = (SuccessResponseDto) responseDto;
            File file = new File(outputPath, successResponseDto.resultFileName());
            return new FileSystemResource(file);
        }
        return null;
    }
    
    public ResponseDto downloadVideo(String url, String format) {
        if (!isValidFormat(format)) {
            return ErrorResponseDto.builder()
                    .message("Requested format is invalid: " + format + ". Supported formats: mp3, mp4, webm")
                    .errorType("InvalidFormat")
                    .code(400)
                    .build();
        }
        
        // Download video first
        String downloadedFilePath = "";
        try {
            downloadedFilePath = downloadVideoFile(url);
        } catch (NotFoundException e) {
            return ErrorResponseDto.builder()
                    .message("Video not found at URL: " + url)
                    .errorType("VideoNotFound")
                    .code(404)
                    .build();
        } catch (PrivateVideoException e) {
            return ErrorResponseDto.builder()
                    .message("Video is private at URL: " + url + ".")
                    .errorType("PrivateVideo")
                    .code(403)
                    .build();
        }
        
        catch (Exception e) {
            return ErrorResponseDto.builder()
                    .message("Failed to download video from URL: " + url + ". Error: " + e.getMessage())
                    .errorType("DownloadFailed")
                    .code(500)
                    .build();
        }
        
        System.out.println("Downloaded file path: " + downloadedFilePath);
        
        // Convert to requested format
        if (!format.equals(extractFileExtension(downloadedFilePath))) {
            System.out.println("Converting downloaded file to format: " + format);
            String resultFileName = "";
            try {
                resultFileName = convertVideo(downloadedFilePath, format);
            } catch (Exception e) {
                return ErrorResponseDto.builder()
                        .message("Failed to convert video to format: " + format)
                        .errorType("ConversionFailed")
                        .code(500)
                        .build();
            }
            try {
                String downloadUrl = "/download?url=" + URLEncoder.encode(url, "UTF-8") + "&format=" + format;
                return SuccessResponseDto.builder()
                        .originalDownloadedFileName(extractFileName(downloadedFilePath))
                        .resultFileName(extractFileName(resultFileName))
                        .requestedVideoUrl(url)
                        .requestedExtension(format)
                        .hadToConvertExtensions(true)
                        .downloadUrl(downloadUrl)
                        .build();
            } catch (Exception e) {
                return ErrorResponseDto.builder()
                        .message("Failed to encode download URL")
                        .errorType("UrlEncodingFailed")
                        .code(500)
                        .build();
            }
        } else {
            System.out.println("Downloaded file is already in the requested format: " + format);
            try {
                String downloadUrl = "/download?url=" + URLEncoder.encode(url, "UTF-8") + "&format=" + format;
                return SuccessResponseDto.builder()
                        .originalDownloadedFileName(extractFileName(downloadedFilePath))
                        .resultFileName(extractFileName(downloadedFilePath))
                        .requestedVideoUrl(url)
                        .requestedExtension(format)
                        .hadToConvertExtensions(false)
                        .downloadUrl(downloadUrl)
                        .build();
            } catch (Exception e) {
                return ErrorResponseDto.builder()
                        .message("Failed to encode download URL")
                        .errorType("UrlEncodingFailed")
                        .code(500)
                        .build();
            }
        }
    }
    
    private String downloadVideoFile(String url) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(ytDlpPath);
        command.add("-o");
        command.add(outputPath + "/%(title)s.%(ext)s");
        command.add(url);
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        String downloadedFileFullPath = "";
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
            if (line.contains(notFoundCueLine)) {
                throw new NotFoundException();
            } else if (line.contains(privateVideoCueLine)) {
                throw new PrivateVideoException();
            } else if (line.contains(destinationCueLine)) {
                // In some cases (like audio-only) there is no merging step, so we take the downloaded file path from the destination line
                downloadedFileFullPath = line.substring(line.indexOf(destinationCueLine) + destinationCueLine.length()).trim();
            } else if (line.contains(mergingFormatsCueLine)) {
                // Look for "Merging formats into" which contains the final merged file path
                downloadedFileFullPath = extractFileFullPath(line);
            } else if (line.contains(alreadyDownloadedCueLine)) {
                downloadedFileFullPath = extractFileFullPathFromAlreadyDownloadedLine(line);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("yt-dlp failed with exit code: " + exitCode);
        }
        
        if (downloadedFileFullPath == null || downloadedFileFullPath.isEmpty()) {
            throw new RuntimeException("Could not determine downloaded file path from yt-dlp output");
        }
        
        return downloadedFileFullPath;
    }
    
    private String convertVideo(String inputFilePath, String targetFormat) throws Exception {
        String outputFile = inputFilePath.substring(0, inputFilePath.lastIndexOf('.')) + "." + targetFormat;
        System.out.println("Converting file: " + inputFilePath + " to: " + outputFile);
        
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-i");
        command.add(inputFilePath);
        command.add("-y"); // overwrite

        // format-specific options
        switch (targetFormat) {
            case "mp3":
                command.add("-q:a");
                command.add("0");
                command.add("-map");
                command.add("a");
                break;
            case "webm":
                command.add("-c:v");
                command.add("libvpx-vp9");
                command.add("-crf");
                command.add("23");
                break;
            default:
                // mp4 default, nothing extra
        }

        command.add(outputFile);

        int exitCode = runCommand(command);
        if (exitCode != 0) {
            throw new RuntimeException("ffmpeg conversion failed with exit code: " + exitCode);
        }

        // delete original
        new File(inputFilePath).delete();
        return outputFile;
    }
    
    public boolean isValidFormat(String format) {
        return format.equals("mp3") || format.equals("mp4") || format.equals("webm");
    }
    
    public String extractFileFullPath(String line) {
        var fileName = "";
        // Extract path from quoted string
        // Example: [Merger] Merging formats into "./downloads/SampleVideo.webm"
        int startIdx = line.indexOf('"');
        int endIdx = line.lastIndexOf('"');
        if (startIdx != -1 && endIdx != -1 && startIdx < endIdx) {
            fileName = line.substring(startIdx + 1, endIdx);
            System.out.println("Found merged file: " + fileName);
            return fileName;
        }
        return fileName;
    }

    public String extractFileFullPathFromAlreadyDownloadedLine(String line) {
        // Example: [download] ./downloads/Video by someone.mp4 has already been downloaded
        int startIdx = line.indexOf(outputPath);
        int endIdx = line.indexOf(alreadyDownloadedCueLine) - 1;
        if (startIdx != -1 && endIdx != -1 && startIdx < endIdx) {
            String fileName = line.substring(startIdx, endIdx).trim();
            System.out.println("Found already downloaded file: " + fileName);
            return fileName;
        }
        return "";
    }

    public String extractFileName(String filePath) {
        int lastSeparatorIdx = filePath.lastIndexOf(File.separator);
        if (lastSeparatorIdx != -1 && lastSeparatorIdx < filePath.length() - 1) {
            return filePath.substring(lastSeparatorIdx + 1);
        }
        return filePath; // if no separator found, return the whole path
    }

    public String extractFileExtension(String filePath) {
        int lastDotIdx = filePath.lastIndexOf('.');
        if (lastDotIdx != -1 && lastDotIdx < filePath.length() - 1) {
            return filePath.substring(lastDotIdx + 1);
        }
        return "";
    }

    public int runCommand(List<String> command)  {
        System.out.println("Running command: " + String.join(" ", command));
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            
            int exitCode = process.waitFor();
            return exitCode;
        } catch (Exception e) {
            throw new RuntimeException("Failed to run command: " + String.join(" ", command), e);
        }
        
    }
}