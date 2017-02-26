package edu.ucla.cs.chronochat;

import android.content.Intent;


public class ChronoChatService extends ChronoSyncService {

    private static final String TAG = ChronoChatService.class.getSimpleName();

    public static final String EXTRA_USERNAME = INTENT_PREFIX + "EXTRA_USERNAME",
                               EXTRA_CHATROOM = INTENT_PREFIX + "EXTRA_CHATROOM",
                               EXTRA_PREFIX = INTENT_PREFIX + "EXTRA_PREFIX",
                               APP_NAME = "chronochat-android";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {


        final String username = intent.getStringExtra(EXTRA_USERNAME),
                chatroom = intent.getStringExtra(EXTRA_CHATROOM),
                prefix = intent.getStringExtra(EXTRA_PREFIX),
                userPrefixComponent = prefix + "/" + username,
                groupPrefixComponent = "/" + APP_NAME + "/" + chatroom;

        intent.putExtra(EXTRA_USER_PREFIX_COMPONENT, userPrefixComponent);
        intent.putExtra(EXTRA_GROUP_PREFIX_COMPONENT, groupPrefixComponent);
        return super.onStartCommand(intent, flags, startId);
    }


}
