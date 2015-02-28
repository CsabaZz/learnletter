package com.zappyware.learnletters.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Debug;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

import com.zappyware.learnletters.R;
import com.zappyware.learnletters.entities.Letter;
import com.zappyware.learnletters.entities.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;


public class LetterView extends View {

    private static final int MILLIS_PER_CIRCLE_ANIMATING = 700;
    private static final float DRAG_THRESHHOLD = 0.0f;
    private static final float DIVISION = 20.0f;
    private static final float HIT_FACTOR = 1.5f;
    private static final boolean PROFILE_DRAWING = false;

    private final int mDotSize;
    private final int mDotSizeActivated;
    private final int mPathWidth;

    private boolean mDrawingProfilingStarted = false;

    private Aspect mAspect;
    private DisplayMode mMode;

    private final Paint mDrawPaint = new Paint();
    private final Paint mPathPaint = new Paint();

    private Letter mLetter;

    private final ArrayList<Point> mPoints = new ArrayList<>();
    private final HashMap<Point, CellState> mPointStates = new HashMap<>();
    private final HashMap<Point, Boolean> mPatternDrawLookup = new HashMap<>();

    private OnPatternListener mPatternListener;

    private float mInProgressX;
    private float mInProgressY;

    private float mSquareWidth;
    private float mSquareHeight;

    private long mAnimatingPeriodStart;
    private boolean mStealthMode;

    private boolean mInputEnabled = true;
    private boolean mInStealthMode = false;
    private boolean mEnableHapticFeedback = true;
    private boolean mPatternInProgress = false;

    private final Path mCurrentPath = new Path();
    private final Rect mInvalidate = new Rect();
    private final Rect mTmpInvalidateRect = new Rect();

    private int mRegularColor;
    private int mErrorColor;
    private int mSuccessColor;

    private Interpolator mFastOutSlowInInterpolator;
    private Interpolator mLinearOutSlowInInterpolator;

    public LetterView(Context context) {
        this(context, null, 0);
    }

    public LetterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LetterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.LetterView);

        final int aspectIndex = a.getInt(R.styleable.LetterView_aspect, 0);
        mAspect = Aspect.values()[aspectIndex];

        setClickable(true);

        mPathPaint.setAntiAlias(true);
        mPathPaint.setDither(true);

        mRegularColor = getResources().getColor(R.color.lock_pattern_view_regular_color);
        mErrorColor = getResources().getColor(R.color.lock_pattern_view_error_color);
        mSuccessColor = getResources().getColor(R.color.lock_pattern_view_success_color);
        mRegularColor = a.getColor(R.styleable.LetterView_regularColor, mRegularColor);
        mErrorColor = a.getColor(R.styleable.LetterView_errorColor, mErrorColor);
        mSuccessColor = a.getColor(R.styleable.LetterView_successColor, mSuccessColor);

        int pathColor = a.getColor(R.styleable.LetterView_pathColor, mRegularColor);
        mPathPaint.setColor(pathColor);

        mPathPaint.setStyle(Paint.Style.STROKE);
        mPathPaint.setStrokeJoin(Paint.Join.ROUND);
        mPathPaint.setStrokeCap(Paint.Cap.ROUND);

        mPathWidth = getResources().getDimensionPixelSize(R.dimen.lock_pattern_dot_line_width);
        mPathPaint.setStrokeWidth(mPathWidth);

        mDotSize = getResources().getDimensionPixelSize(R.dimen.lock_pattern_dot_size);
        mDotSizeActivated = getResources().getDimensionPixelSize(
                R.dimen.lock_pattern_dot_size_activated);

        mDrawPaint.setAntiAlias(true);
        mDrawPaint.setDither(true);

        mFastOutSlowInInterpolator =
                AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in);
        mLinearOutSlowInInterpolator =
                AnimationUtils.loadInterpolator(context, android.R.interpolator.linear_out_slow_in);
    }

    public OnPatternListener getOnPatternListener() {
        return mPatternListener;
    }

    public void setOnPatternListener(OnPatternListener l) {
        mPatternListener = l;
    }

    public HashMap<Point, CellState> getCellStates() {
        return mPointStates;
    }
    
    public boolean isInStealthMode() {
        return mInStealthMode;
    }
    
    public boolean isTactileFeedbackEnabled() {
        return mEnableHapticFeedback;
    }
    
    public void setInStealthMode(boolean inStealthMode) {
        mInStealthMode = inStealthMode;
    }

    public void setTactileFeedbackEnabled(boolean tactileFeedbackEnabled) {
        mEnableHapticFeedback = tactileFeedbackEnabled;
    }

    public void setPattern(DisplayMode displayMode, List<Point> points) {
        mPoints.clear();
        mPoints.addAll(points);

        clearPatternDrawLookup();

        CellState state;
        for (Point point : points) {
            state = new CellState();
            state.size = mDotSize;

            mPointStates.put(point, state);
        }

        setDisplayMode(displayMode);
    }

    public void setDisplayMode(DisplayMode displayMode) {
        mMode = displayMode;
        if (displayMode == DisplayMode.Animate) {
            if (mPoints.size() == 0) {
                throw new IllegalStateException("you must have a pattern to "
                        + "animate if you want to set the display mode to animate");
            }
            mAnimatingPeriodStart = SystemClock.elapsedRealtime();
            final Point first = mPoints.get(0);
            mInProgressX = getCenterXForColumn(first.x);
            mInProgressY = getCenterYForRow(first.y);
            clearPatternDrawLookup();
        }
        invalidate();
    }

    private void notifyCellAdded() {
        if (mPatternListener != null) {
            mPatternListener.onPatternCellAdded(mPoints);
        }
    }

    private void notifyPatternStarted() {
        if (mPatternListener != null) {
            mPatternListener.onPatternStart();
        }
    }

    private void notifyPatternDetected() {
        if (mPatternListener != null) {
            mPatternListener.onPatternDetected(mPoints);
        }
    }

    private void notifyPatternCleared() {
        if (mPatternListener != null) {
            mPatternListener.onPatternCleared();
        }
    }
    
    public void clearPattern() {
        resetPattern();
    }
    
    private void resetPattern() {
        mLetter = new Letter();
        clearPatternDrawLookup();
        mMode = DisplayMode.Correct;
        invalidate();
    }

    private void clearPatternDrawLookup() {
        mPatternDrawLookup.clear();
        for(Point point : mPoints) {
            mPatternDrawLookup.put(point, Boolean.FALSE);
        }
    }

    public void disableInput() {
        mInputEnabled = false;
    }
    
    public void enableInput() {
        mInputEnabled = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        final int width = w - getPaddingLeft() - getPaddingRight();
        mSquareWidth = width / DIVISION;

        final int height = h - getPaddingTop() - getPaddingBottom();
        mSquareHeight = height / DIVISION;
    }

    private int resolveMeasured(int measureSpec, int desired)
    {
        int result = 0;
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (MeasureSpec.getMode(measureSpec)) {
            case MeasureSpec.UNSPECIFIED:
                result = desired;
                break;
            case MeasureSpec.AT_MOST:
                result = Math.max(specSize, desired);
                break;
            case MeasureSpec.EXACTLY:
            default:
                result = specSize;
        }
        return result;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int minimumWidth = getSuggestedMinimumWidth();
        final int minimumHeight = getSuggestedMinimumHeight();
        int viewWidth = resolveMeasured(widthMeasureSpec, minimumWidth);
        int viewHeight = resolveMeasured(heightMeasureSpec, minimumHeight);

        switch (mAspect) {
            case SQUARE:
                viewWidth = viewHeight = Math.min(viewWidth, viewHeight);
                break;
            case LOCK_WIDTH:
                viewHeight = Math.min(viewWidth, viewHeight);
                break;
            case LOCK_HEIGHT:
                viewWidth = Math.min(viewWidth, viewHeight);
                break;
        }
        
        setMeasuredDimension(viewWidth, viewHeight);
    }

    
    private Point detectAndAddHit(float x, float y) {
        final Point point = checkForNewHit(x, y);
        if (point != null) {
            addCellToPattern(point);
            if (mEnableHapticFeedback) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                | HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }
            return point;
        }
        return null;
    }

    private void addCellToPattern(Point newCell) {
        mPatternDrawLookup.put(newCell,Boolean.TRUE);
        mPoints.add(newCell);
        if (!mInStealthMode) {
            startCellActivatedAnimation(newCell);
        }
        notifyCellAdded();
    }

    private void startCellActivatedAnimation(Point point) {
        final CellState cellState = mPointStates.get(point);
        startSizeAnimation(mDotSize, mDotSizeActivated, 96, mLinearOutSlowInInterpolator,
                cellState, new Runnable() {
                    @Override
                    public void run() {
                        startSizeAnimation(mDotSizeActivated, mDotSize, 192, mFastOutSlowInInterpolator,
                                cellState, null);
                    }
                });
        startLineEndAnimation(cellState, mInProgressX, mInProgressY,
                getCenterXForColumn(point.x), getCenterYForRow(point.y));
    }

    private void startLineEndAnimation(final CellState state,
                                       final float startX, final float startY, final float targetX, final float targetY) {
        state.lineStartX = startX;
        state.lineStartY = startY;

        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float t = (float) animation.getAnimatedValue();
                state.lineEndX = (1 - t) * startX + t * targetX;
                state.lineEndY = (1 - t) * startY + t * targetY;
                invalidate();
            }
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                state.lineAnimator = null;
            }
        });
        valueAnimator.setInterpolator(mFastOutSlowInInterpolator);
        valueAnimator.setDuration(100);
        valueAnimator.start();
        state.lineAnimator = valueAnimator;
    }

    private void startSizeAnimation(float start, float end, long duration, Interpolator interpolator,
                                    final CellState state, final Runnable endRunnable) {
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(start, end);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                state.size = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        if (endRunnable != null) {
            valueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    endRunnable.run();
                }
            });
        }
        valueAnimator.setInterpolator(interpolator);
        valueAnimator.setDuration(duration);
        valueAnimator.start();
    }
    
    private Point checkForNewHit(float x, float y) {
        float hitSize = mDotSize * 4f;

        Point point = null;
        RectF r;

        float pixelX;
        float pixelY;

        for(int i = 0; i < mPoints.size(); ++i) {
            point = mPoints.get(i);

            pixelX = getPaddingLeft() + getMeasuredWidth() * point.x;
            pixelY = getPaddingTop() + getMeasuredHeight() * point.y;

            r = new RectF();
            r.left = pixelX - hitSize;
            r.right = pixelX + hitSize;
            r.top = pixelY - hitSize;
            r.bottom = pixelY + hitSize;

            if(r.contains(x, y)) {
                break;
            } else {
                point = null;
            }
        }

        if(null == point) {
            return null;
        }

        if (mPatternDrawLookup.get(point)) {
            return null;
        } else {
            return point;
        }
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        AccessibilityManager accessibilityManager =
                (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (accessibilityManager.isTouchExplorationEnabled()) {
            final int action = event.getAction();
            switch (action) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    event.setAction(MotionEvent.ACTION_DOWN);
                    break;
                case MotionEvent.ACTION_HOVER_MOVE:
                    event.setAction(MotionEvent.ACTION_MOVE);
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                    event.setAction(MotionEvent.ACTION_UP);
                    break;
            }
            onTouchEvent(event);
            event.setAction(action);
        }
        return super.onHoverEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mInputEnabled || !isEnabled()) {
            return false;
        }

        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                handleActionDown(event);
                return true;
            case MotionEvent.ACTION_UP:
                handleActionUp(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                handleActionMove(event);
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (mPatternInProgress) {
                    mPatternInProgress = false;
                    resetPattern();
                    notifyPatternCleared();
                }
                if (PROFILE_DRAWING) {
                    if (mDrawingProfilingStarted) {
                        Debug.stopMethodTracing();
                        mDrawingProfilingStarted = false;
                    }
                }
                return true;
        }
        return false;
    }

    private void handleActionMove(MotionEvent event) {
        
        
        final float radius = mPathWidth;
        final int historySize = event.getHistorySize();
        mTmpInvalidateRect.setEmpty();
        boolean invalidateNow = false;
        for (int i = 0; i < historySize + 1; i++) {
            final float x = i < historySize ? event.getHistoricalX(i) : event.getX();
            final float y = i < historySize ? event.getHistoricalY(i) : event.getY();
            Point hitCell = detectAndAddHit(x, y);
            final int patternSize = mPoints.size();
            if (hitCell != null && patternSize == 1) {
                mPatternInProgress = true;
                notifyPatternStarted();
            }
            
            final float dx = Math.abs(x - mInProgressX);
            final float dy = Math.abs(y - mInProgressY);
            if (dx > DRAG_THRESHHOLD || dy > DRAG_THRESHHOLD) {
                invalidateNow = true;
            }

            if (mPatternInProgress && patternSize > 0) {
                final ArrayList<Point> pattern = mPoints;
                final Point lastCell = pattern.get(patternSize - 1);
                float lastCellCenterX = getCenterXForColumn(lastCell.x);
                float lastCellCenterY = getCenterYForRow(lastCell.y);

                
                float left = Math.min(lastCellCenterX, x) - radius;
                float right = Math.max(lastCellCenterX, x) + radius;
                float top = Math.min(lastCellCenterY, y) - radius;
                float bottom = Math.max(lastCellCenterY, y) + radius;

                
                if (hitCell != null) {
                    final float width = mSquareWidth * 0.5f;
                    final float height = mSquareHeight * 0.5f;
                    final float hitCellCenterX = getCenterXForColumn(hitCell.x);
                    final float hitCellCenterY = getCenterYForRow(hitCell.y);

                    left = Math.min(hitCellCenterX - width, left);
                    right = Math.max(hitCellCenterX + width, right);
                    top = Math.min(hitCellCenterY - height, top);
                    bottom = Math.max(hitCellCenterY + height, bottom);
                }

                
                mTmpInvalidateRect.union(Math.round(left), Math.round(top),
                        Math.round(right), Math.round(bottom));
            }
        }
        mInProgressX = event.getX();
        mInProgressY = event.getY();

        
        if (invalidateNow) {
            mInvalidate.union(mTmpInvalidateRect);
            invalidate(mInvalidate);
            mInvalidate.set(mTmpInvalidateRect);
        }
    }

    private void sendAccessEvent(int resId) {
        announceForAccessibility(getContext().getString(resId));
    }

    private void handleActionUp(MotionEvent event) {
        if (!mPoints.isEmpty()) {
            mPatternInProgress = false;
            cancelLineAnimations();
            notifyPatternDetected();
            invalidate();
        }

        if (PROFILE_DRAWING) {
            if (mDrawingProfilingStarted) {
                Debug.stopMethodTracing();
                mDrawingProfilingStarted = false;
            }
        }
    }

    private void cancelLineAnimations() {
        for(Point point : mPoints) {
            CellState state = mPointStates.get(point);
            if (state.lineAnimator != null) {
                state.lineAnimator.cancel();
                state.lineStartX = Float.MIN_VALUE;
                state.lineStartY = Float.MIN_VALUE;
                state.lineEndX = Float.MIN_VALUE;
                state.lineEndY = Float.MIN_VALUE;
            }
        }
    }
    private void handleActionDown(MotionEvent event) {
        resetPattern();
        final float x = event.getX();
        final float y = event.getY();
        final Point hitCell = detectAndAddHit(x, y);
        if (hitCell != null) {
            mPatternInProgress = true;
            mMode = DisplayMode.Correct;
            notifyPatternStarted();
        } else if (mPatternInProgress) {
            mPatternInProgress = false;
            notifyPatternCleared();
        }
        if (hitCell != null) {
            final float startX = getCenterXForColumn(hitCell.x);
            final float startY = getCenterYForRow(hitCell.y);

            final float widthOffset = 0;//mSquareWidth / 2f;
            final float heightOffset = 0;//mSquareHeight / 2f;

            invalidate((int) (startX - widthOffset), (int) (startY - heightOffset),
                    (int) (startX + widthOffset), (int) (startY + heightOffset));
        }
        mInProgressX = x;
        mInProgressY = y;
        if (PROFILE_DRAWING) {
            if (!mDrawingProfilingStarted) {
                Debug.startMethodTracing("LockPatternDrawing");
                mDrawingProfilingStarted = true;
            }
        }
    }

    private float getCenterXForColumn(float x) {
        return getPaddingLeft() + x * getMeasuredWidth();
    }

    private float getCenterYForRow(float y) {
        return getPaddingTop() + y * getMeasuredHeight();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final ArrayList<Point> pattern = mPoints;
        final int count = pattern.size();
        final HashMap<Point, Boolean> drawLookup = mPatternDrawLookup;

        if (mMode == DisplayMode.Animate) {
            final int oneCycle = (count + 1) * MILLIS_PER_CIRCLE_ANIMATING;
            final int spotInCycle = (int) (SystemClock.elapsedRealtime() -
                    mAnimatingPeriodStart) % oneCycle;
            final int numCircles = spotInCycle / MILLIS_PER_CIRCLE_ANIMATING;

            clearPatternDrawLookup();
            for (int i = 0; i < numCircles; i++) {
                final Point point = pattern.get(i);
                drawLookup.put(point, Boolean.TRUE);
            }

            final boolean needToUpdateInProgressPoint = numCircles > 0
                    && numCircles < count;

            if (needToUpdateInProgressPoint) {
                final float percentageOfNextCircle =
                        ((float) (spotInCycle % MILLIS_PER_CIRCLE_ANIMATING)) /
                                MILLIS_PER_CIRCLE_ANIMATING;

                final Point currentCell = pattern.get(numCircles - 1);
                final float centerX = getCenterXForColumn(currentCell.x);
                final float centerY = getCenterYForRow(currentCell.y);

                final Point nextCell = pattern.get(numCircles);
                final float dx = percentageOfNextCircle *
                        (getCenterXForColumn(nextCell.x) - centerX);
                final float dy = percentageOfNextCircle *
                        (getCenterYForRow(nextCell.y) - centerY);
                mInProgressX = centerX + dx;
                mInProgressY = centerY + dy;
            }
            
            invalidate();
        }

        final Path currentPath = mCurrentPath;
        currentPath.rewind();

        for(Point point : mPoints) {
            CellState cellState = mPointStates.get(point);

            float centerX = getCenterXForColumn(point.x);
            float centerY = getCenterYForRow(point.y);

            float size = cellState.size * cellState.scale;
            float translationY = cellState.translate;

            drawCircle(canvas, (int) centerX, (int) centerY + translationY,
                    size, drawLookup.get(point), cellState.alpha);
        }
        
        final boolean drawPath = !mInStealthMode;

        if (drawPath) {
            mPathPaint.setColor(getCurrentColor(true ));

            boolean anyCircles = false;
            float lastX = 0f;
            float lastY = 0f;
            for (int i = 0; i < count; i++) {
                Point point = pattern.get(i);

                if (!drawLookup.get(point)) {
                    break;
                }
                anyCircles = true;

                float centerX = getCenterXForColumn(point.x);
                float centerY = getCenterYForRow(point.y);
                if (i != 0) {
                    CellState state = mPointStates.get(point);
                    currentPath.rewind();
                    currentPath.moveTo(state.lineStartX, state.lineStartY);
                    if (state.lineEndX != Float.MIN_VALUE && state.lineEndY != Float.MIN_VALUE) {
                        currentPath.lineTo(state.lineEndX, state.lineEndY);
                    } else {
                        currentPath.lineTo(centerX, centerY);
                    }
                    canvas.drawPath(currentPath, mPathPaint);
                }
                lastX = centerX;
                lastY = centerY;
            }

            
            if ((mPatternInProgress || mMode == DisplayMode.Animate)
                    && anyCircles) {
                currentPath.rewind();
                currentPath.moveTo(lastX, lastY);
                currentPath.lineTo(mInProgressX, mInProgressY);

                mPathPaint.setAlpha((int) (calculateLastSegmentAlpha(
                        mInProgressX, mInProgressY, lastX, lastY) * 255f));
                canvas.drawPath(currentPath, mPathPaint);
            }
        }
    }

    private float calculateLastSegmentAlpha(float x, float y, float lastX, float lastY) {
        float diffX = x - lastX;
        float diffY = y - lastY;
        float dist = (float) Math.sqrt(diffX*diffX + diffY*diffY);
        float frac = dist/mSquareWidth;
        return Math.min(1f, Math.max(0f, (frac - 0.3f) * 4f));
    }

    private int getCurrentColor(boolean partOfPattern) {
        if (!partOfPattern || mInStealthMode || mPatternInProgress) {
            
            return mRegularColor;
        } else if (mMode == DisplayMode.Wrong) {
            
            return mErrorColor;
        } else if (mMode == DisplayMode.Correct ||
                mMode == DisplayMode.Animate) {
            return mSuccessColor;
        } else {
            throw new IllegalStateException("unknown display mode " + mMode);
        }
    }

    
    private void drawCircle(Canvas canvas, float centerX, float centerY, float size,
                            boolean partOfPattern, float alpha) {
        mDrawPaint.setColor(getCurrentColor(partOfPattern));
        mDrawPaint.setAlpha((int) (alpha * 255));
        canvas.drawCircle(centerX, centerY, size/2, mDrawPaint);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return new SavedState(superState,
                LetterViewUtils.patternToString(mPoints),
                mMode.ordinal(),
                mInputEnabled, mInStealthMode, mEnableHapticFeedback);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        final SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setPattern(
                DisplayMode.Correct,
                LetterViewUtils.stringToPattern(ss.getSerializedPattern()));
        mMode = DisplayMode.values()[ss.getDisplayMode()];
        mInputEnabled = ss.isInputEnabled();
        mInStealthMode = ss.isInStealthMode();
        mEnableHapticFeedback = ss.isTactileFeedbackEnabled();
    }

    protected static enum DisplayMode {
        Empty,
        Correct,
        Animate,
        Wrong
    }

    protected static enum Aspect {
        SQUARE,
        LOCK_WIDTH,
        LOCK_HEIGHT
    }

    public static interface OnPatternListener {
        void onPatternStart();
        void onPatternCleared();
        void onPatternCellAdded(List<Point> points);
        void onPatternDetected(List<Point> points);
    }

    protected static class CellState {
        public float scale = 1.0f;
        public float translate = 0.0f;
        public float alpha = 1.0f;
        public float size;
        public float lineStartX = Float.NaN;
        public float lineStartY = Float.NaN;
        public float lineEndX = Float.NaN;
        public float lineEndY = Float.NaN;
        public ValueAnimator lineAnimator;
    }

    private static class SavedState extends BaseSavedState {

        private final String mSerializedPattern;
        private final int mDisplayMode;
        private final boolean mInputEnabled;
        private final boolean mInStealthMode;
        private final boolean mTactileFeedbackEnabled;

        private SavedState(Parcelable superState, String serializedPattern, int displayMode,
                           boolean inputEnabled, boolean inStealthMode, boolean tactileFeedbackEnabled) {
            super(superState);
            mSerializedPattern = serializedPattern;
            mDisplayMode = displayMode;
            mInputEnabled = inputEnabled;
            mInStealthMode = inStealthMode;
            mTactileFeedbackEnabled = tactileFeedbackEnabled;
        }

        private SavedState(Parcel in) {
            super(in);
            mSerializedPattern = in.readString();
            mDisplayMode = in.readInt();
            mInputEnabled = (Boolean) in.readValue(null);
            mInStealthMode = (Boolean) in.readValue(null);
            mTactileFeedbackEnabled = (Boolean) in.readValue(null);
        }

        public String getSerializedPattern() {
            return mSerializedPattern;
        }

        public int getDisplayMode() {
            return mDisplayMode;
        }

        public boolean isInputEnabled() {
            return mInputEnabled;
        }

        public boolean isInStealthMode() {
            return mInStealthMode;
        }

        public boolean isTactileFeedbackEnabled(){
            return mTactileFeedbackEnabled;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(mSerializedPattern);
            dest.writeInt(mDisplayMode);
            dest.writeValue(mInputEnabled);
            dest.writeValue(mInStealthMode);
            dest.writeValue(mTactileFeedbackEnabled);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}
