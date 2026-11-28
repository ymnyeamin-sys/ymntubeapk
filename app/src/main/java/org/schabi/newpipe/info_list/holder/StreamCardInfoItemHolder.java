package org.schabi.newpipe.info_list.holder;

import android.widget.ImageView;
import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.image.CoilHelper;

/**
 * YouTube-style full-width card used by the Home feed.
 */
public class StreamCardInfoItemHolder extends StreamInfoItemHolder {
    private final ImageView itemUploaderAvatarView;

    public StreamCardInfoItemHolder(final InfoItemBuilder infoItemBuilder, final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_stream_card_item, parent);
        itemUploaderAvatarView = itemView.findViewById(R.id.itemUploaderAvatarView);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        super.updateFromItem(infoItem, historyRecordManager);

        if (infoItem instanceof StreamInfoItem) {
            final StreamInfoItem item = (StreamInfoItem) infoItem;
            CoilHelper.INSTANCE.loadAvatar(itemUploaderAvatarView, item.getUploaderAvatars());
        }
    }
}
