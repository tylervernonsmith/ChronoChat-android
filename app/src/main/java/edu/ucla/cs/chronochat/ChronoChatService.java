package edu.ucla.cs.chronochat;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import net.named_data.jndn.Data;
import net.named_data.jndn.Interest;
import net.named_data.jndn.util.Blob;


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
                    separator = getString(R.string.uri_separator),
                    dataPrefix = prefix + separator + chatroom + separator + username,
                    broadcastPrefix = getString(R.string.broadcast_base_prefix) + separator +
                            getString(R.string.app_name_prefix_component) + separator + chatroom;

            intent.putExtra(EXTRA_DATA_PREFIX, dataPrefix);
            intent.putExtra(EXTRA_BROADCAST_PREFIX, broadcastPrefix);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onReceivedSyncData(Interest interest, Data data) {
        Blob blob = data.getContent();
        String receivedStr = blob.toString(), dataName = interest.getName().toString();
        Log.d(TAG, "received sync data for " + dataName + ":\n" + receivedStr);
        Intent bcast = new Intent(BCAST_RECEIVED_MSG);
        bcast.putExtra(EXTRA_MESSAGE, receivedStr)
             .putExtra(EXTRA_DATA_NAME, dataName);
        LocalBroadcastManager.getInstance(this).sendBroadcast(bcast);
    }

}
