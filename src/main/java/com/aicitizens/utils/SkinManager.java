
package com.aicitizens.utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkinManager {

    private static final String MOJANG_PROFILE =
            "https://api.mojang.com/users/profiles/minecraft/";

    private static final String MOJANG_SESSION =
            "https://sessionserver.mojang.com/session/minecraft/profile/";

    private final HttpClient httpClient;

    private final Map<String, SkinData> cache =
            new ConcurrentHashMap<>();

    public SkinManager() {

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public CompletableFuture<SkinData> getSkin(String username) {

        if (username == null
                || !username.matches("[A-Za-z0-9_]{3,16}")) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Invalid Minecraft username.")
            );
        }

        String key = username.toLowerCase(Locale.ROOT);

        SkinData cached = cache.get(key);

        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return getProfileUUID(username)
                .thenCompose(uuid -> getProfileSkin(uuid))
                .thenApply(skin -> {
                    cache.put(key, skin);
                    return skin;
                });
    }

    private CompletableFuture<String> getProfileUUID(String username) {

        String url = MOJANG_PROFILE + username;

        return sendRequest(url)
                .thenApply(json -> {

                    String uuid = extract(json, "id");

                    if (uuid == null || uuid.isBlank()) {
                        throw new IllegalStateException(
                                "Minecraft profile was not found."
                        );
                    }

                    return uuid;
                });
    }

    private CompletableFuture<SkinData> getProfileSkin(String uuid) {

        String url = MOJANG_SESSION + uuid + "?unsigned=false";

        return sendRequest(url)
                .thenApply(json -> {

                    String value = extract(json, "value");
                    String signature = extract(json, "signature");

                    if (value == null || value.isBlank()) {
                        throw new IllegalStateException(
                                "Skin texture data was not found."
                        );
                    }

                    return new SkinData(
                            uuid,
                            value,
                            signature
                    );
                });
    }

    private CompletableFuture<String> sendRequest(String url) {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "AICitizens/1.0")
                .GET()
                .build();

        return httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(response -> {

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Mojang API returned HTTP "
                                + response.statusCode()
                );
            }

            return response.body();
        });
    }

    private String extract(String json, String key) {

        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key)
                        + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
        );

        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1);
    }

    public void clearCache() {
        cache.clear();
    }

    public static class SkinData {

        private final String uuid;
        private final String texture;
        private final String signature;

        public SkinData(
                String uuid,
                String texture,
                String signature
        ) {
            this.uuid = uuid;
            this.texture = texture;
            this.signature = signature;
        }

        public String getUuid() {
            return uuid;
        }

        public String getTexture() {
            return texture;
        }

        public String getSignature() {
            return signature;
        }
    }
}
