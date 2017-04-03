package edu.ucla.cs.chronochat;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import net.named_data.jndn.Data;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnTimeout;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

import edu.ucla.cs.chronochat.ChatbufProto.ChatMessage.ChatMessageType;


public class ChronoChatService extends ChronoSyncService {

    private static final String TAG = "ChronoChatService";

    public static final String EXTRA_USERNAME = INTENT_PREFIX + "EXTRA_USERNAME",
                               EXTRA_CHATROOM = INTENT_PREFIX + "EXTRA_CHATROOM",
                               EXTRA_PREFIX = INTENT_PREFIX + "EXTRA_PREFIX",
                               EXTRA_HUB = INTENT_PREFIX + "EXTRA_HUB",
                               EXTRA_MESSAGE = INTENT_PREFIX + "EXTRA_MESSAGE",
                               EXTRA_ROSTER = INTENT_PREFIX + "EXTRA_ROSTER",
                               BCAST_RECEIVED_MSG = INTENT_PREFIX + "BCAST_RECEIVED_MSG",
                               BCAST_ROSTER = INTENT_PREFIX + "BCAST_ROSTER",
                               ACTION_GET_ROSTER = INTENT_PREFIX + "ACTION_GET_ROSTER",
                               ACTION_SEND = INTENT_PREFIX + "ACTION_SEND",
                               ACTION_STOP = INTENT_PREFIX + "ACTION_STOP";

    private String activeUsername, activeChatroom, activePrefix, activeHub;
    private HashMap<String, Integer> roster, rosterAtLastZombieCheck;
    private Long heartbeatInterestID, zombieTimeoutInterestID;


    @Override
    public void onCreate() {
        Intent notificationIntent = new Intent(this, MainActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(getText(R.string.service_notification_title))
                .setContentText(getString(R.string.service_notification_text))
                .setContentIntent(pendingIntent)
                .setColor(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorPrimary))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();

        startForeground(MainActivity.SERVICE_NOTIFICATION_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "received intent " + action);
            switch(action) {
                case ACTION_SEND:
                    byte[] message = intent.getByteArrayExtra(EXTRA_MESSAGE);
                    String prefix = intent.getStringExtra(EXTRA_PREFIX),
                           hub = intent.getStringExtra(EXTRA_HUB);
                    if (prefix == null) {
                        raiseError("ACTION_SEND intent requires EXTRA_PREFIX",
                                ErrorCode.OTHER_EXCEPTION);
                    } else if (message == null) {
                        raiseError("ACTION_SEND intent requires EXTRA_PREFIX",
                                ErrorCode.OTHER_EXCEPTION);
                    } else if (hub == null) {
                        raiseError("ACTION_SEND intent requires EXTRA_HUB",
                                ErrorCode.OTHER_EXCEPTION);
                    } else {
                        sendMessage(message, prefix, hub);
                    }
                    break;
                case ACTION_GET_ROSTER:
                    broadcastRoster();
                    break;
                case ACTION_STOP:
                    stopSelf();
                    break;
            }
        }

        return START_STICKY;
    }

    @Override
    protected void handleApplicationData(byte[] receivedData) {
        if (activeUsername == null) {
            Log.d(TAG, "ignoring received message because we are logged out");
            return;
        }

        ChronoChatMessage message = new ChronoChatMessage(receivedData);
        if (message.getParseError()) {
            raiseError("error receiving message: unable to parse",
                    ErrorCode.OTHER_EXCEPTION);
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
    protected void doApplicationSetup() {
        expressHeartbeatInterest();
        expressZombieTimeoutInterest();
    }

    protected void sendMessage(byte[] data, final String prefix, final String hub) {

        ChronoChatMessage message = new ChronoChatMessage(data);
        if (message.getParseError()) {
            raiseError("error sending message: unable to parse",
                    ErrorCode.OTHER_EXCEPTION);
            return;
        }

        initializeServiceIfNeeded(message, prefix, hub);
        ChatMessageType type = message.getType();

        if (type != ChatMessageType.JOIN) {  // JOIN would be handled by initializeServiceIfNeeded()
            if (type == ChatMessageType.LEAVE)
                prepareToLeaveChat();
            send(data);
        }
    }

    private void initializeServiceIfNeeded(final ChronoChatMessage message, final String prefix,
                                           final String hub) {

        final String username = message.getFrom(),
                chatroom = message.getTo();

        if (!loginInfoIsSet() ||
                !activeUsername.equals(username) || !activeChatroom.equals(chatroom) ||
                !activePrefix.equals(prefix) || !activeHub.equals(hub)) {

            activeUsername = username;
            activeChatroom = chatroom;
            activePrefix = prefix;
            activeHub = hub;

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
            initializeService(hub, dataPrefix, broadcastPrefix, joinMessage);
        }
    }

    private void prepareToLeaveChat() {
        Log.d(TAG, "preparing to leave chat...");
        clearLoginInfo();
        if (heartbeatInterestID != null) {                      // stop heartbeat
            if (face != null) {
                Log.d(TAG, "removing heartbeat timeout interest");
                face.removePendingInterest(heartbeatInterestID);
            }
            heartbeatInterestID = null;
        }
        if (zombieTimeoutInterestID != null) {                   // stop zombie timeout
            if (face != null) {
                Log.d(TAG, "removing zombie timeout interest");
                face.removePendingInterest(zombieTimeoutInterestID);
            }
            zombieTimeoutInterestID = null;
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

    private void expressHeartbeatInterest() {
        Log.d(TAG, "(re)starting heartbeat timeout");
        heartbeatInterestID = expressTimeoutInterest(OnHeartBeatTimeout, 60000,
                "error setting up heartbeat");
    }

    private void expressZombieTimeoutInterest() {
        Log.d(TAG, "(re)starting zombie timeout");
        zombieTimeoutInterestID = expressTimeoutInterest(OnZombieTimeout, 120000,
                "error setting up zombie timeout");
    }

    private Long expressTimeoutInterest(OnTimeout onTimeout, long lifetimeMillis, String errorMsg) {
        Interest timeout = new Interest(new Name("/timeout"));
        timeout.setInterestLifetimeMilliseconds(lifetimeMillis);
        try {
            return face.expressInterest(timeout, DummyOnData, onTimeout);
        } catch (IOException e) {
            raiseError(errorMsg, ErrorCode.NFD_PROBLEM, e);
            return null;
        }
    }

    private byte[] getControlMessage(ChatMessageType type) {
        return getControlMessage(type, activeUsername);
    }

    private byte[] getControlMessage(ChatMessageType type, String from) {
        ChronoChatMessage message = new ChronoChatMessage(from, activeChatroom, type);
        return message.toByteArray();
    }

    private String getRandomStringForDataPrefix() {
        return UUID.randomUUID().toString();
    }

    private boolean loginInfoIsSet() {
        return (activeUsername != null && activeChatroom != null && activePrefix != null &&
                activeHub != null);
    }

    private void clearLoginInfo() {
        Log.d(TAG, "clearing login info");
        activeUsername = activeChatroom = activePrefix = activeHub = null;
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
            Log.d(TAG, "sending HELLO");
            byte[] hello = getControlMessage(ChatMessageType.HELLO);
            send(hello);
            expressHeartbeatInterest();
        }
    };

    private final OnTimeout OnZombieTimeout = new OnTimeout() {
        @Override
        public void onTimeout(Interest interest) {

            Log.d(TAG, "checking for zombies...");
            if (rosterAtLastZombieCheck == null) rosterAtLastZombieCheck = new HashMap<>();
            HashMap<String, Integer> updatedRoster = new HashMap<>();

            for (String user : roster.keySet()) {

                Integer currentTimestamp = roster.get(user),
                        lastTimestamp = rosterAtLastZombieCheck.get(user);

                if (!currentTimestamp.equals(lastTimestamp) || user.equals(activeUsername)) {
                    Log.d(TAG, "'" + user + "' seems alive");
                    updatedRoster.put(user, currentTimestamp);
                } else {
                    Log.d(TAG, "'" + user + "' seems to be a zombie");
                    byte[] leave = getControlMessage(ChatMessageType.LEAVE, user);
                    broadcastReceivedMessage(leave); // create fake LEAVE message for chat log
                    // Note that we're leaving the user out of "updatedRoster"...
                }
            }

            roster = updatedRoster;
            rosterAtLastZombieCheck = new HashMap<>(updatedRoster);
            expressZombieTimeoutInterest();
        }
    };
}
