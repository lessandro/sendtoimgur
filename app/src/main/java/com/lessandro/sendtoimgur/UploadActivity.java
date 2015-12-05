package com.lessandro.sendtoimgur;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class UploadActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        Intent intent = getIntent();
        String action = intent.getAction();

        if (Intent.ACTION_SEND.equals(action)) {
            String type = intent.getType();

            if (type != null && type.startsWith("image/")) {
                Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                Uploader.upload(this, imageUri);
                finish();
            }
        }
    }
}
