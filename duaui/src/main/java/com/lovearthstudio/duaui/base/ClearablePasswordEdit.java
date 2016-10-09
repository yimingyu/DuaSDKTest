package com.lovearthstudio.duaui.base;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.lovearthstudio.duaui.R;

public class ClearablePasswordEdit extends LinearLayout  implements View.OnClickListener,View.OnFocusChangeListener{
    private PasswordEdit mContent;
    private Context mContext;
    private ImageView mIcon;
    private Drawable clear;
    private View line_focus;
    private TextWatcher mWatcher;

    private int defaultLineColor=Color.BLACK;
    private int defaultFocusColor=Color.RED;

    private LayoutParams focusParams;
    private LayoutParams unfocusParams;

    public ClearablePasswordEdit(Context context) {
        this(context,null);
    }
    public ClearablePasswordEdit(Context context, AttributeSet attrs) {
        this(context, attrs,0);
    }
    public ClearablePasswordEdit(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inflate(getContext(), R.layout.et_clearable_password, this);
        mContext = context;
        mContent=(PasswordEdit) findViewById(R.id.et_content);
        mIcon=(ImageView)findViewById(R.id.ic_clear);
        line_focus=findViewById(R.id.line_focus);
        init();
    }

    private void init() {
        clear= DrawableCompat.wrap(ContextCompat.getDrawable(mContext, R.drawable.abc_ic_clear_mtrl_alpha));
        DrawableCompat.setTint(clear, Color.RED);
        clear.setBounds(0, 0, clear.getIntrinsicHeight(), clear.getIntrinsicHeight());
        setClearIconVisible(false);
        mWatcher=new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if(mContent.isFocused()){
                    setClearIconVisible(s.length() > 0);
                }
            }
        };
        mContent.addTextChangedListener(mWatcher);
        mContent.setOnFocusChangeListener(this);
        mIcon.setOnClickListener(this);

        focusParams=new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,6);
        focusParams.setMargins(0,10,0,0);
        unfocusParams=new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,3);
        unfocusParams.setMargins(0,10,0,0);
    }
    private void setClearIconVisible( boolean visible) {
        if(visible){
            mIcon.setVisibility(VISIBLE);
            mIcon.setImageDrawable(clear);
        }else {
            mIcon.setVisibility(GONE);
        }
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.ic_clear) {
            mContent.setError(null);
            mContent.setText("");

        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        int i = v.getId();
        {
            if (hasFocus) {
                line_focus.setBackgroundColor(defaultFocusColor);
                line_focus.setLayoutParams(focusParams);
            } else {
                line_focus.setBackgroundColor(defaultLineColor);
                line_focus.setLayoutParams(unfocusParams);
            }
        }
    }
}
