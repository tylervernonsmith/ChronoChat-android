package edu.ucla.cs.chronochat;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import net.named_data.jndn.Face;
import net.named_data.jndn.Name;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.sync.ChronoSync2013;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Queue;


public class ChronoSyncService extends Service {

    private static final String FACE_URI = "localhost",
                                TAG = ChronoSyncService.class.getSimpleName();

    private Face face = new Face(FACE_URI);
    private Name applicationPrefix, broadcastPrefix;
    private ChronoSync2013 sync;
    private ArrayList<String> sentMessages;

    /* Intent constants */
    public static final String
            ACTION_SEND = "edu.ucla.cs.ChronoChat.ChronoSyncService.ACTION_SEND";

    @Override
    public IBinder onBind(Intent _) { return null; }


    private void send(String message) {
        sentMessages.add(message);
        publishNextSeqNum();
    }

    private void publishNextSeqNum() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                logSeqNum();
                try {
                    sync.publishNextSequenceNo();
                    Log.d(TAG, "published next seqnum");
                } catch (IOException | SecurityException e) {
                    e.printStackTrace();
                }
                logSeqNum();
            }
        }).start();
    }

    private void logSeqNum() {
        Log.d(TAG, "current seqnum " + sync.getSequenceNo());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // handle intent
        return START_STICKY;
    }
}
