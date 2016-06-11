package com.byteshaft.contactsharing.utils;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;

import com.byteshaft.contactsharing.R;

/**
 * This class provide you bitmap with character
 */
public class BitmapWithCharacter {

    /** The number of available tile colors (see R.array.letter_tile_colors) */
    private static final int NUM_OF_TILE_COLORS = 8;

    /** The {@link TextPaint} used to draw the letter onto the tile */
    private final TextPaint mPaint = new TextPaint();
    /** The bounds that enclose the letter */
    private final Rect mBounds = new Rect();
    /** The {@link Canvas} to draw on */
    private final Canvas mCanvas = new Canvas();
    /** The first char of the name being displayed */
    private final char[] mFirstChar = new char[1];
    /** The background colors of the tile */
    private final TypedArray mColors;
    /** The font size used to display the letter */
    private final int mTileLetterFontSize;
    /** The default image to display */
    private final Bitmap mDefaultBitmap;

    /**
     * Constructor for <code>LetterTileProvider</code>
     */
    public BitmapWithCharacter() {
        final Resources resources = AppGlobals.getContext().getResources();
        mPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        mPaint.setColor(Color.WHITE);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setAntiAlias(true);
        mColors = resources.obtainTypedArray(R.array.letter_tile_colors);
        mTileLetterFontSize = resources.getDimensionPixelSize(R.dimen.tile_letter_font_size);
        mDefaultBitmap = BitmapFactory.decodeResource(resources, android.R.drawable.sym_def_app_icon);
    }

    /**
     * @param displayName The name used to create the letter for the tile
     * @param key The key used to generate the background color for the tile
     * @param width The desired width of the tile
     * @param height The desired height of the tile
     * @return A {@link Bitmap} that contains a letter used in the English
     *         alphabet or digit, if there is no letter or digit available, a
     *         default image is shown instead
     */
    public Bitmap getLetterTile(String displayName, int key, int width, int height) {
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final char firstChar = displayName.charAt(0);
        final Canvas canvas = mCanvas;
        canvas.setBitmap(bitmap);
//        int color = pickColor(key);
//        Log.i("key in method", key);
//        Log.i("COLOR", "" + color);
        canvas.drawColor(key);

        if (isEnglishLetterOrDigit(firstChar)) {
            mFirstChar[0] = Character.toUpperCase(firstChar);
            mPaint.setTextSize(mTileLetterFontSize);
            mPaint.getTextBounds(mFirstChar, 0, 1, mBounds);
            canvas.drawText(mFirstChar, 0, 1, 0 + width / 2, 0 + height / 2
                    + (mBounds.bottom - mBounds.top) / 2, mPaint);
        } else {
            canvas.drawBitmap(mDefaultBitmap, 0, 0, null);
        }
        return bitmap;
    }

    /**
     * @param c The char to check
     * @return True if <code>c</code> is in the English alphabet or is a digit,
     *         false otherwise
     */
    private static boolean isEnglishLetterOrDigit(char c) {
        return 'A' <= c && c <= 'Z' || 'a' <= c && c <= 'z' || '0' <= c && c <= '9';
    }

    /**
     * @param key The key used to generate the tile color
     * @return A new or previously chosen color for <code>key</code> used as the
     *         tile background color
     */

    public int pickColor(String key) {
        final int color = Math.abs(key.hashCode()) % NUM_OF_TILE_COLORS;
        try {
            return mColors.getColor(color, Color.BLACK);
        } finally {
            mColors.recycle();
        }
    }

}