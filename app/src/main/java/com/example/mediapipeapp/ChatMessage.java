package com.example.mediapipeapp;

public class ChatMessage {
    public enum Type { USER, AI, PDF }
    
    private String text;
    private Type type;
    private String pdfName;

    public ChatMessage(String text, Type type) {
        this.text = text;
        this.type = type;
    }

    public ChatMessage(String text, String pdfName) {
        this.text = text;
        this.type = Type.PDF;
        this.pdfName = pdfName;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Type getType() { return type; }
    public String getPdfName() { return pdfName; }
}