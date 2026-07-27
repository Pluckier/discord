package uk.co.pluckier.discordbot.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;

public class SharedHttpClient {

    // One single instance for your whole application
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // One single mapper instance for your whole application
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static HttpClient getClient() {
        return CLIENT;
    }

    public static ObjectMapper getMapper() {
        return MAPPER;
    }
}