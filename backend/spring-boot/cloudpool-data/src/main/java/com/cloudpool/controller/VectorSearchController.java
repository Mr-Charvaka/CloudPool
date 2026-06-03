package com.cloudpool.controller;

import com.cloudpool.model.User;
import com.cloudpool.service.VectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vector")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class VectorSearchController {

    private final VectorService vectorService;

    private User getAuthenticatedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam("q") String query) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(vectorService.search(query, user));
    }
}
