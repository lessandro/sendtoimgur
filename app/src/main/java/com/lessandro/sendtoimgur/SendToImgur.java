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
    private Uri imageUri;

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
                imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                refreshToken();
            }
        }

        if (Intent.ACTION_VIEW.equals(action)) {
            String url = intent.getData().toString();
            Uri parsed = Uri.parse(url.replace('#', '?'));
            String refreshToken = parsed.getQueryParameter("refresh_token");

            saveRefreshToken(refreshToken);

            log("auth url: " + url);
            log("refresh token: " + refreshToken);
        }
    }

    private void saveRefreshToken(String refreshToken) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("refresh_token", refreshToken);
        editor.commit();
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
        finish();
    }

    private void refreshToken() {
        String oldRefreshToken = preferences.getString("refresh_token", null);
        String clientId = getString(R.string.imgur_client_id);
        String clientSecret = getString(R.string.imgur_client_secret);

        log("refreshing token");

        if (oldRefreshToken == null) {
            login(null);
            return;
        }

        Ion.with(this)
                .load("POST", getString(R.string.imgur_token_uri))
                .setBodyParameter("refresh_token", oldRefreshToken)
                .setBodyParameter("client_id", clientId)
                .setBodyParameter("client_secret", clientSecret)
                .setBodyParameter("grant_type", "refresh_token")
                .asJsonObject()
                .setCallback(new FutureCallback<JsonObject>() {
                    @Override
                    public void onCompleted(Exception e, JsonObject result) {
                        if (e != null) {
                            log("error: " + e.toString());
                            e.printStackTrace();
                        } else {
                            log("result: " + result.toString());

                            String newRefreshToken = result.get("refresh_token").getAsString();
                            saveRefreshToken(newRefreshToken);

                            String accessToken = result.get("access_token").getAsString();
                            uploadImage(accessToken);
                        }
                    }
                });
    }

    private void uploadImage(String accessToken) {
        String path = convertMediaUriToPath(imageUri);

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
