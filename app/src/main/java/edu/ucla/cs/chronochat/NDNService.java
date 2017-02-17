package edu.ucla.cs.chronochat;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.encoding.EncodingException;

import java.io.IOException;

public class NDNService extends Service {

    private static final String HOST = "localhost";
    private static final String NAME_URI = "/localhost/nfd/status/general";
    private static final String TAG = NDNService.class.getSimpleName();

    private static final Face FACE = new Face(HOST);
    private static final Name NAME = new Name(NAME_URI);    private boolean destroyed = false;

    // Thread for processing events on the face
    private final Thread processEventsThread = new Thread(new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "started processEventsThread");
            while (!destroyed) {
                try {
                    FACE.processEvents();
                } catch (IOException | EncodingException e) {
                    e.printStackTrace();
                }
                try {
                    Thread.sleep(100); // avoid hammering the CPU
                } catch (InterruptedException e) {}
            }
            Log.d(TAG, "stopped processEventsThread");
        }
    });

    /* Implements callbacks for Face.expressInterest() */
    private static final class PingHandler implements OnData, OnTimeout {
        private final String TAG = NDNService.class.getSimpleName();
        @Override
        public void onData(Interest interest, Data data) {
            // TODO: Broadcast result
            Log.d(TAG, "response recv'd");
        }
        @Override
        public void onTimeout(Interest interest) {
            // TODO: Broadcast result
            Log.d(TAG, "timed out");
        }
        public void startPing() {
            final Thread thread = new Thread(new Runnable() {
                @Override
                public void run () {
                    try {
                        FACE.expressInterest(NAME, PingHandler.this, PingHandler.this);
                    } catch (Exception e) {
                        // TODO: Broadcast result
                        Log.d(TAG, "Exception: " + e.toString());
                    }
                }
            });
            thread.start();
        }
    }

    private static final PingHandler handler = new PingHandler();

    @Override
    public void onCreate() {
        processEventsThread.start();
    }

    @Override
    public IBinder onBind(Intent _) { return null; }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        destroyed = true; // kills processEventsThread
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler.startPing();
        return START_STICKY;
    }

//    /* Basic NDN "services" provided to clients of NDNService */
//    public Face getLocalhost() { return localhost; }
}
