package edu.ucla.cs.chronochat;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import net.named_data.jndn.Data;
import net.named_data.jndn.Interest;


public class ChronoChatService extends ChronoSyncService {

    private static final String TAG = "ChronoChatService";

    public static final String EXTRA_USERNAME = INTENT_PREFIX + "EXTRA_USERNAME",
                               EXTRA_CHATROOM = INTENT_PREFIX + "EXTRA_CHATROOM",
                               EXTRA_PREFIX = INTENT_PREFIX + "EXTRA_PREFIX",
                               BCAST_RECEIVED_MSG = INTENT_PREFIX + "BCAST_RECEIVED_MSG";

    private String activeUsername, activeChatroom, activePrefix;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "received intent " + action);
            final String username = intent.getStringExtra(EXTRA_USERNAME),
                    chatroom = intent.getStringExtra(EXTRA_CHATROOM),
                    prefix = intent.getStringExtra(EXTRA_PREFIX);

            if (activeUsername == null || activeChatroom == null || activePrefix == null ||
                    !activeUsername.equals(username) || !activeChatroom.equals(chatroom) ||
                    !activePrefix.equals(prefix)) {

                activeUsername = username;
                activeChatroom = chatroom;
                activePrefix = prefix;

                String separator = getString(R.string.uri_separator),
                       dataPrefix = prefix + separator + chatroom + separator + username,
                       broadcastPrefix = getString(R.string.broadcast_base_prefix) + separator +
                               getString(R.string.app_name_prefix_component) + separator +
                               chatroom;

                initializeService(dataPrefix, broadcastPrefix);
            }

            if (ACTION_SEND.equals(action)) {
                byte[] message = intent.getByteArrayExtra(EXTRA_MESSAGE);
                if (message != null) {
                    send(message);
                }
            }

        }

        return START_STICKY;
    }

    @Override
    public void onReceivedSyncData(Interest interest, Data data) {
        String dataName = interest.getName().toString();
        Log.d(TAG, "received sync data for " + dataName);
        byte[] receivedData = data.getContent().getImmutableArray();
        Intent bcast = new Intent(BCAST_RECEIVED_MSG);
        bcast.putExtra(EXTRA_MESSAGE, receivedData)
             .putExtra(EXTRA_DATA_NAME, dataName);
        LocalBroadcastManager.getInstance(this).sendBroadcast(bcast);
    }
}
