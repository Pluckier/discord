package uk.co.pluckier.discordbot.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    private static final Properties properties = new Properties();

    static {
        try (InputStream input = ConfigLoader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.err.println("CRITICAL: config.properties not found in resources folder!");
            } else {
                properties.load(input);
            }
        } catch (IOException ex) {
            System.err.println("CRITICAL: Failed to read config.properties file!");
            ex.printStackTrace();
        }
    }

    public static String getToken() {
        return properties.getProperty("discord.token");
    }

    public static String getChannelId() {
        return properties.getProperty("discord.channel.id");
    }

    public static String getWebSite() {
        return properties.getProperty("website.url");
    }

    public static String getFileExtension() {
        return properties.getProperty("file.extension");
    }

    public static String getResultsURL() {
        return properties.getProperty("results.url");
    }

    public static String getWebhookURL() {
        return properties.getProperty("webhook.url");
    }

    public static String getResultsWebhookURL() {
        return properties.getProperty("results.webhook.url");
    }

    public static String getStorageFile() {
        return properties.getProperty("storage.file");
    }
}