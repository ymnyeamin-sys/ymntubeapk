package org.schabi.newpipe.fragments.list.recommendations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The (first page of the) home feed, wrapped into a {@link ListInfo} so that it can be used with
 * the regular list fragments.
 */
public final class HomeFeedInfo extends ListInfo<InfoItem> {
    public HomeFeedInfo(final int serviceId,
                        @NonNull final String name,
                        @NonNull final List<InfoItem> items,
                        @Nullable final Page nextPage) {
        super(serviceId,
                new ListLinkHandler("", "", "home", Collections.emptyList(), null),
                name);
        setRelatedItems(new ArrayList<>(items));
        setNextPage(nextPage);
    }
}
