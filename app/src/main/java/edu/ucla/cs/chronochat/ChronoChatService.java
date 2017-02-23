package edu.ucla.cs.chronochat;

import android.content.Intent;
import android.util.Log;


public class ChronoChatService extends ChronoSyncService {

    private static final String TAG = ChronoChatService.class.getSimpleName();

    /* Intent constants */
    public static final String
            ACTION_SEND = "edu.ucla.cs.ChronoChat.ChronoChatService.ACTION_SEND",
            EXTRA_MESSAGE = "edu.ucla.cs.ChronoChat.ChronoChatService.EXTRA_MESSAGE";

    @Override
    public void onCreate() {
        send(null); // placeholder
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() == ACTION_SEND) {
            String message = intent.getStringExtra(EXTRA_MESSAGE);
            send(message);
        }

        return super.onStartCommand(intent, flags, startId);
    }
}
