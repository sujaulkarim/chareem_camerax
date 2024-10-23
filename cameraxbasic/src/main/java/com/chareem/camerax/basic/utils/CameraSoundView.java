package com.chareem.camerax.basic.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.chareem.camerax.basic.R;
import com.chareem.camerax.basic.utils.Utils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by Arpit Gandhi on 6/24/16.
 */
public class CameraSoundView extends AppCompatImageButton {

    public static final int SOUND_TYPE_OFF = 0;
    public static final int SOUND_TYPE_ON = 1;
    private OnSoundTypeChangeListener onSoundTypeChangeListener;
    private Context context;
    private Drawable volumeOnDrawable;
    private Drawable volumeOffDrawable;
    private int padding = 5;
    private
    @SoundType
    int currentSoundType = SOUND_TYPE_ON;

    public CameraSoundView(Context context) {
        this(context, null);
    }

    public CameraSoundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        initializeView();
    }

    public CameraSoundView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs);
    }

    private void initializeView() {
        volumeOnDrawable = ContextCompat.getDrawable(context, R.drawable.ic_volume_up);
        volumeOnDrawable = DrawableCompat.wrap(volumeOnDrawable);

        volumeOffDrawable = ContextCompat.getDrawable(context, R.drawable.ic_volume_off);
        volumeOffDrawable = DrawableCompat.wrap(volumeOffDrawable);

        setBackgroundResource(R.drawable.circle_frame_background_dark);
        setOnClickListener(new SoundTypeClickListener());
        setIcons();
        padding = Utils.INSTANCE.convertDpToPixel(padding);
        setPadding(padding, padding, padding, padding);
    }

    private void setIcons() {
        if (currentSoundType == SOUND_TYPE_ON) {
            setImageDrawable(volumeOnDrawable);
        } else setImageDrawable(volumeOffDrawable);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (enabled) {
            setAlpha(1f);
        } else {
            setAlpha(0.5f);
        }
    }

    public
    @SoundType
    int getCameraType() {
        return currentSoundType;
    }

    public void setSoundType(@SoundType int soundType) {
        this.currentSoundType = soundType;
        setIcons();
    }

    public void setOnSoundTypeChangeListener(OnSoundTypeChangeListener onSoundTypeChangeListener) {
        this.onSoundTypeChangeListener = onSoundTypeChangeListener;
    }

    @IntDef({SOUND_TYPE_OFF, SOUND_TYPE_ON})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SoundType {
    }

    public interface OnSoundTypeChangeListener {
        void onSoundTypeChanged(@SoundType int soundType);
    }

    private class SoundTypeClickListener implements OnClickListener {

        @Override
        public void onClick(View view) {
            if (currentSoundType == SOUND_TYPE_ON) {
                currentSoundType = SOUND_TYPE_OFF;
            } else currentSoundType = SOUND_TYPE_ON;

            setIcons();

            if (onSoundTypeChangeListener != null)
                onSoundTypeChangeListener.onSoundTypeChanged(currentSoundType);
        }
    }
}
