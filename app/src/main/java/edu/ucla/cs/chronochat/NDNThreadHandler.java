package edu.ucla.cs.chronochat;


import android.util.Log;

import net.named_data.jndn.Face;
import net.named_data.jndn.encoding.EncodingException;

import java.io.IOException;


public abstract class NDNThreadHandler {

    protected final NDNService service;

    private boolean stopped = false;

    private final Thread thread = new Thread(new Runnable() {
        @Override
        public void run () {
            final String TAG = NDNThreadHandler.this.getClass().getSimpleName();
            stopped = false;
            Log.d(TAG, "thread started");
            onThreadStart();
            while (!stopped) {
                try {
                    service.getFace().processEvents();
                } catch (IOException | EncodingException e) {
                    e.printStackTrace();
                }
                try {
                    Thread.sleep(500); // avoid hammering the CPU
                } catch (InterruptedException e) {
                    Log.d(TAG, "thread interrupted");
                }
            }
            Log.d(TAG, "thread stopped");
        }
    });


    public NDNThreadHandler(NDNService service) {
        this.service = service;
    }

    public void start() { thread.start(); }
    public void stop() { stopped = true; }

    protected abstract void onThreadStart();

}
