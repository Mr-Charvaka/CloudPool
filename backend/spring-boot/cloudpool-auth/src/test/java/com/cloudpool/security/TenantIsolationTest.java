package com.cloudpool.security;

import com.cloudpool.model.User;
import com.cloudpool.model.enums.Role;
import com.cloudpool.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class TenantIsolationTest {

    @Autowired 
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Test
    public void userCannotSeeAnotherUsersProjectsViaSpoofedTenantHeader() throws Exception {
        User userA = registerUser("tenant-a@test.com");
        User userB = registerUser("tenant-b@test.com");

        String tokenA = jwtUtils.generateToken(userA.getEmail());
        String tokenB = jwtUtils.generateToken(userB.getEmail());

        // Tenant B creates a project that should never be visible to A
        mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"tenant-b-secret-project\", \"description\":\"secret\"}"))
            .andExpect(status().is2xxSuccessful());

        // Tenant A tries to see it by spoofing the header — must not appear
        mockMvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer " + tokenA)
                .header("X-Tenant-ID", userB.getId().toString())) // attempted spoof
            .andExpect(status().is2xxSuccessful())
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("tenant-b-secret-project"))));
    }

    private User registerUser(String email) { 
        User user = User.builder()
            .email(email)
            .name(email)
            .passwordHash("dummy-hash")
            .role(Role.USER)
            .build();
        return userRepository.save(user);
    }
}
