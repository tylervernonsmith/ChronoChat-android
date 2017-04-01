package edu.ucla.cs.chronochat;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import edu.ucla.cs.chronochat.ChatbufProto.ChatMessage;
import edu.ucla.cs.chronochat.ChatbufProto.ChatMessage.ChatMessageType;

/* Wrapper Parcelable class for ChatMessage. Probably would have been nicer to extend ChatMessage
 *   but the Protobuf document warns against inheriting from generated message classes.
 */
public class ChronoChatMessage implements Parcelable {

    private ChatMessage message;
    private static final String TAG = "ChronoChatMessage";

    public ChatMessage getMessage() { return message; }

    public ChronoChatMessage(final ChatMessage fromMessage) {
        message = fromMessage;
    }

    protected ChronoChatMessage(Parcel in) {
        int arrayLength = in.readInt();
        byte[] messageBytes = new byte[arrayLength];
        in.readByteArray(messageBytes);
        try {
            message = ChatMessage.parseFrom(messageBytes);
        } catch (InvalidProtocolBufferException e) {
            Log.e(TAG, "error parsing ChatMessage from Parcel", e);
            int timestamp = (int) (System.currentTimeMillis() / 1000);
            message = ChatMessage.newBuilder()
                        .setFrom("[unknown user]")
                        .setTo("[unknown chatroom]")
                        .setData("[error parsing message]")
                        .setType(ChatMessageType.CHAT)
                        .setTimestamp(timestamp)
                        .build();
        }
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
