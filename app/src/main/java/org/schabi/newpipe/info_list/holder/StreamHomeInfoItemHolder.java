package org.schabi.newpipe.info_list.holder;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.image.CoilHelper;

/**
 * Video card used on the home page. It follows the YouTube home feed look: rounded 16:9
 * thumbnail, channel avatar, title, a single "channel • views • age" line and a three dot menu.
 */
public class StreamHomeInfoItemHolder extends StreamInfoItemHolder {
    private final ImageView itemUploaderAvatar;
    private final View itemMenuButton;

    public StreamHomeInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                    final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_stream_youtube_item, parent);
        itemUploaderAvatar = itemView.findViewById(R.id.itemUploaderAvatar);
        itemMenuButton = itemView.findViewById(R.id.itemMenuButton);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        super.updateFromItem(infoItem, historyRecordManager);

        if (!(infoItem instanceof StreamInfoItem)) {
            return;
        }
        final StreamInfoItem item = (StreamInfoItem) infoItem;

        final String uploader = item.getUploaderName();
        final CharSequence details = itemAdditionalDetails.getText();
        if (TextUtils.isEmpty(uploader)) {
            itemAdditionalDetails.setText(details);
        } else if (TextUtils.isEmpty(details)) {
            itemAdditionalDetails.setText(uploader);
        } else {
            itemAdditionalDetails.setText(Localization.concatenateStrings(uploader,
                    details.toString()));
        }

        CoilHelper.INSTANCE.loadAvatar(itemUploaderAvatar, item.getUploaderAvatars());

        final String uploaderUrl = item.getUploaderUrl();
        if (TextUtils.isEmpty(uploaderUrl)) {
            itemUploaderAvatar.setOnClickListener(null);
            itemUploaderAvatar.setClickable(false);
        } else {
            itemUploaderAvatar.setOnClickListener(view ->
                    NavigationHelper.openChannelFragmentUsingIntent(itemBuilder.getContext(),
                            item.getServiceId(), uploaderUrl,
                            uploader == null ? "" : uploader));
        }

        itemMenuButton.setOnClickListener(view -> {
            if (itemBuilder.getOnStreamSelectedListener() != null) {
                itemBuilder.getOnStreamSelectedListener().held(item);
            }
        });
    }
}
