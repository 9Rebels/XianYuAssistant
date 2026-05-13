package com.feijimiao.xianyuassistant.service;

import org.springframework.http.ResponseEntity;

public interface XianyuAudioService {

    ResponseEntity<byte[]> getPlayableAudio(Long messageId);
}
