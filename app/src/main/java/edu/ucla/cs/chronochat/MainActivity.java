package edu.ucla.cs.chronochat;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private class LocalBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "broadcast received");
        }
    }

    private final LocalBroadcastReceiver broadcastReceiver = new LocalBroadcastReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        IntentFilter statusIntentFilter = new IntentFilter(ChronoSyncService.BROADCAST_ACTION);
//        LocalBroadcastManager.getInstance(this).registerReceiver(
//                broadcastReceiver,
//                statusIntentFilter);
        Log.d(TAG, "onCreate");
        Intent intent = new Intent(this, ChronoChatService.class);
        startService(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

//    @Override
//    protected void onDestroy() {}

}

