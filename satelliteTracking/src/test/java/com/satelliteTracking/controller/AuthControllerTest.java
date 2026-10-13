package com.satelliteTracking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.satelliteTracking.dto.AuthLoginRequestDTO;
import com.satelliteTracking.dto.AuthRegisterRequestDTO;
import com.satelliteTracking.dto.CommunityThreadCreateRequestDTO;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
                .with(csrf())
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated", is(true)))
            .andExpect(jsonPath("$.user.email", equalTo("register@example.com")))
            .andExpect(jsonPath("$.token", nullValue())); // Token should not be in response body (HttpOnly cookie)
    }

    @Test
    public void testRegisterRequiresCsrfToken() throws Exception {
        AuthRegisterRequestDTO request = new AuthRegisterRequestDTO(
            "csrfuser",
            "csrf@example.com",
            "password123"
        );

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    public void testRegisterRejectsInvalidCsrfToken() throws Exception {
        AuthRegisterRequestDTO request = new AuthRegisterRequestDTO(
            "invalidcsrfuser",
            "invalidcsrf@example.com",
            "password123"
        );

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().useInvalidToken())
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    public void testCsrfEndpointIssuesCookie() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isNoContent())
            .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    public void testRegisterAcceptsCsrfCookieValueInHeader() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isNoContent())
            .andReturn();

        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);

        AuthRegisterRequestDTO request = new AuthRegisterRequestDTO(
            "realcsrfuser",
            "realcsrf@example.com",
            "password123"
        );

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
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
            .with(csrf())
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isOk());

        // Act & Assert - login
        AuthLoginRequestDTO loginRequest = new AuthLoginRequestDTO(
            "login@test.com",
            "password123"
        );

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
            .with(csrf())
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
            .with(csrf())
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
            .with(csrf())
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isOk())
            .andReturn();

        Cookie authCookie = registerResult.getResponse().getCookie("st_auth");
        assertNotNull(authCookie);

        // Act & Assert - logout
        mockMvc.perform(post("/api/auth/logout")
            .with(csrf())
                .cookie(authCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated", is(false)))
            .andExpect(cookie().maxAge("st_auth", 0));
    }

    @Test
    public void testLogoutRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isForbidden());
    }

    @Test
    public void testProtectedEndpointRejectsMissingJwt() throws Exception {
        mockMvc.perform(get("/api/satellites/groups-stats"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void testProtectedEndpointRejectsInvalidJwt() throws Exception {
        mockMvc.perform(get("/api/satellites/groups-stats")
                .cookie(new Cookie("st_auth", "not-a-valid-jwt")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void testProtectedEndpointAcceptsValidJwt() throws Exception {
        AuthRegisterRequestDTO request = new AuthRegisterRequestDTO(
            "protecteduser",
            "protected@example.com",
            "password123"
        );

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

        Cookie authCookie = registerResult.getResponse().getCookie("st_auth");
        assertNotNull(authCookie);

        mockMvc.perform(get("/api/satellites/groups-stats")
                .cookie(authCookie))
            .andExpect(status().isOk());
    }

    @Test
    public void testProtectedMutationRejectsValidJwtWithoutCsrf() throws Exception {
        Cookie authCookie = registerAndGetAuthCookie("protected-nocsrf");

        mockMvc.perform(post("/api/community/threads")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(authCookie)
                .content(objectMapper.writeValueAsString(new CommunityThreadCreateRequestDTO(
                    "Test thread senza CSRF",
                    "Il token JWT valido non sostituisce il token CSRF"
                ))))
            .andExpect(status().isForbidden());
    }

    @Test
    public void testProtectedMutationAcceptsValidJwtWithCsrf() throws Exception {
        Cookie authCookie = registerAndGetAuthCookie("protected-csrf");

        mockMvc.perform(post("/api/community/threads")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(authCookie)
                .with(csrf())
                .content(objectMapper.writeValueAsString(new CommunityThreadCreateRequestDTO(
                    "Test thread con CSRF",
                    "JWT e CSRF validi"
                ))))
            .andExpect(status().isOk());
    }

    private Cookie registerAndGetAuthCookie(String username) throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(new AuthRegisterRequestDTO(
                    username,
                    username + "@example.com",
                    "password123"
                ))))
            .andExpect(status().isOk())
            .andReturn();

        Cookie authCookie = registerResult.getResponse().getCookie("st_auth");
        assertNotNull(authCookie);
        return authCookie;
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
            .with(csrf())
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
