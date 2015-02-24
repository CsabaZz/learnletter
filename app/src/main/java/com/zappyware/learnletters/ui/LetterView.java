package com.zappyware.learnletters.ui;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;

import com.zappyware.learnletters.entities.Letter;

import java.util.ArrayList;

/**
 * Created by Csaba on 2015.02.24..
 */
public class LetterView extends View {

    private static final int MILLIS_PER_CIRCLE_ANIMATING = 700;
    private static final float DRAG_THRESHHOLD = 0.0f;
    private static final float HIT_FACTOR = 0.6f;

    private final int mDotSize;
    private final int mDotSizeActivated;
    private final int mPathActivated;

    private Aspect mAspect;
    private DisplayMode mMode;

    private Paint mDrawPaint;
    private Paint mPathPaint;

    private ArrayList<Letter> mLetters;

    private OnPatternListener mPatternListener;

    private float mInProgressX;
    private float mInProgressY;

    private float mSquareWidth;
    private float mSquareHeight;

    private boolean mStealthMode;

    public LetterView(Context context) {
        this(context, null, 0);
    }

    public LetterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LetterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mDotSize = 0;
        mDotSizeActivated = 0;
        mPathActivated = 0;
    }

    public OnPatternListener getOnPatternListener() {
        return mPatternListener;
    }

    public void setOnPatternListener(OnPatternListener l) {
        mPatternListener = l;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        return super.onSaveInstanceState();
    }

    protected static enum DisplayMode {
        Empty,
        Correct,
        Animating,
        Wrong
    }

    protected static enum Aspect {
        SQUARE,
        LOCK_WIDTH,
        LOCK_HEIGHT
    }

    public static interface OnPatternListener {
        void onPatternStarted();
        void onPatternEnded();
    }

    protected static class CellState {
        public float scale = 1.0f;
        public float translate = 0.0f;
        public float alpha = 1.0f;
        public float size;
        public float lineEndX = Float.NaN;
        public float lineEndY = Float.NaN;
        public ValueAnimator lineAnimator;
    }

    public static class SavedState extends BaseSavedState {

        public SavedState(Parcel source) {
            super(source);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }
    }
}
