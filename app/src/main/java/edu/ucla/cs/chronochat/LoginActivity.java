package edu.ucla.cs.chronochat;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity",
                                DEFAULT_CHATROOM = "chatroom",
                                DEFAULT_PREFIX = "/ndn/test";

    private EditText username, chatroom, prefix;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        username = (EditText) findViewById(R.id.username);
        chatroom = (EditText) findViewById(R.id.chatroom);
        prefix = (EditText) findViewById(R.id.prefix);

        chatroom.setText(DEFAULT_CHATROOM);
        prefix.setText(DEFAULT_PREFIX);
    }

    public void onSignIn(View view) {
        final String username = this.username.getText().toString().trim(),
                     chatroom = this.chatroom.getText().toString().trim(),
                     prefix = this.prefix.getText().toString().trim();

        // FIXME validation
        if (username.equals("") || chatroom.equals("") || prefix.equals("")) return;

        Log.d(TAG, "username = \"" + username + "\", chatroom = \"" + chatroom + "\", prefix = \"" +
                prefix + "\"");

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(ChronoChatService.EXTRA_USERNAME, username)
                .putExtra(ChronoChatService.EXTRA_CHATROOM, chatroom)
                .putExtra(ChronoChatService.EXTRA_PREFIX, prefix);

        setResult(RESULT_OK, intent);
        finish();
    }
}
