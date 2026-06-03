package com.cloudpool.controller;

import com.cloudpool.model.User;
import com.cloudpool.repository.UserRepository;
import com.cloudpool.service.GoogleDriveService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OAuthController {

    private final GoogleDriveService googleDriveService;
    private final UserRepository userRepository;

    @GetMapping("/api/storage/google/auth-url")
    public ResponseEntity<?> getAuthUrl() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String url = googleDriveService.getAuthorizationUrl(user);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/api/storage/google/status")
    public ResponseEntity<?> getGoogleStatus() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User dbUser = userRepository.findById(user.getId()).orElse(user);
        boolean linked = dbUser.getGoogleRefreshToken() != null;
        return ResponseEntity.ok(Map.of("linked", linked, "email", dbUser.getEmail()));
    }

    @GetMapping("/oauth/callback")
    public void oauthCallback(@RequestParam("code") String code,
                              @RequestParam("state") String state,
                              HttpServletResponse response) throws IOException {
        try {
            UUID userId = UUID.fromString(state);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            googleDriveService.exchangeCodeForTokens(code, user);
            response.sendRedirect("/index.html?gdrive=connected");
        } catch (Exception e) {
            response.sendRedirect("/index.html?gdrive=error&message=" + e.getMessage());
        }
    }
}
