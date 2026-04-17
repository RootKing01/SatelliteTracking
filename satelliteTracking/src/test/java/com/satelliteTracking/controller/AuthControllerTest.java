package com.satelliteTracking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.satelliteTracking.dto.AuthLoginRequestDTO;
import com.satelliteTracking.dto.AuthRegisterRequestDTO;
import com.satelliteTracking.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Test
    public void testRegisterEndpoint() throws Exception {
        // Arrange
        AuthRegisterRequestDTO request = new AuthRegisterRequestDTO(
            "registeruser",
            "register@example.com",
            "password123"
        );

        // Act & Assert
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated", is(true)))
            .andExpect(jsonPath("$.user.email", equalTo("register@example.com")))
            .andExpect(jsonPath("$.token", nullValue())); // Token should not be in response body (HttpOnly cookie)
    }

    @Test
    public void testLoginEndpoint() throws Exception {
        // Arrange - first register a user
        AuthRegisterRequestDTO registerRequest = new AuthRegisterRequestDTO(
            "loginuser",
            "login@test.com",
            "password123"
        );
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isOk());

        // Act & Assert - login
        AuthLoginRequestDTO loginRequest = new AuthLoginRequestDTO(
            "login@test.com",
            "password123"
        );

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated", is(true)))
            .andExpect(cookie().exists("st_auth"))
                .andExpect(cookie().secure("st_auth", false))
            .andExpect(cookie().httpOnly("st_auth", true));
    }

    @Test
    public void testLoginInvalidCredentials() throws Exception {
        // Arrange
        AuthLoginRequestDTO loginRequest = new AuthLoginRequestDTO(
            "nonexistent@example.com",
            "password123"
        );

        // Act & Assert
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().is4xxClientError());
    }

    @Test
    public void testLogoutEndpoint() throws Exception {
        // Arrange - register and login first
        AuthRegisterRequestDTO registerRequest = new AuthRegisterRequestDTO(
            "logoutuser",
            "logout@test.com",
            "password123"
        );

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isOk())
            .andReturn();

        Cookie authCookie = registerResult.getResponse().getCookie("st_auth");
        assertNotNull(authCookie);

        // Act & Assert - logout
        mockMvc.perform(post("/api/auth/logout")
                .cookie(authCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated", is(false)))
            .andExpect(cookie().maxAge("st_auth", 0));
    }

    @Test
    public void testMeEndpointWithoutAuth() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated", is(false)));
    }

    @Test
    public void testMeEndpointWithValidToken() throws Exception {
        // Arrange - register a user to get a token
        AuthRegisterRequestDTO registerRequest = new AuthRegisterRequestDTO(
            "meuser",
            "me@test.com",
            "password123"
        );

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isOk())
            .andReturn();

        Cookie authCookie = registerResult.getResponse().getCookie("st_auth");
        assertNotNull(authCookie);

        // Act & Assert - get current user info
        mockMvc.perform(get("/api/auth/me")
            .cookie(authCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated", is(true)))
            .andExpect(jsonPath("$.user.email", equalTo("me@test.com")));
    }
}
