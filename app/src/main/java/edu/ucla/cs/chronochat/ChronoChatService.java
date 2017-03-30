package edu.ucla.cs.chronochat;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import net.named_data.jndn.Data;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnTimeout;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

import edu.ucla.cs.chronochat.ChatbufProto.ChatMessage;
import edu.ucla.cs.chronochat.ChatbufProto.ChatMessage.ChatMessageType;


public class ChronoChatService extends ChronoSyncService {

    private static final String TAG = "ChronoChatService";

    public static final String EXTRA_USERNAME = INTENT_PREFIX + "EXTRA_USERNAME",
                               EXTRA_CHATROOM = INTENT_PREFIX + "EXTRA_CHATROOM",
                               EXTRA_PREFIX = INTENT_PREFIX + "EXTRA_PREFIX",
                               EXTRA_ROSTER = INTENT_PREFIX + "EXTRA_ROSTER",
                               BCAST_RECEIVED_MSG = INTENT_PREFIX + "BCAST_RECEIVED_MSG",
                               BCAST_ROSTER = INTENT_PREFIX + "BCAST_ROSTER",
                               ACTION_GET_ROSTER = INTENT_PREFIX + "ACTION_GET_ROSTER",
                               ACTION_SEND = INTENT_PREFIX + "ACTION_SEND";

    private String activeUsername, activeChatroom, activePrefix;

    private HashMap<String, Integer> roster;

    Long heartbeatInterestID;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "received intent " + action);
            switch(action) {
                case ACTION_SEND:
                    byte[] message = intent.getByteArrayExtra(EXTRA_MESSAGE);
                    String prefix = intent.getStringExtra(EXTRA_PREFIX);
                    if (prefix == null) {
                        raiseError("ACTION_SEND intent requires prefix",
                                ErrorCode.OTHER_EXCEPTION);
                    } else if (message == null) {
                        raiseError("ACTION_SEND intent requires message data",
                                ErrorCode.OTHER_EXCEPTION);
                    } else {
                        sendMessage(message, prefix);
                    }
                    break;
                case ACTION_GET_ROSTER:
                    broadcastRoster();
                    break;
            }
        }

        return START_STICKY;
    }

    @Override
    protected void onReceivedSyncData(Interest interest, Data data) {

        String dataName = interest.getName().toString();
        Log.d(TAG, "received sync data for " + dataName);
        if (activeUsername == null) {
            Log.d(TAG, "ignoring sync data because we are logged out");
            return;
        }

        byte[] receivedData = data.getContent().getImmutableArray();
        ChatMessage message;
        try {
            message = ChatMessage.parseFrom(receivedData);
        } catch (InvalidProtocolBufferException e) {
            raiseError("error receiving message: unable to parse",
                    ErrorCode.OTHER_EXCEPTION, e);
            return;
        }

        String from = message.getFrom();
        ChatMessageType type = message.getType();
        int timestamp = message.getTimestamp();

        fakeJoinMessageIfNeeded(from, type);
        updateRoster(from, type, timestamp);
        broadcastReceivedMessage(receivedData);
    }

    @Override
    protected void setUpForApplication() {
        sendHelloAndExpressHeartbeatInterest();
    }

    @Override
    protected void raiseError(String logMessage, ErrorCode code) {
        prepareToLeaveChat();
        super.raiseError(logMessage, code);
    }

    protected void sendMessage(byte[] data, final String prefix) {

        ChatMessage message;
        try {
            message = ChatMessage.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            raiseError("error sending message: unable to parse",
                    ErrorCode.OTHER_EXCEPTION, e);
            return;
        }

        initializeServiceIfNeeded(message, prefix);
        ChatMessageType type = message.getType();

        if (type != ChatMessageType.JOIN) {  // JOIN would be handled by initializeServiceIfNeeded()
            if (type == ChatMessageType.LEAVE)
                prepareToLeaveChat();
            send(data);
        }
    }

    private void initializeServiceIfNeeded(final ChatMessage message, final String prefix) {

        final String username = message.getFrom(),
                chatroom = message.getTo();

        if (activeUsername == null || activeChatroom == null || activePrefix == null ||
                !activeUsername.equals(username) || !activeChatroom.equals(chatroom) ||
                !activePrefix.equals(prefix)) {

            activeUsername = username;
            activeChatroom = chatroom;
            activePrefix = prefix;

            roster = new HashMap<>();
            roster.put(activeUsername, 0);

            String separator = getString(R.string.uri_separator),
                    randomString = getRandomStringForDataPrefix(),
                    dataPrefix = prefix + separator + chatroom + separator + randomString,
                    broadcastPrefix = getString(R.string.broadcast_base_prefix) + separator +
                            getString(R.string.app_name_prefix_component) + separator +
                            chatroom;

            byte[] joinMessage = (message.getType() == ChatMessageType.JOIN) ?
                    message.toByteArray() : getControlMessage(ChatMessageType.JOIN);
            initializeService(dataPrefix, broadcastPrefix, joinMessage);
        }
    }

    private void prepareToLeaveChat() {
        activeUsername = activeChatroom = activePrefix = null;
        if (heartbeatInterestID != null) {                      // stop heartbeat
            face.removePendingInterest(heartbeatInterestID);
            heartbeatInterestID = null;
        }
    }

    private void fakeJoinMessageIfNeeded(String from, ChatMessageType type) {
        if (roster.containsKey(from) || type == ChatMessageType.JOIN) return;
        byte[] join = getControlMessage(ChatMessageType.JOIN, from);
        broadcastReceivedMessage(join);
    }

    private void updateRoster(String from, ChatMessageType type, int timestamp) {
        if (type == ChatMessageType.LEAVE) {
            roster.remove(from);
        } else {
            roster.put(from, timestamp);
        }
    }

    private void broadcastReceivedMessage(byte[] message) {
        Intent bcast = new Intent(BCAST_RECEIVED_MSG);
        bcast.putExtra(EXTRA_MESSAGE, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(bcast);
    }

    private void broadcastRoster() {
        if (roster == null) return;
        Intent rosterIntent = new Intent(BCAST_ROSTER);
        String[] usernames = roster.keySet().toArray(new String[0]);
        rosterIntent.putExtra(EXTRA_ROSTER, usernames);
        LocalBroadcastManager.getInstance(this).sendBroadcast(rosterIntent);
    }

    private void sendHelloAndExpressHeartbeatInterest() {

        byte[] hello = getControlMessage(ChatMessageType.HELLO);
        send(hello);

        Interest heartbeat = new Interest(new Name("/timeout"));
        heartbeat.setInterestLifetimeMilliseconds(60000);
        try {
            heartbeatInterestID = face.expressInterest(heartbeat, DummyOnData, OnHeartBeatTimeout);
        } catch (IOException e) {
            raiseError("error setting up heartbeat", ErrorCode.NFD_PROBLEM, e);
        }
    }


    private byte[] getControlMessage(ChatMessageType type) {
        return getControlMessage(type, activeUsername);
    }

    private byte[] getControlMessage(ChatMessageType type, String from) {
        int timestamp = (int) (System.currentTimeMillis() / 1000);
        byte[] message = ChatMessage.newBuilder()
                .setFrom(from)
                .setTo(activeChatroom)
                .setType(type)
                .setTimestamp(timestamp)
                .build()
                .toByteArray();
        return message;
    }

    private String getRandomStringForDataPrefix() {
        return UUID.randomUUID().toString();
    }

    private static final OnData DummyOnData = new OnData() {
        @Override
        public void onData(Interest interest, Data data) {
            Log.e(TAG, "DummyOnData callback should never be called!");
        }
    };

    private final OnTimeout OnHeartBeatTimeout = new OnTimeout() {
        @Override
        public void onTimeout(Interest interest) {
            sendHelloAndExpressHeartbeatInterest();
        }
    };
}
