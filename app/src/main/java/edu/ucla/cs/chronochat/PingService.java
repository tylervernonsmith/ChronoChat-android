package edu.ucla.cs.chronochat;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnTimeout;

public class PingService extends IntentService {

    private static final String HOST = "localhost";
    private static final String NAME_URI = "/localhost/nfd/status/general";
    private static final String TAG = NDNService.class.getSimpleName();

    private static final Face FACE = new Face(HOST);
    private static final Name NAME = new Name(NAME_URI);

    private static final class PingHandler implements OnData, OnTimeout {
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
    }

    private static final PingHandler handler = new PingHandler();


    public PingService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            FACE.expressInterest(NAME, handler, handler);
            while (true) {
                FACE.processEvents();
                Thread.sleep(100); // avoid hammering the CPU
            }
        } catch (Exception e) {
            // TODO: Broadcast result
            Log.d(TAG, "Exception: " + e.getLocalizedMessage());
        }

    }
}
