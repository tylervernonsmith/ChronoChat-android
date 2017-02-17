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

    private final IBinder binder = new NDNBinder();
    private final Face localhost = new Face("localhost");
    private boolean bound = false, destroyed = false;

    private final String TAG = NDNService.class.getSimpleName();

    public class NDNBinder extends Binder {
        NDNService getService() { return NDNService.this; }
    }

    // Thread for processing events on the face
    private final Thread processEventsThread = new Thread(new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "started processEventsThread");
            while (!destroyed) {
                try {
                    Thread.sleep(100); // avoid hammering the CPU
                    localhost.processEvents();
                } catch (InterruptedException | IOException | EncodingException e) {
                    e.printStackTrace();
                }
            }
            Log.d(TAG, "stopped processEventsThread");
        }
    });

    @Override
    public void onCreate() {
        processEventsThread.start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        bound = true;
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        bound = false;
        return false;  // no need to call onRebind()
    }

    @Override
    public void onDestroy() {
        destroyed = true; // kills processEventsThread
    }

    /* Basic NDN "services" provided to clients of NDNService */
    public Face getLocalhost() { return localhost; }
}
