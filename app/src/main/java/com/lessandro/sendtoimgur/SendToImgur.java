package com.lessandro.sendtoimgur;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.JsonObject;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.ProgressCallback;

import java.io.File;

public class SendToImgur extends Activity implements FutureCallback<JsonObject>, ProgressCallback {

    private static String TAG = "SendToImgur";

    private SharedPreferences preferences;
    private TextView logView;
    private TextView linkView;
    private ProgressBar bar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_to_imgur);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        logView = (TextView) findViewById(R.id.log);
        linkView = (TextView) findViewById(R.id.link);
        bar = (ProgressBar) findViewById(R.id.bar);
    }

    private void log(String text) {
        logView.setText(text + "\n\n" + logView.getText());
        Log.i(TAG, text);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handleIntent();
    }

    private void reset() {
        linkView.setVisibility(View.GONE);
        bar.setVisibility(View.GONE);
        setIntent(null);
        logView.setText("");
    }

    private void handleIntent() {
        Intent intent = getIntent();
        if (intent == null)
            return;

        reset();

        String action = intent.getAction();

        if (Intent.ACTION_SEND.equals(action)) {
            String type = intent.getType();

            if (type != null && type.startsWith("image/")) {
                Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                uploadImage(imageUri);
            }
        }

        if (Intent.ACTION_VIEW.equals(action)) {
            String url = intent.getData().toString();
            String accessToken = Uri.parse(url.replace('#', '?')).getQueryParameter("access_token");

            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("access_token", accessToken);
            editor.commit();

            log("auth url: " + url);
            log("access token: " + accessToken);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    public void login(MenuItem item) {
        Uri authUri = Uri.parse(getString(R.string.imgur_auth_uri))
                .buildUpon()
                .appendQueryParameter("client_id", getString(R.string.imgur_client_id))
                .appendQueryParameter("response_type", "token")
                .build();

        log("opening " + authUri);

        Intent browserIntent = new Intent(Intent.ACTION_VIEW, authUri);
        startActivity(browserIntent);
    }

    private void uploadImage(Uri imageUri) {
        String path = convertMediaUriToPath(imageUri);
        String accessToken = preferences.getString("access_token", null);

        log("media: " + imageUri.toString());
        log("path: " + path);

        bar.setVisibility(View.VISIBLE);

        Ion.with(this)
                .load("POST", getString(R.string.imgur_image_uri))
                .uploadProgress(this)
                .addHeader("Authorization", "Bearer " + accessToken)
                .setMultipartParameter("type", "file")
                .setMultipartFile("image", new File(path))
                .asJsonObject()
                .setCallback(this);
    }

    @Override
    public void onProgress(final long uploaded, final long total) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                log("uploaded " + uploaded + " of " + total);
                bar.setMax((int) total);
                bar.setProgress((int) uploaded);
                bar.setIndeterminate(uploaded == total);
            }
        });
    }

    @Override
    public void onCompleted(Exception e, JsonObject result) {
        bar.setVisibility(View.GONE);

        if (e != null) {
            log("error: " + e.toString());
            e.printStackTrace();
        } else {
            log("result: " + result.toString());

            boolean success = result.get("success").getAsBoolean();

            if (success) {
                JsonObject data = result.getAsJsonObject("data");
                String link = data.get("link").getAsString();

                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newRawUri("imgur link", Uri.parse(link));
                clipboard.setPrimaryClip(clip);

                linkView.setText(link);
                linkView.setVisibility(View.VISIBLE);
            }
        }
    }

    private String convertMediaUriToPath(Uri uri) {
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, proj, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String path = cursor.getString(column_index);
        cursor.close();
        return path;
    }
}
