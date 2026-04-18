package com.bigbear.ai.platform.trigger.api;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/audio")
public class AudioStreamController {

    private static final String MP3_BASE_PATH = "D:\\音乐歌曲\\";

    @GetMapping("/stream/{fileName}")
    public ResponseEntity<Resource> streamMp3(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get(MP3_BASE_PATH + fileName);
            File mp3File = filePath.toFile();

            if (!mp3File.exists() || !mp3File.isFile()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            Resource resource = new FileSystemResource(mp3File);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
            headers.add("Content-Disposition", "inline; filename=\"" + fileName + "\"");
            headers.setContentLength(mp3File.length());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
