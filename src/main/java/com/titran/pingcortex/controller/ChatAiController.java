package com.titran.pingcortex.controller;

import com.titran.pingcortex.dto.request.SendMessageRequest;
import com.titran.pingcortex.dto.response.ChatExchangeResponse;
import com.titran.pingcortex.dto.response.ChatMessagesResponse;
import com.titran.pingcortex.dto.response.ChatSessionResponse;
import com.titran.pingcortex.security.CurrentUser;
import com.titran.pingcortex.service.ChatAiService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@AllArgsConstructor
public class ChatAiController {
    private final ChatAiService chatAiService;

    @PostMapping("/courses/{courseId}/chat-sessions")
    public ResponseEntity<ChatSessionResponse> createSession(@PathVariable UUID courseId, HttpServletRequest request) {
        UUID userId = CurrentUser.id(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatAiService.createSession(userId, courseId));
    }

    @GetMapping("/courses/{courseId}/chat-sessions")
    public ResponseEntity<List<ChatSessionResponse>> findAllSessionsByCourseId(@PathVariable UUID courseId, HttpServletRequest request) {
        UUID userId = CurrentUser.id(request);
        return ResponseEntity.ok(chatAiService.findAllSessionsByCourseId(userId, courseId));
    }

    @GetMapping("/chat-sessions/{chatSessionId}/messages")
    public ResponseEntity<List<ChatMessagesResponse>> findAllMessagesBySessionId(@PathVariable UUID chatSessionId, HttpServletRequest request) {
        UUID userId = CurrentUser.id(request);
        return ResponseEntity.ok(chatAiService.findAllMessagesBySessionId(userId, chatSessionId));
    }

    @PostMapping("/chat-sessions/{chatSessionId}/messages")
    public ResponseEntity<ChatExchangeResponse> askAi(@PathVariable UUID chatSessionId, HttpServletRequest request, @RequestBody SendMessageRequest content) {
        UUID userId = CurrentUser.id(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatAiService.askAi(userId, chatSessionId, content.content()));
    }
}
