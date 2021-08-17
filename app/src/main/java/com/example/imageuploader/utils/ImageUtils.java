package com.example.imageuploader.utils;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;

public class ImageUtils {
    private static final String TAG = "ImageUtility";
    private static ImageUtils instance;
    private final ContentResolver contentResolver;

    // Constructor
    public ImageUtils(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
    }

    // Instance
    public static ImageUtils getInstance(ContentResolver contentResolver) {
        if (instance == null) {
            instance = new ImageUtils(contentResolver);
        }
        return instance;
    }


    /**
     * -----------------------------------------------------------------------------------
     * Function to convert uri to bitmap
     */
    public Bitmap getBitmap(Uri uri) {
        if (uri == null) return null;
        Bitmap bitmap = null;

        // If Build.VERSION.SDK_INT is greater than or equal to P
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bitmap = uri_to_bitmap(uri);
        } else {
            bitmap = uri_to_bitmap_in_lower_than_p(uri);
        }

        return bitmap;
    }

    /*
    --------------------------------------------------------------
    Uri to bitmap if android version is greater than or equal to P
     */
    @RequiresApi(api = Build.VERSION_CODES.P)
    private Bitmap uri_to_bitmap(@NonNull Uri uri) {
        Bitmap bitmap = null;
        ImageDecoder.Source source = ImageDecoder.createSource(contentResolver, uri);
        try {
            bitmap = ImageDecoder.decodeBitmap(source);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return bitmap;
    }

    /*
    -------------------------------------------------
    Uri to bitmap if android version is lower than P
     */
    private Bitmap uri_to_bitmap_in_lower_than_p(@NonNull Uri uri) {
        Bitmap bitmap = null;
        try {
            InputStream inputStream = contentResolver.openInputStream(uri);
            bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "uri_to_bitmap_in_lower_than_p: ", e.getCause());
        }

        return bitmap;
    }

    /*
    ----------------------------------------------------------------
    Uri to bitmap if android version is lower than P (Another Method)
     */
    private Bitmap uri_to_bitmap_alternative(@NonNull Uri uri) {
        Bitmap bitmap = null;
        try {
            ParcelFileDescriptor parcelFileDescriptor =
                    contentResolver.openFileDescriptor(uri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
            parcelFileDescriptor.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "uri_to_bitmap_alternative: ", e.getCause());
        }

        return bitmap;
    }

    /**
     * ---------------------------------
     * Function to compress bitmap image
     */
    public byte[] getByteArray(@NonNull Uri uri, int quality) {
        Bitmap bitmap = getBitmap(uri);
        if (bitmap == null) return new byte[]{};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return out.toByteArray();
    }


    /*
   Function to get file path from the given uri
     */
    public String getFilePath(Uri uri) {
        String result = null;
        Cursor cursor = contentResolver.query(uri, null, null, null, null);
        if (cursor != null) {
            int column_index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            cursor.moveToFirst();
            result = cursor.getString(column_index);
            cursor.close();
        }

        Log.d(TAG, "getFilePath: " + result);
        return result;
    }
}
