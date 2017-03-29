package edu.ucla.cs.chronochat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.app.DialogFragment;
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

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;

import edu.ucla.cs.chronochat.ChronoSyncService.ErrorCode;
import edu.ucla.cs.chronochat.ChatbufProto.ChatMessage;
import edu.ucla.cs.chronochat.ChatbufProto.ChatMessage.ChatMessageType;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity",
                                SAVED_USERNAME = TAG + ".username",
                                SAVED_CHATROOM = TAG + ".chatroom",
                                SAVED_PREFIX = TAG + ".prefix";

    // index of username component in data names
    private static final int NOTIFICATION_ID = 0;

    private EditText editMessage;
    private ArrayList<ChatMessage> messageList = new ArrayList<>();
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
        ListView messageView = (ListView) findViewById(R.id.message_view);

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
                leaveChatroom();
                return true;
            case R.id.action_show_roster:
                showRoster();
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
        Editable messageField = editMessage.getText();
        String text = messageField.toString();
        if (text.equals("")) return;
        messageField.clear();

        ChatMessage message = encodeMessage(username, chatroom, ChatMessageType.CHAT, text);
        sendMessage(message);
    }

    private void sendMessage(ChatMessage message) {
        messageListAdapter.addMessageToView(message);
        Intent intent = getChronoChatServiceIntent(null);
        intent.putExtra(ChronoChatService.EXTRA_MESSAGE, message.toByteArray());
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
        byte[] encodedMessage = intent.getByteArrayExtra(ChronoChatService.EXTRA_MESSAGE);
        ChatMessage message;
        try {
            message = ChatMessage.parseFrom(encodedMessage);
        } catch (InvalidProtocolBufferException e) {
            message = encodeMessage("[unknown user]", chatroom, ChatMessageType.CHAT,
                    "[error parsing message]");
        }
        Log.d(TAG, "received message from " + message.getFrom());
        showNotification(message);
        messageListAdapter.addMessageToView(message);
    }


    private void showNotification(ChatMessage message) {

        if (activityVisible) return;

        ChatMessageType type = message.getType();
        if (type != ChatMessageType.CHAT) return;

        String from = message.getFrom(), text = message.getData();

        NotificationCompat.Builder builder =
                (NotificationCompat.Builder) new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.notification_icon)
                        .setContentTitle(from)
                        .setContentText(text)
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

    private void showRoster() {
        String[] roster = {"<test placeholder>"};
        Bundle args = new Bundle();
        args.putStringArray(ChronoChatService.EXTRA_ROSTER, roster);
        RosterDialogFragment dialog = new RosterDialogFragment();
        dialog.setArguments(args);
        dialog.show(getFragmentManager(), "RosterDialogFragment");
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


    private void leaveChatroom() {
        ChatMessage leave = encodeMessage(username, chatroom, ChatMessageType.LEAVE);
        sendMessage(leave);
        getLoginInfo(null);
    }

    private ChatMessage encodeMessage(String username, String chatroom, ChatMessageType type) {
        return encodeMessage(username, chatroom, type, "");
    }

    private ChatMessage encodeMessage(String username, String chatroom, ChatMessageType type,
                                      String text) {
        int timestamp = (int) (System.currentTimeMillis() / 1000);
        return encodeMessage(username, chatroom, type, text, timestamp);
    }

    private ChatMessage encodeMessage(String username, String chatroom, ChatMessageType type,
                                      String message, int timestamp) {
        return  ChatMessage.newBuilder()
                .setFrom(username)
                .setTo(chatroom)
                .setData(message)
                .setType(type)
                .setTimestamp(timestamp)
                .build();
    }
}

