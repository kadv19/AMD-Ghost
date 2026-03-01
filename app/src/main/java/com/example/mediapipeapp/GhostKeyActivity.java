package com.example.mediapipeapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

public class GhostKeyActivity extends AppCompatActivity {

    private static final String BUCKET_NAME = "ai-ghost-android";
    private static final String PREFS_NAME = "GhostPrefs";

    private EditText usernameInput;
    private EditText ghostKeyInput;
    private Button awakenButton;
    private TextView statusText;
    private TextView errorText;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ghost_key);

        usernameInput = findViewById(R.id.editUsername);
        ghostKeyInput = findViewById(R.id.editGhostKey);
        awakenButton  = findViewById(R.id.btnUnlock);
        statusText    = findViewById(R.id.tvStatus);
        errorText     = findViewById(R.id.tvError);
        progressBar   = findViewById(R.id.progressBar);

        // Already unlocked before → skip straight to chat
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUsername = prefs.getString("username", null);
        if (savedUsername != null && BrainLoader.isBrainLoaded(this)) {
            launchMain(savedUsername);
            return;
        }

        awakenButton.setOnClickListener(v -> attemptAwaken());
    }

    private void attemptAwaken() {
        String username = usernameInput.getText().toString().trim();
        String ghostKey = ghostKeyInput.getText().toString().trim().toLowerCase();

        errorText.setVisibility(View.GONE);

        if (TextUtils.isEmpty(username)) {
            showError("Please enter your Ghost ID.");
            return;
        }
        if (TextUtils.isEmpty(ghostKey)) {
            showError("Please enter your Ghost Key.");
            return;
        }
        if (!ghostKey.matches("[a-z]+-[a-z]+-[a-z]+-[a-z]+")) {
            showError("Ghost Key format: word-word-word-word");
            return;
        }

        setLoading(true, "Searching for your Ghost...");
        new DownloadBrainTask(username, ghostKey).execute();
    }

    private class DownloadBrainTask extends AsyncTask<Void, String, Boolean> {

        private final String username;
        private final String ghostKey;
        private String errorMessage = "";

        DownloadBrainTask(String username, String ghostKey) {
            this.username = username;
            this.ghostKey = ghostKey;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                BasicAWSCredentials credentials = new BasicAWSCredentials(
                        BuildConfig.AWS_ACCESS_KEY,
                        BuildConfig.AWS_SECRET_KEY
                );
                AmazonS3Client s3Client = new AmazonS3Client(credentials);
                s3Client.setRegion(Region.getRegion(Regions.US_EAST_1));

                // Try secure path first, then simple fallback
                String securePath = "user_" + username + "_" + ghostKey + "/processed/brain.json";
                String simplePath = "user_" + username + "/processed/brain.json";

                publishProgress("Connecting to cloud...");

                String s3Key = null;
                if (objectExists(s3Client, securePath)) {
                    s3Key = securePath;
                    android.util.Log.d("GHOST_KEY", "Found at secure path: " + securePath);
                } else if (objectExists(s3Client, simplePath)) {
                    s3Key = simplePath;
                    android.util.Log.d("GHOST_KEY", "Found at simple path: " + simplePath);
                } else {
                    errorMessage = "Ghost not found. Check your Ghost ID and Key.";
                    return false;
                }

                publishProgress("Ghost found! Downloading memories...");

                // Download to internal storage
                File brainFile = BrainLoader.getBrainFile(GhostKeyActivity.this);
                S3Object s3Object = s3Client.getObject(
                        new GetObjectRequest(BUCKET_NAME, s3Key));

                try (InputStream in = s3Object.getObjectContent();
                     FileOutputStream out = new FileOutputStream(brainFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytes = 0;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                        publishProgress("Downloading... " + (totalBytes / 1024) + " KB");
                    }
                }

                publishProgress("Loading memories into Ghost...");

                // Validate by parsing
                List<BrainLoader.MemoryChunk> chunks =
                        BrainLoader.loadBrain(GhostKeyActivity.this);

                if (chunks == null || chunks.isEmpty()) {
                    errorMessage = "Brain file appears empty. Please try again.";
                    brainFile.delete();
                    return false;
                }

                android.util.Log.d("GHOST_KEY", "Loaded " + chunks.size() + " memory chunks");
                return true;

            } catch (Exception e) {
                android.util.Log.e("GHOST_KEY", "Download failed: " + e.getMessage(), e);
                errorMessage = "Connection failed. Check your internet and try again.";
                return false;
            }
        }

        private boolean objectExists(AmazonS3Client s3Client, String key) {
            try {
                s3Client.getObjectMetadata(BUCKET_NAME, key);
                return true;
            } catch (AmazonS3Exception e) {
                if (e.getStatusCode() == 404) return false;
                throw e;
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            statusText.setText(values[0]);
        }

        @Override
        protected void onPostExecute(Boolean success) {
            setLoading(false, "");
            if (success) {
                SharedPreferences.Editor editor =
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                editor.putString("username", username);
                editor.putString("ghostKey", ghostKey);
                editor.apply();
                launchMain(username);
            } else {
                showError(errorMessage);
            }
        }
    }

    private void launchMain(String username) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("username", username);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading, String message) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        statusText.setVisibility(loading ? View.VISIBLE : View.GONE);
        statusText.setText(message);
        awakenButton.setEnabled(!loading);
        usernameInput.setEnabled(!loading);
        ghostKeyInput.setEnabled(!loading);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }
}