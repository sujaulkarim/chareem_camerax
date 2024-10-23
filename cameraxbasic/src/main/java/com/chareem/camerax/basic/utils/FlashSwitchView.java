package com.chareem.camerax.basic.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;

import com.chareem.camerax.basic.R;
import com.chareem.camerax.basic.utils.Utils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by Arpit Gandhi on 7/6/16.
 */
public class FlashSwitchView extends AppCompatImageButton {

    public static final int FLASH_ON = 1;
    public static final int FLASH_OFF = 0;
    public static final int FLASH_AUTO = 2;
    @FlashMode
    private int currentMode = FLASH_OFF;
    private FlashModeSwitchListener switchListener;
    private Drawable flashOnDrawable;
    private Drawable flashOffDrawable;
    private Drawable flashAutoDrawable;
    private int padding = 5;

    public FlashSwitchView(@NonNull Context context) {
        this(context, null);
    }

    public FlashSwitchView(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        flashOnDrawable = ContextCompat.getDrawable(context, R.drawable.ic_flash_on_white_24dp);
        flashOffDrawable = ContextCompat.getDrawable(context, R.drawable.ic_flash_off_white_24dp);
        flashAutoDrawable = ContextCompat.getDrawable(context, R.drawable.ic_flash_auto_white_24dp);
        init();
    }

    private void init() {
        setBackgroundResource(R.drawable.circle_frame_background_dark);
        setOnClickListener(new FlashButtonClickListener());
        setIcon();
        padding = Utils.INSTANCE.convertDpToPixel(padding);
        setPadding(padding, padding, padding, padding);
    }

    private void setIcon() {
        if (FLASH_OFF == currentMode) {
            setImageDrawable(flashOffDrawable);
        } else if (FLASH_ON == currentMode) {
            setImageDrawable(flashOnDrawable);
        } else setImageDrawable(flashAutoDrawable);

    }

    public void setFlashMode(@FlashMode int mode) {
        this.currentMode = mode;
        setIcon();
    }

    @FlashMode
    public int getCurrentFlashMode() {
        return currentMode;
    }

    public void setFlashSwitchListener(@NonNull FlashModeSwitchListener switchListener) {
        this.switchListener = switchListener;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (Build.VERSION.SDK_INT > 10) {
            if (enabled) {
                setAlpha(1f);
            } else {
                setAlpha(0.5f);
            }
        }
    }

    @IntDef({FLASH_ON, FLASH_OFF, FLASH_AUTO})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FlashMode {
    }

    public interface FlashModeSwitchListener {
        void onFlashModeChanged(@FlashMode int mode);
    }

    private class FlashButtonClickListener implements OnClickListener {

        @Override
        public void onClick(View v) {
            if (FLASH_AUTO == currentMode) {
                currentMode = FLASH_OFF;
            } else if (FLASH_OFF == currentMode) {
                currentMode = FLASH_ON;
            } else if (FLASH_ON == currentMode) {
                currentMode = FLASH_AUTO;
            }
            setIcon();
            if (switchListener != null) {
                switchListener.onFlashModeChanged(currentMode);
            }
        }
    }
}
