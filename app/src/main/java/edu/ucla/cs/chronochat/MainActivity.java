package edu.ucla.cs.chronochat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.NotificationCompat;
import android.text.Editable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import net.named_data.jndn.Name;

import java.util.ArrayList;

import edu.ucla.cs.chronochat.ChronoSyncService.ErrorCode;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity",
                                SAVED_USERNAME = TAG + ".username",
                                SAVED_CHATROOM = TAG + ".chatroom",
                                SAVED_PREFIX = TAG + ".prefix";

    // index of username component in data names
    private static final int USERNAME_COMPONENT_INDEX = -3,
                             NOTIFICATION_ID = 0;

    private EditText editMessage;
    private ListView messageView;
    private ArrayList<Message> messageList = new ArrayList<>();
    private MessagesAdapter messageListAdapter;

    private String username, chatroom, prefix;

    private boolean activityVisible = false;

    private class LocalBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "broadcast received");
            switch (intent.getAction()) {
                case ChronoChatService.BCAST_RECEIVED_MSG:
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
        messageView = (ListView) findViewById(R.id.message_view);

        messageListAdapter = new MessagesAdapter(this, messageList);
        messageView.setAdapter(messageListAdapter);

        IntentFilter broadcastIntentFilter = new IntentFilter(ChronoChatService.BCAST_RECEIVED_MSG);
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
            messageList.clear();
            messageListAdapter.notifyDataSetChanged();
            startActivityForResult(new Intent(this, LoginActivity.class), 0);
        } else {
            setUsername(savedInstanceState.getString(SAVED_USERNAME));
            prefix = savedInstanceState.getString(SAVED_PREFIX);
            setChatroom(savedInstanceState.getString(SAVED_CHATROOM));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            startActivityForResult(new Intent(this, LoginActivity.class), 0);
        }
        setUsername(data.getStringExtra(ChronoChatService.EXTRA_USERNAME));
        prefix = data.getStringExtra(ChronoChatService.EXTRA_PREFIX);
        setChatroom(data.getStringExtra(ChronoChatService.EXTRA_CHATROOM));

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

    private void setUsername(String username) {
        this.username = username;
        messageListAdapter.setLoggedInUsername(username);
    }

    private void setChatroom(String chatroom) {
        this.chatroom = chatroom;
        getSupportActionBar().setTitle(chatroom);
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
        showNotification(message, receivedFrom);
        addReceivedMessageToView(message, receivedFrom);
    }

    private void addReceivedMessageToView(String message, String receivedFrom) {
        addMessageToView(message, receivedFrom);
    }

    private void addSentMessageToView(String message) {
        addMessageToView(message, username);
    }

    private void addMessageToView(String text, String sentBy) {
        Message message = new Message(text, sentBy);
        messageList.add(message);
        messageListAdapter.notifyDataSetChanged();
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

