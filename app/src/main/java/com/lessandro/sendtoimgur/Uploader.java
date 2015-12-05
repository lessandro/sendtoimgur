package com.lessandro.sendtoimgur;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.google.gson.JsonObject;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.ProgressCallback;

import java.io.File;

public class Uploader extends IntentService {
    private static final String ACTION_UPLOAD = "com.lessandro.sendtoimgur.action.UPLOAD";
    private static final String TAG = "SendToImgur";

    private int id = 1;
    private NotificationManager notifier;
    private NotificationCompat.Builder notification;
    private SharedPreferences preferences;

    public Uploader() {
        super("Uploader");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        notifier = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notification = new NotificationCompat.Builder(this);
        notification.setContentTitle("Uploading image...");
        notification.setContentText("Uploading image...");
        notification.setSmallIcon(R.mipmap.ic_launcher);
        notification.setAutoCancel(true);
    }

    public static void upload(Context context, Uri contentUri) {
        Intent intent = new Intent(context, Uploader.class);
        intent.setAction(ACTION_UPLOAD);
        intent.setData(contentUri);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_UPLOAD.equals(action)) {
                uploadImage(intent.getData());
            }
        }
    }

    private void uploadImage(Uri contentUri) {
        String path = convertMediaUriToPath(contentUri);
        String accessToken = preferences.getString("access_token", null);

        Ion
                .with(getApplicationContext())
                .load("POST", getString(R.string.imgur_image_uri))
                .uploadProgress(new ProgressCallback() {
                    @Override
                    public void onProgress(long uploaded, long total) {
                        int totalkb = (int) (total / 1024);
                        int uploadedkb = (int) (uploaded / 1024);
                        notification.setProgress(totalkb, uploadedkb, uploaded == total);
                        notification.setContentText("Uploaded " + uploadedkb + "kb of " + totalkb + "kb");
                        notification.setContentIntent(null);
                        notifier.notify(id, notification.build());
                    }
                })
                .addHeader("Authorization", "Bearer " + accessToken)
                .setMultipartParameter("type", "file")
                .setMultipartFile("image", new File(path))
                .asJsonObject()
                .setCallback(new FutureCallback<JsonObject>() {
                    @Override
                    public void onCompleted(Exception e, JsonObject result) {
                        if (e != null) {
                            Log.i(TAG, e.toString());
                            notification.setContentTitle("Upload failed");
                            notification.setContentText(e.toString());
                        } else {
                            Log.i(TAG, result.toString());
                            boolean success = result != null && result.get("success").getAsBoolean();
                            if (success) {
                                String link = result.getAsJsonObject("data").get("link").getAsString();
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                                PendingIntent pintent = PendingIntent.getActivity(Uploader.this, 0, intent, 0);
                                notification.setContentTitle("Upload complete");
                                notification.setContentText(link);
                                notification.setContentIntent(pintent);
                            } else {
                                String error = result.getAsJsonObject("data").get("error").getAsString();
                                notification.setContentTitle("Upload failed");
                                notification.setContentText(error);
                            }
                        }
                        notification.setProgress(0, 0, false);
                        notifier.notify(id, notification.build());
                    }
                });
    }

    protected String convertMediaUriToPath(Uri uri) {
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, proj, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String path = cursor.getString(column_index);
        cursor.close();
        return path;
    }
}
