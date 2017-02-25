package edu.ucla.cs.chronochat;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class LoginActivity extends AppCompatActivity {

    private EditText username, chatroom, prefix;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        username = (EditText) findViewById(R.id.username);
        chatroom = (EditText) findViewById(R.id.chatroom);
        prefix = (EditText) findViewById(R.id.prefix);
    }

    public void onSignIn(View view) {
        final String username = this.username.getText().toString(),
                     chatroom = this.chatroom.getText().toString(),
                     prefix = this.prefix.getText().toString();

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(ChronoChatService.EXTRA_USERNAME, username)
                .putExtra(ChronoChatService.EXTRA_CHATROOM, chatroom)
                .putExtra(ChronoChatService.EXTRA_PREFIX, prefix);

        setResult(RESULT_OK, intent);
        finish();
    }
}
