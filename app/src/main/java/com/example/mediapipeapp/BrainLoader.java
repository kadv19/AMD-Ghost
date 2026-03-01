package com.example.mediapipeapp;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * BrainLoader reads brain.json from the app's internal files directory
 * and parses it into a list of MemoryChunk objects ready for RAG search.
 *
 * brain.json structure:
 * {
 *   "user_id": "...",
 *   "total_chunks": 169,
 *   "memories": [
 *     {
 *       "chunk_id": "chunk_001",
 *       "text": "...",
 *       "vector": [0.012, -0.034, ...],  // 384 floats
 *       "metadata": { "source_type": "pdf", "file_name": "..." }
 *     }
 *   ]
 * }
 */
public class BrainLoader {

    private static final String TAG = "GHOST_BRAIN";
    public static final String BRAIN_FILE_NAME = "brain.json";

    public static class MemoryChunk {
        public final String chunkId;
        public final String text;
        public final float[] vector;
        public final String sourceType;
        public final String fileName;

        public MemoryChunk(String chunkId, String text, float[] vector,
                           String sourceType, String fileName) {
            this.chunkId = chunkId;
            this.text = text;
            this.vector = vector;
            this.sourceType = sourceType;
            this.fileName = fileName;
        }
    }

    /**
     * Returns path to brain.json in app internal storage.
     * This is where we expect the file to be placed (either from S3 download or manual copy).
     */
    public static File getBrainFile(Context context) {
        return new File(context.getFilesDir(), BRAIN_FILE_NAME);
    }

    /**
     * Returns true if brain.json exists and is non-empty.
     */
    public static boolean isBrainLoaded(Context context) {
        File f = getBrainFile(context);
        return f.exists() && f.length() > 0;
    }

    /**
     * Loads and parses brain.json into a list of MemoryChunks.
     * Call this once at startup and keep the result in memory.
     * 169 chunks × 384 floats = ~260KB RAM, totally fine.
     */
    public static List<MemoryChunk> loadBrain(Context context) throws Exception {
        File brainFile = getBrainFile(context);
        if (!brainFile.exists()) {
            throw new Exception("brain.json not found at: " + brainFile.getAbsolutePath());
        }

        Log.d(TAG, "Loading brain from: " + brainFile.getAbsolutePath());

        // Read file to string
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(brainFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        // Parse JSON
        JSONObject root = new JSONObject(sb.toString());
        JSONArray memoriesArray = root.getJSONArray("memories");
        List<MemoryChunk> chunks = new ArrayList<>();

        for (int i = 0; i < memoriesArray.length(); i++) {
            JSONObject mem = memoriesArray.getJSONObject(i);

            String chunkId = mem.optString("chunk_id", "chunk_" + i);
            String text = mem.optString("text", "");

            // Parse 384-dim vector
            JSONArray vectorArray = mem.getJSONArray("vector");
            float[] vector = new float[vectorArray.length()];
            for (int j = 0; j < vectorArray.length(); j++) {
                vector[j] = (float) vectorArray.getDouble(j);
            }

            // Parse metadata
            String sourceType = "unknown";
            String fileName = "";
            if (mem.has("metadata")) {
                JSONObject meta = mem.getJSONObject("metadata");
                sourceType = meta.optString("source_type", "unknown");
                fileName = meta.optString("file_name", "");
            }

            chunks.add(new MemoryChunk(chunkId, text, vector, sourceType, fileName));
        }

        Log.d(TAG, "Brain loaded: " + chunks.size() + " chunks");
        return chunks;
    }
}