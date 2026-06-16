package top.zibin.luban;

import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.InputStream;

import androidx.exifinterface.media.ExifInterface;

public enum Checker {
    SINGLE;

    private static final String TAG = "Luban";

    private static final String JPG = ".jpg";

    /**
     * Determine if it is JPG by checking EXIF metadata.
     *
     * @param is image file input stream
     */
    boolean isJPG(InputStream is) {
        try {
            ExifInterface exif = new ExifInterface(is);
            // If EXIF can be parsed, it's a JPEG (only JPEG/HEIF support EXIF)
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the degrees in clockwise. Values are 0, 90, 180, or 270.
     * Uses ExifInterface instead of loading the entire file into a byte array.
     */
    int getOrientation(InputStream is) {
        try {
            ExifInterface exif = new ExifInterface(is);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read orientation", e);
            return 0;
        }
    }

    /**
     * is content://
     *
     * @param url
     * @return
     */
    public static boolean isContent(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        return url.startsWith("content://");
    }

    String extSuffix(InputStreamProvider input) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input.open(), null, options);
            return options.outMimeType.replace("image/", ".");
        } catch (Exception e) {
            return JPG;
        }
    }

    boolean needCompress(int leastCompressSize, String path) {
        if (leastCompressSize > 0) {
            File source = new File(path);
            return source.exists() && source.length() > ((long) leastCompressSize << 10);
        }
        return true;
    }
}
