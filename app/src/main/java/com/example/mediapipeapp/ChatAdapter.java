package com.example.mediapipeapp;

import android.content.Intent;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public void appendToLastMessage(String text) {
        if (messages.isEmpty()) return;
        int last = messages.size() - 1;
        messages.get(last).setText(messages.get(last).getText() + text);
        notifyItemChanged(last, "APPEND");
    }

    /**
     * Simple markdown renderer — handles **bold** and *italic*.
     * Lives here so no external dependency is needed.
     */
    public static CharSequence renderMarkdown(String raw) {
        if (raw == null || raw.isEmpty()) return "";

        SpannableStringBuilder sb = new SpannableStringBuilder(raw);

        // Bold: **text**
        Pattern bold = Pattern.compile("\\*\\*(.+?)\\*\\*", Pattern.DOTALL);
        Matcher bm = bold.matcher(raw);
        int offset = 0;
        while (bm.find()) {
            String inner = bm.group(1);
            int start = bm.start() - offset;
            int end = bm.end() - offset;
            sb.replace(start, end, inner);
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, start + inner.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            offset += 4; // removed 2x "**"
        }

        // Italic: *text* (single asterisk, after bold is handled)
        raw = sb.toString();
        sb = new SpannableStringBuilder(raw);
        Pattern italic = Pattern.compile("\\*(.+?)\\*", Pattern.DOTALL);
        Matcher im = italic.matcher(raw);
        offset = 0;
        while (im.find()) {
            String inner = im.group(1);
            int start = im.start() - offset;
            int end = im.end() - offset;
            sb.replace(start, end, inner);
            sb.setSpan(new StyleSpan(Typeface.ITALIC), start, start + inner.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            offset += 2; // removed 2x "*"
        }

        return sb;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType().ordinal();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == ChatMessage.Type.USER.ordinal()) {
            return new UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false));
        } else if (viewType == ChatMessage.Type.AI.ordinal()) {
            return new AiViewHolder(inflater.inflate(R.layout.item_chat_ai, parent, false));
        } else {
            return new PdfViewHolder(inflater.inflate(R.layout.item_chat_pdf, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).textMessage.setText(message.getText());
        } else if (holder instanceof AiViewHolder) {
            String text = message.getText();
            ((AiViewHolder) holder).textMessage.setText(renderMarkdown(text));
            
            // Check for URLs in AI responses to show the PDF card or make them clickable
            Matcher matcher = Patterns.WEB_URL.matcher(text);
            if (matcher.find()) {
                String url = matcher.group();
                ((AiViewHolder) holder).itemView.setOnClickListener(v -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    v.getContext().startActivity(browserIntent);
                });
            } else {
                ((AiViewHolder) holder).itemView.setOnClickListener(null);
            }
        } else if (holder instanceof PdfViewHolder) {
            ((PdfViewHolder) holder).pdfName.setText(message.getPdfName());
            ((PdfViewHolder) holder).btnViewPdf.setOnClickListener(v -> {
                // Handle PDF view
            });
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && holder instanceof AiViewHolder) {
            onBindViewHolder(holder, position); // Reuse logic to handle URL detection
            return;
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage;
        UserViewHolder(View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.textMessage);
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage;
        AiViewHolder(View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.textMessage);
        }
    }

    static class PdfViewHolder extends RecyclerView.ViewHolder {
        TextView pdfName;
        Button btnViewPdf;
        PdfViewHolder(View itemView) {
            super(itemView);
            pdfName = itemView.findViewById(R.id.pdfName);
            btnViewPdf = itemView.findViewById(R.id.btnViewPdf);
        }
    }
}
