package edu.ucla.cs.chronochat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private EditText editMessage;
    private ViewGroup messages;

    private String username, chatroom;

    private class LocalBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "broadcast received");
            if (intent.getAction() == ChronoSyncService.BCAST_RECEIVED) {
                String message = intent.getStringExtra(ChronoChatService.EXTRA_MESSAGE);
                Log.d(TAG, "received message \"" + message + "\"");
                addReceivedMessageToView(message);
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
        messages = (ViewGroup) findViewById(R.id.messages);

//        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
//        setSupportActionBar(myToolbar);

        IntentFilter statusIntentFilter = new IntentFilter(ChronoSyncService.BCAST_RECEIVED);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                broadcastReceiver,
                statusIntentFilter);

        startActivityForResult(new Intent(this, LoginActivity.class), 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            startActivityForResult(new Intent(this, LoginActivity.class), 0);
        }
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
        if (message.equals("")) return;
        messageText.clear();
        addSentMessageToView(message);

        Intent intent = new Intent(this, ChronoChatService.class);
        intent.setAction(ChronoChatService.ACTION_SEND);
        intent.putExtra(ChronoChatService.EXTRA_MESSAGE, message);
        startService(intent);
    }

    private void addReceivedMessageToView(String message) {
        addMessageToView(message, true);
    }

    private void addSentMessageToView(String message) {
        addMessageToView(message, false);
    }

    private void addMessageToView(String message, boolean received) {
        TextView textView = new TextView(this);
        textView.setText(message);
        if (received) {
            textView.setTypeface(null, Typeface.BOLD);
        } else {
            textView.setGravity(Gravity.RIGHT);
        }
        messages.addView(textView);
    }

}

