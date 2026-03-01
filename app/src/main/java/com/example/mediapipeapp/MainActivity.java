package com.example.mediapipeapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String BUCKET_NAME = "ai-ghost-android";

    private TextView textViewStatus;
    private EditText editTextPrompt;
    private ImageButton buttonSend;
    private ImageButton buttonToggleMode;
    private RecyclerView recyclerViewChat;
    private LinearLayout thinkingLayout;
    private TextView thinkingDots;

    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages = new ArrayList<>();

    private LlmInference llmInference;
    private ExecutorService executorService;
    private String modelPath;

    // RAG components — loaded once at startup
    private GhostRAG ghostRAG;
    private String username = "Ghost";
    private String ghostKey = "";

    // Mode Toggle: false = Chat, true = Extract File
    private boolean isExtractMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        modelPath = getFilesDir().getAbsolutePath() + "/llm/gemma3-1b-it-int4.task";

        // Get credentials from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("GhostPrefs", MODE_PRIVATE);
        username = prefs.getString("username", "Ghost");
        ghostKey = prefs.getString("ghostKey", "");

        textViewStatus = findViewById(R.id.textViewStatus);
        editTextPrompt = findViewById(R.id.editTextPrompt);
        buttonSend = findViewById(R.id.buttonSend);
        buttonToggleMode = findViewById(R.id.buttonToggleMode);
        recyclerViewChat = findViewById(R.id.recyclerViewChat);
        thinkingLayout = findViewById(R.id.thinkingLayout);
        thinkingDots = findViewById(R.id.thinkingDots);

        setupChat();

        executorService = Executors.newSingleThreadExecutor();

        // Load brain + model in parallel sequence
        loadBrainAndModel();

        buttonSend.setOnClickListener(v -> handleSendAction());
        buttonToggleMode.setOnClickListener(v -> toggleMode());
    }

    private void setupChat() {
        chatAdapter = new ChatAdapter(messages);
        recyclerViewChat.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewChat.setAdapter(chatAdapter);

        // Welcome message
        String welcome = "👻 Ghost awakened for " + username + ".\n\nI have your memories loaded. Ask me anything about your old phone's data!";
        messages.add(new ChatMessage(welcome, ChatMessage.Type.AI));
        chatAdapter.notifyItemInserted(0);
    }

    private void toggleMode() {
        isExtractMode = !isExtractMode;
        if (isExtractMode) {
            editTextPrompt.setHint("Enter file name to retrieve...");
            buttonToggleMode.setImageResource(android.R.drawable.ic_menu_save);
            Toast.makeText(this, "Extract File Mode Active", Toast.LENGTH_SHORT).show();
        } else {
            editTextPrompt.setHint("Ask your ghost...");
            buttonToggleMode.setImageResource(android.R.drawable.ic_menu_help);
            Toast.makeText(this, "Chat Mode Active", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSendAction() {
        String input = editTextPrompt.getText().toString().trim();
        if (input.isEmpty()) return;

        if (isExtractMode) {
            handleFileExtraction(input);
        } else {
            generateResponse(input);
        }
    }

    private void handleFileExtraction(String fileName) {
        // Show user message
        messages.add(new ChatMessage("Retrieve file: " + fileName, ChatMessage.Type.USER));
        chatAdapter.notifyItemInserted(messages.size() - 1);
        recyclerViewChat.scrollToPosition(messages.size() - 1);
        editTextPrompt.setText("");

        // Trigger Alert/Action for file retrieval
        Toast.makeText(this, "Searching for: " + fileName, Toast.LENGTH_LONG).show();

        executorService.execute(() -> {
            try {
                // Initialize S3 client
                BasicAWSCredentials credentials = new BasicAWSCredentials(
                        BuildConfig.AWS_ACCESS_KEY,
                        BuildConfig.AWS_SECRET_KEY
                );
                AmazonS3Client s3Client = new AmazonS3Client(credentials);
                s3Client.setRegion(Region.getRegion(Regions.US_EAST_1));

                // Try multiple potential S3 paths based on user structure
                String[] potentialPaths = {
                    "user_" + username + "_" + ghostKey + "/processed/" + fileName,
                    "user_" + username + "/processed/" + fileName,
                    "user_" + username + "/raw/pdfs/" + fileName,
                    "user_" + username + "/raw/" + fileName
                };

                String finalS3Key = null;
                boolean exists = false;

                for (String path : potentialPaths) {
                    try {
                        s3Client.getObjectMetadata(BUCKET_NAME, path);
                        finalS3Key = path;
                        exists = true;
                        break;
                    } catch (Exception ignored) {}
                }

                if (exists) {
                    // Generate a pre-signed URL valid for 1 hour
                    Date expiration = new Date();
                    long expTimeMillis = expiration.getTime();
                    expTimeMillis += 1000 * 60 * 60; // 1 hour
                    expiration.setTime(expTimeMillis);

                    URL url = s3Client.generatePresignedUrl(BUCKET_NAME, finalS3Key, expiration);
                    final String preSignedUrl = url.toString();

                    runOnUiThread(() -> {
                        String resultMessage = "Ghost has located '" + fileName + "'.\nYou can access it here (valid for 1 hour):\n" + preSignedUrl;
                        messages.add(new ChatMessage(resultMessage, ChatMessage.Type.AI));
                        chatAdapter.notifyItemInserted(messages.size() - 1);
                        recyclerViewChat.scrollToPosition(messages.size() - 1);
                    });
                } else {
                    runOnUiThread(() -> {
                        String resultMessage = "Ghost could not find a file named '" + fileName + "' in your memories.";
                        messages.add(new ChatMessage(resultMessage, ChatMessage.Type.AI));
                        chatAdapter.notifyItemInserted(messages.size() - 1);
                        recyclerViewChat.scrollToPosition(messages.size() - 1);
                    });
                }

            } catch (Exception e) {
                android.util.Log.e("GHOST", "File extraction failed", e);
                runOnUiThread(() -> {
                    messages.add(new ChatMessage("Error retrieving file: " + e.getMessage(), ChatMessage.Type.AI));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    recyclerViewChat.scrollToPosition(messages.size() - 1);
                });
            }
        });
    }

    private void loadBrainAndModel() {
        executorService.execute(() -> {
            // Step 1: Load brain.json for RAG
            try {
                updateStatus("Loading memories...");
                List<BrainLoader.MemoryChunk> chunks = BrainLoader.loadBrain(this);
                ghostRAG = new GhostRAG(chunks);
                android.util.Log.d("GHOST", "RAG ready with " + chunks.size() + " chunks");
            } catch (Exception e) {
                android.util.Log.e("GHOST", "Brain load FAILED: " + e.getMessage(), e);
                updateStatus("Error loading brain");
            }

            // Step 2: Load Gemma model
            try {
                updateStatus("Loading model...");
                copyModelIfNeeded();

                File modelFile = new File(modelPath);
                if (!modelFile.exists()) {
                    updateStatus("Error: Model file not found");
                    return;
                }

                LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelPath)
                        .setMaxTopK(64)
                        .build();

                llmInference = LlmInference.createFromOptions(MainActivity.this, options);

                String statusText = ghostRAG != null ? "On-Device + RAG" : "On-Device";
                updateStatus(statusText);
                runOnUiThread(() -> buttonSend.setEnabled(true));

            } catch (Exception e) {
                updateStatus("Offline");
            }
        });
    }

    private void copyModelIfNeeded() {
        File dest = new File(modelPath);
        if (dest.exists()) return;

        File src = new File(getExternalFilesDir(null), "gemma3-1b-it-int4.task");
        if (!src.exists()) return;

        try {
            dest.getParentFile().mkdirs();
            java.io.InputStream in = new java.io.FileInputStream(src);
            java.io.OutputStream out = new java.io.FileOutputStream(dest);
            byte[] buffer = new byte[1024 * 1024];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            in.close();
            out.close();
        } catch (Exception ignored) {}
    }

    private void generateResponse(String userQuestion) {
        if (llmInference == null) return;

        // Show user message
        messages.add(new ChatMessage(userQuestion, ChatMessage.Type.USER));
        chatAdapter.notifyItemInserted(messages.size() - 1);
        recyclerViewChat.scrollToPosition(messages.size() - 1);
        editTextPrompt.setText("");
        buttonSend.setEnabled(false);

        showThinking(true);

        executorService.execute(() -> {
            try {
                // Build the prompt — with RAG if brain is loaded, raw if not
                String prompt;
                if (ghostRAG != null) {
                    prompt = ghostRAG.buildRAGPrompt(userQuestion);
                    android.util.Log.d("GHOST", "RAG prompt built, length: " + prompt.length());
                } else {
                    // Fallback: plain prompt without memory context
                    prompt = "You are an AI Ghost, a digital memory assistant. " +
                            "Answer this question: " + userQuestion;
                }

                // Add AI response placeholder
                runOnUiThread(() -> {
                    messages.add(new ChatMessage("", ChatMessage.Type.AI));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                });

                // Stream Gemma's response token by token
                llmInference.generateResponseAsync(prompt, (partialResult, done) -> {
                    runOnUiThread(() -> {
                        if (thinkingLayout.getVisibility() == View.VISIBLE) {
                            showThinking(false);
                        }

                        ChatMessage lastMsg = messages.get(messages.size() - 1);
                        lastMsg.setText(lastMsg.getText() + partialResult);
                        chatAdapter.notifyItemChanged(messages.size() - 1);
                        recyclerViewChat.scrollToPosition(messages.size() - 1);

                        if (done) {
                            buttonSend.setEnabled(true);
                        }
                    });
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    showThinking(false);
                    buttonSend.setEnabled(true);
                    messages.add(new ChatMessage("Error: " + e.getMessage(), ChatMessage.Type.AI));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                });
            }
        });
    }

    private void showThinking(boolean show) {
        if (show) {
            thinkingLayout.setVisibility(View.VISIBLE);
            Animation breathing = AnimationUtils.loadAnimation(this, R.anim.breathing);
            thinkingDots.startAnimation(breathing);
        } else {
            thinkingDots.clearAnimation();
            thinkingLayout.setVisibility(View.GONE);
        }
    }

    private void updateStatus(final String status) {
        runOnUiThread(() -> textViewStatus.setText(status));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (llmInference != null) llmInference.close();
        if (executorService != null) executorService.shutdown();
    }
}
