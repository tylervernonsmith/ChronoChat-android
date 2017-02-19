package edu.ucla.cs.chronochat;

import android.util.Log;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnTimeout;

import java.io.IOException;


public class PingThreadHandler extends NDNThreadHandler implements OnData, OnTimeout {

    private static final String TAG = PingThreadHandler.class.getSimpleName(),
                                NAME_URI = "/localhost/nfd/status/general";
    private static final Name NAME = new Name(NAME_URI);


    public PingThreadHandler(NDNService service) {
        super(service);
    }

    @Override
    protected void onThreadStart() {
        try {
            service.getFace().expressInterest(NAME, this, this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onData(Interest interest, Data data) {
        stop();
        Log.d(TAG, "response recv'd");
        // TODO: Broadcast result
    }

    @Override
    public void onTimeout(Interest interest) {
        stop();
        Log.d(TAG, "timed out");
        // TODO: Broadcast result
    }
}
