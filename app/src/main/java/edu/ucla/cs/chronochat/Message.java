package edu.ucla.cs.chronochat;

public class Message {

    private String text, username;

    public Message(String text, String username) {
        this.text = text;
        this.username = username;
    }

    public String getText() { return text; }
    public String getUsername() { return username; }
}
