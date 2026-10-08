package com.fixmate.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NetworkErrorAuthStatusTest {
    @Test
    public void only401IsRenderedAsSessionExpiry() {
        assertTrue(NetworkError.forHttp(401).contains("Session Expired"));
        assertFalse(NetworkError.forHttp(403).contains("Session Expired"));
        assertFalse(NetworkError.forHttp(400).contains("Session Expired"));
        assertFalse(NetworkError.forHttp(409).contains("Session Expired"));
        assertFalse(NetworkError.forHttp(500).contains("Session Expired"));
    }
}
