package edu.ucla.cs.chronochat;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private EditText username, chatroom, prefix;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        username = (EditText) findViewById(R.id.username);
        chatroom = (EditText) findViewById(R.id.chatroom);
        prefix = (EditText) findViewById(R.id.prefix);

        chatroom.setText(getString(R.string.default_chatroom));
        prefix.setText(getString(R.string.default_prefix));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.login_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_quit:
                // NOTE: MainActivity should close application upon receiving RESULT_CANCELED
                Intent intent = new Intent(this, MainActivity.class);
                setResult(RESULT_CANCELED, intent);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onSignIn(View view) {
        final String username = this.username.getText().toString().trim(),
                     chatroom = this.chatroom.getText().toString().trim(),
                     prefix = this.prefix.getText().toString().trim();

        if (username.equals("") || chatroom.equals("") || prefix.equals(""))
            return;

        Log.d(TAG, "username = \"" + username + "\", chatroom = \"" + chatroom + "\", prefix = \"" +
                prefix + "\"");

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(ChronoChatService.EXTRA_USERNAME, username)
                .putExtra(ChronoChatService.EXTRA_CHATROOM, chatroom)
                .putExtra(ChronoChatService.EXTRA_PREFIX, prefix)
                .putExtra(ChronoChatService.EXTRA_HUB, getString(R.string.hub_uri));

        setResult(RESULT_OK, intent);
        finish();
    }
}
