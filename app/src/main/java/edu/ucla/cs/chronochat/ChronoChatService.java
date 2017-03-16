package edu.ucla.cs.chronochat;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import net.named_data.jndn.Data;
import net.named_data.jndn.Interest;

import edu.ucla.cs.chronochat.ChatbufProto.ChatMessage;
import edu.ucla.cs.chronochat.ChatbufProto.ChatMessage.ChatMessageType;


public class ChronoChatService extends ChronoSyncService {

    private static final String TAG = "ChronoChatService";

    public static final String EXTRA_USERNAME = INTENT_PREFIX + "EXTRA_USERNAME",
                               EXTRA_CHATROOM = INTENT_PREFIX + "EXTRA_CHATROOM",
                               EXTRA_PREFIX = INTENT_PREFIX + "EXTRA_PREFIX",
                               BCAST_RECEIVED_MSG = INTENT_PREFIX + "BCAST_RECEIVED_MSG";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {
            final String username = intent.getStringExtra(EXTRA_USERNAME),
                    chatroom = intent.getStringExtra(EXTRA_CHATROOM),
                    prefix = intent.getStringExtra(EXTRA_PREFIX),
                    message = intent.getStringExtra(EXTRA_MESSAGE),
                    separator = getString(R.string.uri_separator),
                    dataPrefix = prefix + separator + chatroom + separator + username,
                    broadcastPrefix = getString(R.string.broadcast_base_prefix) + separator +
                            getString(R.string.app_name_prefix_component) + separator + chatroom;

            intent.putExtra(EXTRA_DATA_PREFIX, dataPrefix);
            intent.putExtra(EXTRA_BROADCAST_PREFIX, broadcastPrefix);
            if (message != null) {
                byte[] encodedMessage = encodeMessage(username, chatroom, ChatMessageType.CHAT,
                        message);
                intent.putExtra(EXTRA_MESSAGE, encodedMessage);
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onReceivedSyncData(Interest interest, Data data) {
        String dataName = interest.getName().toString();
        Message decodedMessage = decodeMessage(data.getContent().getImmutableArray());
        String receivedStr = decodedMessage.getText();
        Log.d(TAG, "received sync data for " + dataName + ":\n" + receivedStr);
        Intent bcast = new Intent(BCAST_RECEIVED_MSG);
        bcast.putExtra(EXTRA_MESSAGE, receivedStr)
             .putExtra(EXTRA_DATA_NAME, dataName);
        LocalBroadcastManager.getInstance(this).sendBroadcast(bcast);
    }

    private byte[] encodeMessage(String username, String chatroom, ChatMessageType type,
                                 String message, int timestamp) {
        ChatMessage chatMessage =
                ChatMessage.newBuilder()
                        .setFrom(username)
                        .setTo(chatroom)
                        .setData(message)
                        .setType(type).setTimestamp(timestamp)
                        .build();

        return chatMessage.toByteArray();
    }

    private byte[] encodeMessage(String username, String chatroom, ChatMessageType type,
                                 String message) {
        int timestamp = (int) (System.currentTimeMillis() / 1000);
        return encodeMessage(username, chatroom, type, message, timestamp);
    }

    private Message decodeMessage(byte[] encodedMessage) {
        String message = "[error parsing received message]", from = "[unknown user]";
        try {
            ChatMessage chatMessage = ChatMessage.parseFrom(encodedMessage);
            message = chatMessage.getData();
            from = chatMessage.getFrom();
        } catch (InvalidProtocolBufferException e) {
            Log.e(TAG, "error parsing received message", e);
        }
        return new Message(message, from);
    }

}
