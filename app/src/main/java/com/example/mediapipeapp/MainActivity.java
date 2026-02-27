package com.example.mediapipeapp;

import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private TextView textViewStatus;
    private EditText editTextPrompt;
    private ImageButton buttonSend;
    private RecyclerView recyclerViewChat;
    private LinearLayout thinkingLayout;
    private TextView thinkingDots;

    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages = new ArrayList<>();

    private LlmInference llmInference;
    private ExecutorService executorService;
    private String modelPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        modelPath = getFilesDir().getAbsolutePath() + "/llm/gemma3-1b-it-int4.task";

        textViewStatus = findViewById(R.id.textViewStatus);
        editTextPrompt = findViewById(R.id.editTextPrompt);
        buttonSend = findViewById(R.id.buttonSend);
        recyclerViewChat = findViewById(R.id.recyclerViewChat);
        thinkingLayout = findViewById(R.id.thinkingLayout);
        thinkingDots = findViewById(R.id.thinkingDots);

        setupChat();

        executorService = Executors.newSingleThreadExecutor();

        loadModel();

        buttonSend.setOnClickListener(v -> generateResponse());
    }

    private void setupChat() {
        chatAdapter = new ChatAdapter(messages);
        recyclerViewChat.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewChat.setAdapter(chatAdapter);
    }

    private void loadModel() {
        executorService.execute(() -> {
            try {
                updateStatus("Copying model...");
                copyModelIfNeeded();
                updateStatus("Loading model...");

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

                updateStatus("On-Device");
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
        if (!src.exists()) {
            return;
        }

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

    private void generateResponse() {
        String prompt = editTextPrompt.getText().toString().trim();
        if (prompt.isEmpty() || llmInference == null) return;

        // Add User Message
        messages.add(new ChatMessage(prompt, ChatMessage.Type.USER));
        chatAdapter.notifyItemInserted(messages.size() - 1);
        recyclerViewChat.scrollToPosition(messages.size() - 1);
        editTextPrompt.setText("");
        buttonSend.setEnabled(false);

        // Show Thinking Animation
        showThinking(true);

        executorService.execute(() -> {
            try {
                // We'll create a placeholder for AI response
                runOnUiThread(() -> {
                    messages.add(new ChatMessage("", ChatMessage.Type.AI));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                });

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
                            // Example: If response contains "PDF", show a PDF card
                            if (lastMsg.getText().contains(".pdf")) {
                                messages.add(new ChatMessage("Generated Document", "Report.pdf"));
                                chatAdapter.notifyItemInserted(messages.size() - 1);
                                recyclerViewChat.scrollToPosition(messages.size() - 1);
                            }
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
        if (llmInference != null) {
            llmInference.close();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
