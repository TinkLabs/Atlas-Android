package com.layer.atlas.messagetypes.location;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.layer.atlas.R;
import com.layer.atlas.messagetypes.AtlasCellFactory;
import com.layer.atlas.provider.ParticipantProvider;
import com.layer.atlas.util.Log;
import com.layer.atlas.util.Util;
import com.layer.atlas.util.picasso.transformations.RoundedTransform;
import com.layer.sdk.LayerClient;
import com.layer.sdk.messaging.Message;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Transformation;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.net.URLEncoder;

public class LocationCellFactory extends AtlasCellFactory<LocationCellFactory.CellHolder, LocationCellFactory.Location> implements View.OnClickListener {
    private static final String PICASSO_TAG = LocationCellFactory.class.getSimpleName();
    public static final String MIME_TYPE = "location/coordinate";
    public static final String KEY_LATITUDE = "lat";
    public static final String KEY_LONGITUDE = "lon";
    public static final String KEY_LABEL = "label";

    private static final int PLACEHOLDER = R.drawable.atlas_message_item_cell_placeholder;
    private static final double GOLDEN_RATIO = (1.0 + Math.sqrt(5.0)) / 2.0;

    private final Picasso mPicasso;
    private final Transformation mTransform;

    private String mMapType;

    private WeakReference<Context> mContext;

    public LocationCellFactory(Context context, Picasso picasso, String mapType) {
        super(256 * 1024);
        mPicasso = picasso;
        float radius = context.getResources().getDimension(R.dimen.atlas_message_item_cell_radius);
        mTransform = new RoundedTransform(radius);
        this.mMapType = mapType;
        mContext = new WeakReference<>(context);
    }

    public static boolean isType(Message message) {
        return message.getMessageParts().get(0).getMimeType().equals(MIME_TYPE);
    }

    public static String getMessagePreview(Context context, Message message) {
        return context.getString(R.string.atlas_message_preview_location);
    }

    @Override
    public boolean isBindable(Message message) {
        return LocationCellFactory.isType(message);
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
    public Location parseContent(LayerClient layerClient, ParticipantProvider participantProvider, Message message) {
        try {
            JSONObject o = new JSONObject(new String(message.getMessageParts().get(0).getData()));
            Location c = new Location();
            c.mLatitude = o.optDouble(KEY_LATITUDE, 0);
            c.mLongitude = o.optDouble(KEY_LONGITUDE, 0);
            c.mLabel = o.optString(KEY_LABEL, null);
            return c;
        } catch (JSONException e) {
            if (Log.isLoggable(Log.ERROR)) {
                Log.e(e.getMessage(), e);
            }
        }
        return null;
    }

    @Override
    public void bindCellHolder(final CellHolder cellHolder, final Location location, Message message, CellHolderSpecs specs) {
        cellHolder.mImageView.setTag(location);
        cellHolder.mImageView.setOnClickListener(this);

        // Google Static Map API has max dimension 640
        int mapWidth = Math.min(640, specs.maxWidth);
        int mapHeight = (int) Math.round((double) mapWidth / GOLDEN_RATIO);
        int[] cellDims = Util.scaleDownInside(specs.maxWidth, (int) Math.round((double) specs.maxWidth / GOLDEN_RATIO), specs.maxWidth, specs.maxHeight);
        ViewGroup.LayoutParams params = cellHolder.mImageView.getLayoutParams();
        params.width = cellDims[0];
        params.height = cellDims[1];
        cellHolder.mProgressBar.show();
        mPicasso.load(getStaticMapUrl(location.mLatitude, location.mLongitude, mapWidth, mapHeight))
                .tag(PICASSO_TAG).placeholder(PLACEHOLDER).resize(cellDims[0], cellDims[1])
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
            String relativeDate = ((String) DateUtils.getRelativeTimeSpanString(message.getSentAt().getTime(), System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS));
            cellHolder.mTime.setText(relativeDate);
            cellHolder.mTime.setVisibility(View.VISIBLE);

            if (specs.isMe) {
                ((LinearLayout.LayoutParams) cellHolder.mTime.getLayoutParams()).gravity = Gravity.END;
            } else {
                ((LinearLayout.LayoutParams) cellHolder.mTime.getLayoutParams()).gravity = Gravity.START;
            }
        } else {
            cellHolder.mTime.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View v) {
        Location location = (Location) v.getTag();
        String encodedLabel = (location.mLabel == null) ? URLEncoder.encode("Shared Marker") : URLEncoder.encode(location.mLabel);
        Intent intent;
        Context context = v.getContext();
        if ("baidu".equals(mMapType)) {
            intent = new Intent();
            intent.setData(Uri.parse("baidumap://map/marker?location=" + location.mLatitude + "," + location.mLongitude + "&title=Pinned Point"));
            try {
                context.startActivity(intent);
                return;
            } catch (ActivityNotFoundException e) {
                //ignore baidu map app not install.
            }
        }
        intent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + location.mLatitude + "," + location.mLongitude + "(" + encodedLabel + ")&z=16"));
        context.startActivity(intent);
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

    static class Location implements AtlasCellFactory.ParsedContent {
        double mLatitude;
        double mLongitude;
        String mLabel;

        @Override
        public int sizeOf() {
            return (mLabel == null ? 0 : mLabel.getBytes().length) + ((Double.SIZE + Double.SIZE) / Byte.SIZE);
        }
    }

    static class CellHolder extends AtlasCellFactory.CellHolder {
        ImageView mImageView;
        ContentLoadingProgressBar mProgressBar;
        TextView mTime;

        public CellHolder(View view) {
            mImageView = (ImageView) view.findViewById(R.id.cell_image);
            mProgressBar = (ContentLoadingProgressBar) view.findViewById(R.id.cell_progress);
            mTime = (TextView) view.findViewById(R.id.cell_time);
        }
    }

    public String getStaticMapUrl(double latitude, double longitude, int mapWidth, int mapHeight) {
        String staticMapUrl;
        if ("baidu".equals(mMapType)) {
            staticMapUrl = "http://api.map.baidu.com/staticimage?center=" + longitude + "," + latitude + "&markers=" + longitude + "," + latitude;
        } else {
            staticMapUrl = "https://maps.googleapis.com/maps/api/staticmap?zoom=16&maptype=roadmap&scale=2&center=" + latitude + "," + longitude + "&markers=color:red%7C" + latitude + "," + longitude + "&size=" + mapWidth + "x" + mapHeight;
        }

        return staticMapUrl;
    }
}
