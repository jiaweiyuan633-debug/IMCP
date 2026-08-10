package com.example.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpAuthFilterTest {

    private McpAuthFilter filter(String token, MockEnvironment environment) {
        return new McpAuthFilter(token, new ObjectMapper(), environment);
    }

    @Test
    void allowsValidTokenOnMcpEndpoint() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        McpAuthFilter filter = filter("secret-mcp-token", env);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mcp");
        request.addHeader("Authorization", "Bearer secret-mcp-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsMissingToken() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        McpAuthFilter filter = filter("secret-mcp-token", env);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mcp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
    }

    @Test
    void rejectsWrongToken() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        McpAuthFilter filter = filter("secret-mcp-token", env);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mcp");
        request.addHeader("Authorization", "Bearer wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
    }

    @Test
    void rejectsDevFallbackTokenOutsideDev() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        McpAuthFilter filter = filter("dev-mcp-token", env);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mcp");
        request.addHeader("Authorization", "Bearer dev-mcp-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
    }

    @Test
    void passesThroughNonMcpPaths() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        McpAuthFilter filter = filter("secret-mcp-token", env);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/captcha");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(200, response.getStatus());
    }
}
