package com.layer.atlas.messagetypes.singlepartimage;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.layer.atlas.R;
import com.layer.atlas.messagetypes.AtlasCellFactory;
import com.layer.atlas.provider.ParticipantProvider;
import com.layer.atlas.util.imagepopup.AtlasImagePopupActivity;
import com.layer.atlas.util.picasso.transformations.RoundedTransform;
import com.layer.sdk.LayerClient;
import com.layer.sdk.messaging.Message;
import com.layer.sdk.messaging.MessagePart;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Transformation;

import java.lang.ref.WeakReference;

/**
 * BasicImage handles non-ThreePartImage images.  It relies on the ThreePartImage RequestHandler and does not handle image rotation.
 */
public class SinglePartImageCellFactory extends AtlasCellFactory<SinglePartImageCellFactory.CellHolder, SinglePartImageCellFactory.PartId> implements View.OnClickListener {
    private static final String PICASSO_TAG = SinglePartImageCellFactory.class.getSimpleName();
    private static final int PLACEHOLDER = com.layer.atlas.R.drawable.atlas_message_item_cell_placeholder;

    private final WeakReference<Activity> mActivity;
    private final LayerClient mLayerClient;
    private final Picasso mPicasso;
    private final Transformation mTransform;

    public SinglePartImageCellFactory(Activity activity, LayerClient layerClient, Picasso picasso) {
        super(256 * 1024);
        mActivity = new WeakReference<Activity>(activity);
        mLayerClient = layerClient;
        mPicasso = picasso;
        float radius = activity.getResources().getDimension(com.layer.atlas.R.dimen.atlas_message_item_cell_radius);
        mTransform = new RoundedTransform(radius);
    }

    @Override
    public boolean isBindable(Message message) {
        return SinglePartImageCellFactory.isType(message);
    }

    @Override
    public CellHolder createCellHolder(ViewGroup cellView, boolean isMe, LayoutInflater layoutInflater) {
        View view = layoutInflater.inflate(R.layout.atlas_message_item_cell_image, cellView, true);

        TextView time = (TextView) view.findViewById(R.id.cell_time);
        time.setTypeface(isMe ? mMessageStyle.getMyTextTypeface() : mMessageStyle.getOtherTextTypeface(), isMe ? mMessageStyle.getMyTextStyle() : mMessageStyle.getOtherTextStyle());
        time.setTextColor(time.getContext().getResources().getColor(R.color.grey_light));

        return new CellHolder(view);
    }

    @Override
    public void bindCellHolder(final CellHolder cellHolder, PartId index, Message message, CellHolderSpecs specs) {
        cellHolder.mImageView.setTag(index);
        cellHolder.mImageView.setOnClickListener(this);
        cellHolder.mProgressBar.show();
        mPicasso.load(index.mId).tag(PICASSO_TAG).placeholder(PLACEHOLDER)
                .centerInside().resize(specs.maxWidth, specs.maxHeight).onlyScaleDown()
                .transform(mTransform).into(cellHolder.mImageView, new Callback() {
            @Override
            public void onSuccess() {
                cellHolder.mProgressBar.hide();
            }

            @Override
            public void onError() {
                cellHolder.mProgressBar.hide();
            }
        });

        if (message.getSentAt() != null) {
            String relativeDate = ((String) DateUtils.getRelativeTimeSpanString(message.getSentAt().getTime(),System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS));
            cellHolder.mTime.setText(relativeDate);
            cellHolder.mTime.setVisibility(View.VISIBLE);
        } else {
            cellHolder.mTime.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View v) {
        AtlasImagePopupActivity.init(mLayerClient);
        Activity activity = mActivity.get();
        if (activity == null) return;
        Intent intent = new Intent(activity, AtlasImagePopupActivity.class);
        intent.putExtra("fullId", ((PartId) v.getTag()).mId);

        if (Build.VERSION.SDK_INT >= 21) {
            activity.startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(activity, v, "image").toBundle());
        } else {
            activity.startActivity(intent);
        }
    }

    @Override
    public void onScrollStateChanged(int newState) {
        switch (newState) {
            case RecyclerView.SCROLL_STATE_DRAGGING:
                mPicasso.pauseTag(PICASSO_TAG);
                break;
            case RecyclerView.SCROLL_STATE_IDLE:
            case RecyclerView.SCROLL_STATE_SETTLING:
                mPicasso.resumeTag(PICASSO_TAG);
                break;
        }
    }

    @Override
    public PartId parseContent(LayerClient layerClient, ParticipantProvider participantProvider, Message message) {
        for (MessagePart part : message.getMessageParts()) {
            if (part.getMimeType().startsWith("image/")) return new PartId(part.getId());
        }
        return null;
    }


    //==============================================================================================
    // Static utilities
    //==============================================================================================

    public static boolean isType(Message message) {
        for (MessagePart part : message.getMessageParts()) {
            if (part.getMimeType().startsWith("image/")) return true;
        }
        return false;
    }

    public static String getMessagePreview(Context context, Message message) {
        return context.getString(R.string.atlas_message_preview_image);
    }


    //==============================================================================================
    // Inner classes
    //==============================================================================================

    public static class CellHolder extends AtlasCellFactory.CellHolder {
        ImageView mImageView;
        ContentLoadingProgressBar mProgressBar;
        TextView mTime;

        public CellHolder(View view) {
            mImageView = (ImageView) view.findViewById(R.id.cell_image);
            mProgressBar = (ContentLoadingProgressBar) view.findViewById(R.id.cell_progress);
            mTime = (TextView) view.findViewById(R.id.cell_time);
        }
    }

    public static class PartId implements AtlasCellFactory.ParsedContent {
        public final Uri mId;

        public PartId(Uri id) {
            mId = id;
        }

        @Override
        public int sizeOf() {
            return mId.toString().getBytes().length;
        }
    }
}
