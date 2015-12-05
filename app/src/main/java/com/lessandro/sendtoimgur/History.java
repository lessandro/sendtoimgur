package com.lessandro.sendtoimgur;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

public class History extends AppCompatActivity {

    public static final String TAG = "SendToImgur";

    private int id = 1;
    private SharedPreferences preferences;
    private MenuItem loginMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        Intent intent = getIntent();
        String action = intent.getAction();

        if (Intent.ACTION_SEND.equals(action)) {
            String type = intent.getType();

            if (type != null && type.startsWith("image/")) {
                Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                Uploader.upload(this, imageUri);
                finish();
                return;
            }
        }

        if (Intent.ACTION_VIEW.equals(action)) {
            Uri data = intent.getData();
            requestAccessToken("authorization_code", "code", data.getQueryParameter("code"));
        }

        setContentView(R.layout.activity_history);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        loginMenu = menu.findItem(R.id.login);
        updateMenu();
        return true;
    }

    private void updateMenu() {
        String user = preferences.getString("account_username", null);
        if (user != null) {
            loginMenu.setTitle(user);
        }
    }

    public void login(MenuItem item) {
        Uri authUri = Uri.parse(getString(R.string.imgur_auth_uri))
                .buildUpon()
                .appendQueryParameter("client_id", getString(R.string.imgur_client_id))
                .appendQueryParameter("response_type", "code")
                .build();

        Intent browserIntent = new Intent(Intent.ACTION_VIEW, authUri);
        startActivity(browserIntent);
    }

    public void requestAccessToken(String type, String key, String value) {
        Ion.with(this)
                .load("POST", getString(R.string.imgur_token_uri))
                .setBodyParameter("client_id", getString(R.string.imgur_client_id))
                .setBodyParameter("client_secret", getString(R.string.imgur_client_secret))
                .setBodyParameter("grant_type", type)
                .setBodyParameter(key, value)
                .asJsonObject()
                .setCallback(new FutureCallback<JsonObject>() {
                    @Override
                    public void onCompleted(Exception e, JsonObject result) {
                        if (result != null) {
                            Log.i(TAG, result.toString());
                            Toast.makeText(History.this, result.toString(), Toast.LENGTH_LONG).show();

                            if (result.get("access_token") != null) {
                                parseTokenResult(result);
                                updateMenu();
                            }
                        }
                    }
                });
    }

    private void parseTokenResult(JsonObject result) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("access_token", result.get("access_token").getAsString());
        editor.putString("refresh_token", result.get("refresh_token").getAsString());
        editor.putString("account_username", result.get("account_username").getAsString());
        editor.commit();
    }
}
