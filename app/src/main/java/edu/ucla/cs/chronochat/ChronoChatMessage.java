package edu.ucla.cs.chronochat;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import java.text.DateFormat;
import java.util.Date;

import edu.ucla.cs.chronochat.ChatbufProto.ChatMessage;
import edu.ucla.cs.chronochat.ChatbufProto.ChatMessage.ChatMessageType;


/* Wrapper Parcelable class for ChatMessage. Might have been nicer to extend ChatMessage
 *   but the Protobuf documentation warns against inheriting from generated message classes.
 */
public class ChronoChatMessage implements Parcelable {

    private static final String TAG = "ChronoChatMessage";

    private ChatMessage message;
    private boolean parseError = false;


    public ChronoChatMessage(String username, String chatroom, ChatMessageType type,
                             String data) {
        setMessage(username, chatroom, type, data);
    }

    public ChronoChatMessage(String username, String chatroom, ChatMessageType type) {
        setMessage(username, chatroom, type);
    }

    public ChronoChatMessage(byte[] encodedMessage) {
        setMessage(encodedMessage);
    }


    private void setMessage(byte[] encodedMessage) {
        try {
            message = ChatMessage.parseFrom(encodedMessage);
        } catch (InvalidProtocolBufferException e) {
            Log.e(TAG, "error parsing ChatMessage from Parcel", e);
            setMessage("[unknown user]", "[unknown chatroom]", ChatMessageType.CHAT,
                    "[error parsing message]");
            parseError = true;
        }
    }

    private void setMessage(String username, String chatroom, ChatMessageType type,
                            String data, int timestamp) {
        message = ChatMessage.newBuilder()
                    .setFrom(username)
                    .setTo(chatroom)
                    .setData(data)
                    .setType(type)
                    .setTimestamp(timestamp)
                    .build();
    }

    private void setMessage(String username, String chatroom, ChatMessageType type,
                            String data) {
        int currentTimeSeconds = (int) (System.currentTimeMillis() / 1000);
        setMessage(username, chatroom, type, data, currentTimeSeconds);
    }

    private void setMessage(String username, String chatroom, ChatMessageType type) {
        setMessage(username, chatroom, type, "");
    }


    public String getFrom() { return message.getFrom(); }
    public String getTo() { return message.getTo(); }
    public String getData() { return message.getData(); }
    public ChatMessageType getType() { return message.getType(); }
    public int getTimestamp() { return message.getTimestamp(); }
    public boolean getParseError() { return parseError; }
    public byte[] toByteArray() { return message.toByteArray(); }

    public String getTimestampString() {
        long longTimestamp = (long) getTimestamp();
        Date date = new Date(longTimestamp * 1000);
        DateFormat formatter = DateFormat.getTimeInstance();
        return formatter.format(date);
    }


    /* Parcelable implementation  */

    private ChronoChatMessage(Parcel in) {
        int arrayLength = in.readInt();
        byte[] messageBytes = new byte[arrayLength];
        in.readByteArray(messageBytes);
        setMessage(messageBytes);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        byte[] messageBytes = message.toByteArray();
        dest.writeInt(messageBytes.length);
        dest.writeByteArray(messageBytes);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ChronoChatMessage> CREATOR = new Creator<ChronoChatMessage>() {
        @Override
        public ChronoChatMessage createFromParcel(Parcel in) {
            return new ChronoChatMessage(in);
        }

        @Override
        public ChronoChatMessage[] newArray(int size) {
            return new ChronoChatMessage[size];
        }
    };
}
