package edu.ucla.cs.chronochat;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import net.named_data.jndn.Face;
import net.named_data.jndn.encoding.EncodingException;

import java.io.IOException;

public class NDNService extends Service {

    private final Face localhost = new Face("localhost");
    private boolean destroyed = false;

    private final String TAG = NDNService.class.getSimpleName();

    // Thread for processing events on the face
    private final Thread processEventsThread = new Thread(new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "started processEventsThread");
            while (!destroyed) {
                try {
                    localhost.processEvents();
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

    /* Basic NDN "services" provided to clients of NDNService */
    public Face getLocalhost() { return localhost; }
}
