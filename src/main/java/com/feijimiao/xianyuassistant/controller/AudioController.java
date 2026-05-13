package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.service.XianyuAudioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/msg")
@CrossOrigin(origins = "*")
public class AudioController {

    private final XianyuAudioService xianyuAudioService;

    public AudioController(XianyuAudioService xianyuAudioService) {
        this.xianyuAudioService = xianyuAudioService;
    }

    @GetMapping("/audio/{messageId}")
    public ResponseEntity<byte[]> getAudio(@PathVariable Long messageId) {
        return xianyuAudioService.getPlayableAudio(messageId);
    }
}
