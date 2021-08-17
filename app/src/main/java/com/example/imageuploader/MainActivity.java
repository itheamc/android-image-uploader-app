package com.example.imageuploader;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.HandlerCompat;

import com.example.imageuploader.utils.ImageUtils;
import com.google.android.material.snackbar.Snackbar;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private ActivityResultLauncher<Intent> imagePickerResultLauncher;
    private List<Uri> imagesUri;
    private ImageView imageView;

    /*
    Integer to store the uploaded image qty
     */
    int uploadCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);

        // Activity Result launcher to listen the result of the multi image picker
        imagePickerResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {

                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // There are no request codes
                        Intent data = result.getData();
                        if (data == null) return;


                        imagesUri = new ArrayList<>();

                        // Get the Images from data
                        ClipData mClipData = data.getClipData();
                        if (mClipData != null) {
                            int count = mClipData.getItemCount();
                            for (int i = 0; i < count; i++) {
                                // adding imageUri in array
                                Uri imageUri = mClipData.getItemAt(i).getUri();
                                imagesUri.add(imageUri);
                            }

                            imageView.setImageBitmap(ImageUtils.getInstance(getContentResolver()).getBitmap(imagesUri.get(0)));
                            return;
                        }

                        Uri imageUri = data.getData();
                        imagesUri.add(imageUri);
                        imageView.setImageBitmap(ImageUtils.getInstance(getContentResolver()).getBitmap(imageUri));

                    } else {
                        Log.d(TAG, "imagePickerResultLauncher: Failed");
                    }
                });

        findViewById(R.id.button).setOnClickListener(v -> {
            uploadImage();
        });

        findViewById(R.id.button2).setOnClickListener(v -> {
            showImagePicker();
        });
    }

    /**
     * -----------------------------------------------------------------------------
     * Function to start the image picker intent
     */
    private void showImagePicker() {
        // initialising intent
        Intent intent = new Intent();

        // setting type to select to be image
        intent.setType("image/*");

        // allowing multiple image to be selected
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        imagePickerResultLauncher.launch(Intent.createChooser(intent, "Select Images"));
    }


    /**
     * --------------------------------------------------------------------------
     * Function to handle image upload to cloud storage
     * It will be triggered continuously until all the images will be uploaded
     */
    private void uploadImage() {
        if (imagesUri == null || imagesUri.isEmpty()) return;

        // Creating the instance of ImageUtils
        ImageUtils imageUtils = ImageUtils.getInstance(getContentResolver());

        // Handling all the image processing and uploads in background
        Executors.newFixedThreadPool(4).execute(() -> {

            MultipartBody.Builder builder = new MultipartBody.Builder();
            builder.setType(MultipartBody.FORM);

            for (Uri uri : imagesUri) {
                if (uri == null) continue;

                String s = imageUtils.getFilePath(uri);
                File file = new File(getCacheDir(), s);
                FileOutputStream outputStream = null;
                try {
                    outputStream = new FileOutputStream(file);
                    byte[] bytes = imageUtils.getByteArray(uri, 40);
                    outputStream.write(bytes);
                    outputStream.close();
                    Uri uris = Uri.fromFile(file);
                    String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uris.toString());
                    String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
                    String imageName = file.getName();

                    builder.addFormDataPart("file", imageName, RequestBody.create(file, MediaType.parse(mime)));

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

            RequestBody requestBody = builder.build();
            Log.d(TAG, "uploadImage: " + requestBody.toString());

            // Outside for loop
            Request request = new Request.Builder()
                    .url("https://uploadimages.amcbizprojects.co.in/uploads")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .post(requestBody)
                    .build();


            new OkHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.d(TAG, "onFailure: " + e.getMessage());
                    HandlerCompat.createAsync(Looper.getMainLooper()).post(() -> {
                        Toast.makeText(MainActivity.this, "Failed", Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    HandlerCompat.createAsync(Looper.getMainLooper()).post(() -> {
                        Toast.makeText(MainActivity.this, "success", Toast.LENGTH_LONG).show();
                    });
                    Log.d(TAG, "onResponse: " + response.body().string());
                }
            });
        });


    }


    /**
     * --------------------------------------------------------------------------
     * Function to handle single image upload to server
     */
    private void imageUpload(Uri uri) {

        String s = ImageUtils.getInstance(getContentResolver()).getFilePath(uri);
        Executors.newFixedThreadPool(2).execute(() -> {
            try {
                ParcelFileDescriptor parcelFileDescriptor = getContentResolver().openFileDescriptor(uri, "r", null);
                FileInputStream inputStream = new FileInputStream(parcelFileDescriptor.getFileDescriptor());
                File file = new File(getCacheDir(), s);
                FileOutputStream outputStream = new FileOutputStream(file);

                byte[] b = new byte[(int) inputStream.getChannel().size()];
                while (inputStream.read(b, 0, (int) inputStream.getChannel().size()) > 0) {
                    outputStream.write(b);
                }

                inputStream.close();
                outputStream.close();


                Uri uris = Uri.fromFile(file);
                String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uris.toString());
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
                String imageName = file.getName();
                Log.d(TAG, "handleImageUpload: " + imageName);

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", imageName, RequestBody.create(file, MediaType.parse(mime)))
                        .build();

                MultipartBody.Builder builder = new MultipartBody.Builder();

                Request request = new Request.Builder()
                        .url("https://uploadimages.amcbizprojects.co.in/uploads")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .post(requestBody)
                        .build();


                new OkHttpClient().newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.d(TAG, "onFailure: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        Log.d(TAG, "onResponse: " + response.body().string());
                        uploadCount += 1;
                    }
                });

            } catch (IOException e) {
                e.printStackTrace();
            }
        });

    }
}