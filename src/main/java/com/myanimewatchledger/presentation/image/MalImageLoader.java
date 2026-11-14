package com.myanimewatchledger.presentation.image;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Validates and asynchronously loads cover art returned by MyAnimeList. */
public final class MalImageLoader {
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "cdn.myanimelist.net",
            "api-cdn.myanimelist.net",
            "myanimelist.cdn-dena.com"
    );

    private MalImageLoader() { }

    /**
     * Returns the first syntactically valid MAL image URL, preserving the caller's preference order.
     */
    public static String selectUrl(String preferred, String fallback) {
        if (isAllowedUrl(preferred)) return preferred;
        return isAllowedUrl(fallback) ? fallback : null;
    }

    /**
     * Accepts only HTTPS URLs from known MAL image CDN hosts. User information and non-default
     * ports are rejected so metadata cannot direct the image loader to arbitrary endpoints.
     */
    public static boolean isAllowedUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            return uri.isAbsolute()
                    && "https".equalsIgnoreCase(uri.getScheme())
                    && host != null
                    && ALLOWED_HOSTS.contains(host.toLowerCase(Locale.ROOT))
                    && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /**
     * Loads the preferred URL off the JavaFX Application Thread and retries the fallback URL
     * if the preferred image cannot be decoded or downloaded.
     */
    public static void load(ImageView target, Label placeholder, String preferred, String fallback,
                            double fitWidth, double fitHeight) {
        String first = selectUrl(preferred, fallback);
        String second = isAllowedUrl(preferred) && isAllowedUrl(fallback)
                && !preferred.equals(fallback) ? fallback : null;
        loadCandidate(target, placeholder, first, second, fitWidth, fitHeight);
    }

    private static void loadCandidate(ImageView target, Label placeholder, String url, String fallback,
                                      double fitWidth, double fitHeight) {
        if (url == null) {
            target.setImage(null);
            placeholder.setVisible(true);
            return;
        }
        try {
            Image image = new Image(url, fitWidth, fitHeight, true, true, true);
            target.setImage(image);
            placeholder.setVisible(false);
            image.errorProperty().addListener((observable, oldError, hasError) -> {
                if (!hasError) return;
                Runnable retry = () -> {
                    if (target.getImage() != image) return;
                    if (fallback != null) {
                        loadCandidate(target, placeholder, fallback, null, fitWidth, fitHeight);
                    } else {
                        target.setImage(null);
                        placeholder.setVisible(true);
                    }
                };
                if (Platform.isFxApplicationThread()) retry.run();
                else Platform.runLater(retry);
            });
            if (image.isError()) {
                if (fallback != null) loadCandidate(target, placeholder, fallback, null, fitWidth, fitHeight);
                else {
                    target.setImage(null);
                    placeholder.setVisible(true);
                }
            }
        } catch (IllegalArgumentException invalid) {
            if (fallback != null) loadCandidate(target, placeholder, fallback, null, fitWidth, fitHeight);
            else {
                target.setImage(null);
                placeholder.setVisible(true);
            }
        }
    }
}
