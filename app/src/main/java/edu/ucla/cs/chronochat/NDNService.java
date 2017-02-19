package edu.ucla.cs.chronochat;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import net.named_data.jndn.Face;
import net.named_data.jndn.Name;


public class NDNService extends Service {

    private static final String HOST = "localhost",
                                PREFIX_STR = "/test",
                                TAG = NDNService.class.getSimpleName();

    private final Name PREFIX = new Name(PREFIX_STR); // FIXME
    private final Face FACE = new Face(HOST);

    private PingThreadHandler pingThreadHandler;
    private RegisterPrefixThreadHandler registerPrefixThreadHandler;


    @Override
    public void onCreate() {
        pingThreadHandler = new PingThreadHandler(this);
        registerPrefixThreadHandler = new RegisterPrefixThreadHandler(this);
    }

    @Override
    public IBinder onBind(Intent _) { return null; }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        // stop all threads just in case
        pingThreadHandler.stop();
        registerPrefixThreadHandler.stop();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        registerPrefixThreadHandler.start();
        return START_STICKY;
    }

    public Face getFace() { return FACE; }
    public Name getPrefix() { return PREFIX; }
}
