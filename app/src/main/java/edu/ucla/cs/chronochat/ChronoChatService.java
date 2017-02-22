package edu.ucla.cs.chronochat;

import android.util.Log;


public class ChronoChatService extends ChronoSyncService {

    private static final String TAG = ChronoChatService.class.getSimpleName();

    @Override
    public void onCreate() {
        sentData.add(null); // placeholder
        sentData.add("yo");
    }
}
