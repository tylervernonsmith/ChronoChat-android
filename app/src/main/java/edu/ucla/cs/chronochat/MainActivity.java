package edu.ucla.cs.chronochat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private EditText editMessage;
    private TextView messages;

    private class LocalBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "broadcast received");
            if (intent.getAction() == ChronoSyncService.BCAST_RECEIVED) {
                String message = intent.getStringExtra(ChronoChatService.EXTRA_MESSAGE);
                Log.d(TAG, "received message \"" + message + "\"");
                messages.append(message + "\n");
            }
        }
    }

    private final LocalBroadcastReceiver broadcastReceiver = new LocalBroadcastReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate");

        editMessage = (EditText) findViewById(R.id.edit_message);
        messages = (TextView) findViewById(R.id.messages);

//        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
//        setSupportActionBar(myToolbar);

        IntentFilter statusIntentFilter = new IntentFilter(ChronoSyncService.BCAST_RECEIVED);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                broadcastReceiver,
                statusIntentFilter);

        Intent intent = new Intent(this, ChronoChatService.class);
        startService(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    public void sendMessage(View view) {
        Editable messageText = editMessage.getText();
        String message = messageText.toString();
        messageText.clear();
        messages.append(message + "\n");

        Intent intent = new Intent(this, ChronoChatService.class);
        intent.setAction(ChronoChatService.ACTION_SEND);
        intent.putExtra(ChronoChatService.EXTRA_MESSAGE, message);
        startService(intent);
    }

}

