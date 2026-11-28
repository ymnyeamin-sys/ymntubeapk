package org.schabi.newpipe.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.core.Single;

public class DownloadLinkInputTest {
    @Test
    public void normalizesSupportedWebLinkWithoutChangingItsContents() {
        assertEquals("https://youtu.be/example?t=30",
                DownloadLinkInput.normalize("  https://youtu.be/example?t=30  "));
    }

    @Test
    public void rejectsEmptyMalformedAndNonWebLinks() {
        assertNull(DownloadLinkInput.normalize(null));
        assertNull(DownloadLinkInput.normalize("  "));
        assertNull(DownloadLinkInput.normalize("ftp://example.com/video"));
        assertNull(DownloadLinkInput.normalize("https:///missing-host"));
        assertNull(DownloadLinkInput.normalize("not a URL"));
    }

    @Test
    public void fetchPassesValidatedUrlToExistingStreamResolver() {
        final AtomicReference<String> requestedUrl = new AtomicReference<>();
        DownloadLinkInput.fetch(" https://example.com/watch?v=1 ", url -> {
            requestedUrl.set(url);
            return Single.just("stream-info");
        }).test().assertValue("stream-info").assertComplete();

        assertEquals("https://example.com/watch?v=1", requestedUrl.get());
    }

    @Test
    public void invalidLinkNeverReachesStreamResolver() {
        final AtomicReference<String> requestedUrl = new AtomicReference<>();
        DownloadLinkInput.fetch("javascript:alert(1)", url -> {
            requestedUrl.set(url);
            return Single.just("stream-info");
        }).test().assertError(DownloadLinkInput.InvalidDownloadLinkException.class);

        assertNull(requestedUrl.get());
    }
}
