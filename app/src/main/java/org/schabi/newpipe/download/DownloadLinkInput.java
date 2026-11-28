// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.download;

import androidx.annotation.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Function;

import io.reactivex.rxjava3.core.Single;

public final class DownloadLinkInput {
    private DownloadLinkInput() {
    }

    @Nullable
    public static String normalize(@Nullable final String input) {
        if (input == null) {
            return null;
        }

        final String url = input.trim();
        if (url.isEmpty()) {
            return null;
        }

        try {
            final URI uri = new URI(url);
            final String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return null;
            }
            return url;
        } catch (final URISyntaxException e) {
            return null;
        }
    }

    public static <T> Single<T> fetch(
            @Nullable final String input,
            final Function<String, Single<T>> fetcher) {
        final String url = normalize(input);
        if (url == null) {
            return Single.error(new InvalidDownloadLinkException());
        }
        return Single.defer(() -> fetcher.apply(url));
    }

    public static final class InvalidDownloadLinkException extends IllegalArgumentException {
        public InvalidDownloadLinkException() {
            super("A valid HTTP or HTTPS link is required");
        }
    }

    public static final class UnsupportedDownloadLinkException extends IllegalArgumentException {
        public UnsupportedDownloadLinkException() {
            super("This source is not supported for downloads");
        }

        public UnsupportedDownloadLinkException(final Throwable cause) {
            super("This source is not supported for downloads", cause);
        }
    }
}
