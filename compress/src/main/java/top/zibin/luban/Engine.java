package top.zibin.luban;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Responsible for starting compress and managing active and cached resources.
 */
class Engine {
    private static final int ARGB_8888_BYTE_PER_PIXEL = 4;
    private static final long MAX_BITMAP_SIZE = 100 * 1024 * 1024; // 100 MB

    private InputStreamProvider srcImg;
    private File tagImg;
    private int srcWidth;
    private int srcHeight;
    private boolean focusAlpha;

    Engine(InputStreamProvider srcImg, File tagImg, boolean focusAlpha) throws IOException {
        this.tagImg = tagImg;
        this.srcImg = srcImg;
        this.focusAlpha = focusAlpha;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inSampleSize = 1;

        BitmapFactory.decodeStream(srcImg.open(), null, options);
        this.srcWidth = options.outWidth;
        this.srcHeight = options.outHeight;
    }

    private int computeSize() {
        srcWidth = srcWidth % 2 == 1 ? srcWidth + 1 : srcWidth;
        srcHeight = srcHeight % 2 == 1 ? srcHeight + 1 : srcHeight;

        int longSide = Math.max(srcWidth, srcHeight);
        int shortSide = Math.min(srcWidth, srcHeight);

        float scale = ((float) shortSide / longSide);
        if (scale <= 1 && scale > 0.5625) {
            if (longSide < 1664) {
                return 1;
            } else if (longSide < 4990) {
                return 2;
            } else if (longSide > 4990 && longSide < 10240) {
                return 4;
            } else {
                return longSide / 1280;
            }
        } else if (scale <= 0.5625 && scale > 0.5) {
            return longSide / 1280 == 0 ? 1 : longSide / 1280;
        } else {
            return (int) Math.ceil(longSide / (1280.0 / scale));
        }
    }

    /**
     * Calculate a safe inSampleSize that ensures the decoded bitmap fits in available memory.
     * Starts from computeSize() and doubles until the estimated bitmap size <= available memory.
     */
    private int computeSafeSampleSize() {
        int inSampleSize = computeSize();
        long availableMemory = getAvailableMemory();

        while (inSampleSize > 0) {
            int width = srcWidth / inSampleSize;
            int height = srcHeight / inSampleSize;
            long estimatedSize = (long) width * height * ARGB_8888_BYTE_PER_PIXEL;
            if (estimatedSize <= availableMemory) {
                break;
            }
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

    /**
     * Get available memory for bitmap decoding, capped at MAX_BITMAP_SIZE.
     */
    private long getAvailableMemory() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long available = maxMemory - usedMemory;
        // Cap to prevent using all remaining heap
        return Math.min(available, MAX_BITMAP_SIZE);
    }

    private Bitmap rotatingImage(Bitmap bitmap, int angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        // Recycle the original bitmap immediately to avoid double-bitmap memory spike
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }

    File compress() throws IOException {
        int inSampleSize = computeSafeSampleSize();

        // Decode with OOM catch-and-retry: if decode fails due to OOM, double inSampleSize
        Bitmap tagBitmap = null;
        while (tagBitmap == null && inSampleSize > 0) {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = inSampleSize;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                tagBitmap = BitmapFactory.decodeStream(srcImg.open(), null, options);
                if (tagBitmap == null) {
                    // Stream may be exhausted, break to avoid infinite loop
                    break;
                }
            } catch (OutOfMemoryError e) {
                inSampleSize *= 2;
                System.gc();
            }
        }

        if (tagBitmap == null) {
            throw new IOException("Failed to decode bitmap after OOM retries");
        }

        if (Checker.SINGLE.isJPG(srcImg.open())) {
            tagBitmap = rotatingImage(tagBitmap, Checker.SINGLE.getOrientation(srcImg.open()));
        }

        // Write compressed output directly to file to avoid ByteArrayOutputStream buffering
        FileOutputStream fos = new FileOutputStream(tagImg);
        boolean compressed = tagBitmap.compress(
                focusAlpha || tagBitmap.hasAlpha() ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG,
                60, fos);
        tagBitmap.recycle();
        fos.flush();
        fos.close();

        if (!compressed) {
            throw new IOException("Failed to compress bitmap");
        }

        return tagImg;
    }
}
