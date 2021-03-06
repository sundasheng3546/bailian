package com.ydd.zhichat.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;

import com.ydd.zhichat.R;
import com.ydd.zhichat.util.SkinUtils;

/**
 * 用于切换深浅皮肤时切换两套图标，
 */
public class LightImageView extends AppCompatImageView {
    public LightImageView(Context context) {
        super(context);
    }

    public LightImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public LightImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        SkinUtils.Skin skin = SkinUtils.getSkin(context);
        if (!skin.isLight()) {
            return;
        }
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.LightImageView);

        if (ta.hasValue(R.styleable.LightImageView_srcLight)) {
            setImageDrawable(ta.getDrawable(R.styleable.LightImageView_srcLight));
        }
        ta.recycle();
    }
}
