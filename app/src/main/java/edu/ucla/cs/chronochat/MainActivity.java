package edu.ucla.cs.chronochat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.NotificationCompat;
import android.text.Editable;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import net.named_data.jndn.Name;
import edu.ucla.cs.chronochat.ChronoSyncService.ErrorCode;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity",
                                SAVED_USERNAME = TAG + ".username",
                                SAVED_CHATROOM = TAG + ".chatroom",
                                SAVED_PREFIX = TAG + ".prefix";

    // index of username component in data names
    private static final int USERNAME_COMPONENT_INDEX = -5,
                             NOTIFICATION_ID = 0;

    private EditText editMessage;
    private ViewGroup messages;
    private ScrollView containerForMessages;

    private String username, chatroom, prefix, lastMessageSentBy;

    private boolean activityVisible = false;

    private class LocalBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "broadcast received");
            switch (intent.getAction()) {
                case ChronoSyncService.BCAST_RECEIVED:
                    handleReceivedMessage(intent);
                    break;
                case ChronoSyncService.BCAST_ERROR:
                    handleError(intent);
                    break;
            }
        }
    }

    private final LocalBroadcastReceiver broadcastReceiver = new LocalBroadcastReceiver();

    protected void registerBroadcastReceiver(IntentFilter intentFilter) {
        LocalBroadcastManager.getInstance(this).registerReceiver(
                broadcastReceiver,
                intentFilter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate");

        editMessage = (EditText) findViewById(R.id.edit_message);
        messages = (ViewGroup) findViewById(R.id.messages);
        containerForMessages = (ScrollView) messages.getParent();

        IntentFilter broadcastIntentFilter = new IntentFilter(ChronoSyncService.BCAST_RECEIVED);
        registerBroadcastReceiver(broadcastIntentFilter);
        broadcastIntentFilter = new IntentFilter(ChronoSyncService.BCAST_ERROR);
        registerBroadcastReceiver(broadcastIntentFilter);

        getLoginInfo(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        activityVisible = true;
        hideNotification();
    }

    @Override
    public void onStop() {
        super.onStop();
        activityVisible = false;
    }

    private void getLoginInfo(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            messages.removeAllViews();
            startActivityForResult(new Intent(this, LoginActivity.class), 0);
        } else {
            username = savedInstanceState.getString(SAVED_USERNAME);
            chatroom = savedInstanceState.getString(SAVED_CHATROOM);
            prefix = savedInstanceState.getString(SAVED_PREFIX);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            startActivityForResult(new Intent(this, LoginActivity.class), 0);
        }
        username = data.getStringExtra(ChronoChatService.EXTRA_USERNAME);
        chatroom = data.getStringExtra(ChronoChatService.EXTRA_CHATROOM);
        prefix = data.getStringExtra(ChronoChatService.EXTRA_PREFIX);

        Intent intent = getChronoChatServiceIntent(null);
        startService(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public void onSaveInstanceState(Bundle savedState) {
        savedState.putString(SAVED_USERNAME, username);
        savedState.putString(SAVED_CHATROOM, chatroom);
        savedState.putString(SAVED_PREFIX, prefix);
        super.onSaveInstanceState(savedState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.chat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_leave:
                getLoginInfo(null);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void sendMessage(View view) {
        Editable messageText = editMessage.getText();
        String message = messageText.toString();
        if (message.equals("")) return;
        messageText.clear();
        addSentMessageToView(message);

        Intent intent = getChronoChatServiceIntent(ChronoChatService.ACTION_SEND);
        intent.putExtra(ChronoChatService.EXTRA_MESSAGE, message);
        startService(intent);
    }

    private Intent getChronoChatServiceIntent(String action) {
        Intent intent = new Intent(this, ChronoChatService.class);
        intent.setAction(action);
        intent.putExtra(ChronoChatService.EXTRA_USERNAME, username)
                .putExtra(ChronoChatService.EXTRA_CHATROOM, chatroom)
                .putExtra(ChronoChatService.EXTRA_PREFIX, prefix);
        return intent;
    }

    private void handleReceivedMessage(Intent intent) {
        String message = intent.getStringExtra(ChronoChatService.EXTRA_MESSAGE);
        String dataNameStr = intent.getStringExtra(ChronoChatService.EXTRA_DATA_NAME);
        Name dataName = new Name(dataNameStr);
        String receivedFrom = dataName.get(USERNAME_COMPONENT_INDEX).toEscapedString();
        Log.d(TAG, "received message \"" + message + "\"" + " from " + receivedFrom);
        addReceivedMessageToView(message, receivedFrom);
    }

    private void addReceivedMessageToView(String message, String receivedFrom) {
        addMessageToView(message, receivedFrom);
    }

    private void addSentMessageToView(String message) {
        addMessageToView(message, username);
    }

    private void addMessageToView(String message, String sentBy) {

        TextView textView = new TextView(this);
        textView.setText(message);
        final int gravity;

        if (sentBy.equals(username)) {
            gravity = Gravity.RIGHT;
        } else {
            gravity = Gravity.LEFT;
            showNotification(message, sentBy);
        }

        if (!sentBy.equals(lastMessageSentBy)) {
            TextView labelTextView = new TextView(this);
            labelTextView.setText(sentBy);
            labelTextView.setTypeface(null, Typeface.BOLD);
            labelTextView.setGravity(gravity);
            messages.addView(labelTextView);
        }
        lastMessageSentBy = sentBy;

        textView.setGravity(gravity);
        messages.addView(textView);
        scrollToLastMessage();
    }

    private void scrollToLastMessage() {
        // FIXME
        containerForMessages.post(new Runnable() {
            @Override
            public void run() {
                containerForMessages.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    private void showNotification(String message, String sentBy) {

        if (activityVisible) return;

        NotificationCompat.Builder builder =
                (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.notification_icon)
                        .setContentTitle(sentBy)
                        .setContentText(message)
                        .setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_LIGHTS);

        // FIXME is this done right?
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(getApplicationContext(),
                (int)System.currentTimeMillis(), intent, 0);

        builder.setContentIntent(resultPendingIntent);
        builder.setAutoCancel(true);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void hideNotification() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private void handleError(Intent intent) {
        ErrorCode errorCode =
                (ErrorCode) intent.getSerializableExtra(ChronoSyncService.EXTRA_ERROR_CODE);
        String toastText = "";
        switch (errorCode) {
            case NFD_PROBLEM:
                toastText = "Encountered an NFD error... maybe the service has closed?";
                break;
            case OTHER_EXCEPTION:
                toastText = "Encountered an error, please check debug logs...";
                break;
        }
        Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_LONG).show();
        getLoginInfo(null);
    }

}

