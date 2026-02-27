package com.example.mediapipeapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
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
            ((AiViewHolder) holder).textMessage.setText(message.getText());
        } else if (holder instanceof PdfViewHolder) {
            ((PdfViewHolder) holder).pdfName.setText(message.getPdfName());
            ((PdfViewHolder) holder).btnViewPdf.setOnClickListener(v -> {
                // Handle PDF view
            });
        }
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