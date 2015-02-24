package com.zappyware.learnletters.entities;

/**
 * Created by Csaba on 2015.02.24..
 */
public class Point {

    private static final String TS_PATTERN = "Point[x=%.2f;y=%.2f]";

    public float x;
    public float y;

    public static Point of(float x, float y) {
        return new Point(x, y);
    }

    private Point(float x, float y) {
        ensureRangeIsValid(x);
        ensureRangeIsValid(y);

        this.x = x;
        this.y = y;
    }

    private static void ensureRangeIsValid(float value) {
        if(value < 0f) {
            value = 0f;
        } else if(value > 1f) {
            value = 1f;
        }
    }

    @Override
    public String toString() {
        return String.format(TS_PATTERN, x, y);
    }

}
