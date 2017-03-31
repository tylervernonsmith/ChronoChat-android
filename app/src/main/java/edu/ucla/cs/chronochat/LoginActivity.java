package edu.ucla.cs.chronochat;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private EditText username, chatroom, prefix, hub;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        username = (EditText) findViewById(R.id.username);
        chatroom = (EditText) findViewById(R.id.chatroom);
        prefix = (EditText) findViewById(R.id.prefix);
        hub = (EditText) findViewById(R.id.hub);

        chatroom.setText(getString(R.string.default_chatroom));
        prefix.setText(getString(R.string.default_prefix));
        hub.setText(getString(R.string.default_hub));
    }

    public void onSignIn(View view) {
        final String username = this.username.getText().toString().trim(),
                     chatroom = this.chatroom.getText().toString().trim(),
                     prefix = this.prefix.getText().toString().trim(),
                     hub = this.hub.getText().toString().trim();

        // FIXME validation
        if (username.equals("") || chatroom.equals("") || prefix.equals("") || hub.equals(""))
            return;

        Log.d(TAG, "username = \"" + username + "\", chatroom = \"" + chatroom + "\", prefix = \"" +
                prefix + "\"" + ", hub = \"" + hub + "\"");

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(ChronoChatService.EXTRA_USERNAME, username)
                .putExtra(ChronoChatService.EXTRA_CHATROOM, chatroom)
                .putExtra(ChronoChatService.EXTRA_PREFIX, prefix)
                .putExtra(ChronoChatService.EXTRA_HUB, hub);

        setResult(RESULT_OK, intent);
        finish();
    }
}
