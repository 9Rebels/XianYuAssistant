package com.feijimiao.xianyuassistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.entity.XianyuChatMessage;
import com.feijimiao.xianyuassistant.mapper.XianyuChatMessageMapper;
import com.feijimiao.xianyuassistant.service.XianyuAudioService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class XianyuAudioServiceImpl implements XianyuAudioService {

    private static final int MAX_VISITED_NODES = 300;
    private static final int MAX_AUDIO_BYTES = 2 * 1024 * 1024;
    private static final Duration CACHE_MAX_AGE = Duration.ofHours(12);
    private static final List<String> AUDIO_URL_KEYS = List.of("url", "audio_url", "audioUrl", "voiceUrl", "playUrl");
    private static final MediaType AUDIO_WAV = MediaType.parseMediaType("audio/wav");

    private final XianyuChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    public XianyuAudioServiceImpl(XianyuChatMessageMapper chatMessageMapper, ObjectMapper objectMapper) {
        this.chatMessageMapper = chatMessageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResponseEntity<byte[]> getPlayableAudio(Long messageId) {
        if (messageId == null) {
            return ResponseEntity.badRequest().build();
        }
        XianyuChatMessage message = chatMessageMapper.findById(messageId);
        if (message == null) {
            return ResponseEntity.notFound().build();
        }
        String url = extractAudioUrl(message.getCompleteMsg());
        if (!hasText(url)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        try {
            DownloadedAudio audio = downloadAudio(url);
            boolean transcode = needsTranscode(audio.contentType(), audio.bytes());
            byte[] playable = transcode ? transcodeAmrToWav(audio.bytes()) : audio.bytes();
            MediaType mediaType = transcode ? AUDIO_WAV : resolveMediaType(audio.contentType());
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(CACHE_MAX_AGE).cachePrivate())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"xianyu-audio-" + messageId + audioExtension(mediaType) + "\"")
                    .contentType(mediaType)
                    .contentLength(playable.length)
                    .body(playable);
        } catch (Exception e) {
            log.warn("语音消息转码失败: messageId={}, url={}", messageId, url, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private DownloadedAudio downloadAudio(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("audio download failed: " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IllegalStateException("audio body is empty");
            }
            byte[] bytes = body.bytes();
            if (bytes.length > MAX_AUDIO_BYTES) {
                throw new IllegalStateException("audio too large: " + bytes.length);
            }
            return new DownloadedAudio(response.header("Content-Type", ""), bytes);
        }
    }

    private byte[] transcodeAmrToWav(byte[] source) throws Exception {
        Path input = Files.createTempFile("xianyu-audio-", ".amr");
        Path output = Files.createTempFile("xianyu-audio-", ".wav");
        Path logFile = Files.createTempFile("xianyu-audio-", ".log");
        try {
            Files.write(input, source);
            Process process = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "error",
                    "-y", "-i", input.toString(), "-ar", "16000", "-ac", "1", output.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("ffmpeg timeout");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("ffmpeg failed: " + Files.readString(logFile));
            }
            return Files.readAllBytes(output);
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
            Files.deleteIfExists(logFile);
        }
    }

    private String extractAudioUrl(String completeMsg) {
        JsonNode root = parseJson(completeMsg);
        if (root == null) {
            return "";
        }
        ArrayDeque<JsonNode> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited < MAX_VISITED_NODES) {
            JsonNode node = queue.removeFirst();
            visited += 1;
            String found = audioUrlFromNode(node);
            if (hasText(found)) {
                return found;
            }
            enqueueChildren(queue, node);
        }
        return "";
    }

    private String audioUrlFromNode(JsonNode node) {
        if (!node.isObject()) {
            return "";
        }
        JsonNode audio = node.get("audio");
        if (audio != null && audio.isObject()) {
            String url = pickUrl(audio);
            if (hasText(url)) {
                return url;
            }
        }
        if (node.has("contentType") && node.path("contentType").asInt() == 3) {
            return pickUrl(node);
        }
        return "";
    }

    private String pickUrl(JsonNode node) {
        for (String key : AUDIO_URL_KEYS) {
            JsonNode value = node.get(key);
            if (value != null && value.isTextual() && hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return "";
    }

    private void enqueueChildren(ArrayDeque<JsonNode> queue, JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> enqueueValue(queue, entry.getValue()));
        } else if (node.isArray()) {
            node.forEach(child -> enqueueValue(queue, child));
        }
    }

    private void enqueueValue(ArrayDeque<JsonNode> queue, JsonNode value) {
        if (value.isTextual()) {
            JsonNode parsed = parseJson(value.asText());
            if (parsed != null) {
                queue.add(parsed);
            }
            return;
        }
        if (value.isContainerNode()) {
            queue.add(value);
        }
    }

    private JsonNode parseJson(String value) {
        if (!hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }
        try {
            return objectMapper.readTree(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean needsTranscode(String contentType, byte[] bytes) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return normalized.contains("amr") || startsWithAmrHeader(bytes);
    }

    private boolean startsWithAmrHeader(byte[] bytes) {
        return bytes != null && bytes.length >= 6
                && bytes[0] == '#' && bytes[1] == '!' && bytes[2] == 'A'
                && bytes[3] == 'M' && bytes[4] == 'R';
    }

    private MediaType resolveMediaType(String contentType) {
        if (hasText(contentType)) {
            try {
                return MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String audioExtension(MediaType mediaType) {
        if (AUDIO_WAV.equals(mediaType)) {
            return ".wav";
        }
        String subtype = mediaType.getSubtype();
        return hasText(subtype) ? "." + subtype.replaceAll("[^a-zA-Z0-9]+", "") : ".bin";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record DownloadedAudio(String contentType, byte[] bytes) {
    }
}
