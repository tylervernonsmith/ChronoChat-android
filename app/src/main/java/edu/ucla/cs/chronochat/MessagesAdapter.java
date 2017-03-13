package edu.ucla.cs.chronochat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by tvsmith on 3/12/17.
 */

public class MessagesAdapter extends ArrayAdapter<Message> {

    public MessagesAdapter(Context context, ArrayList<Message> messages) {
        super(context, 0, messages);
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {

        Message message = getItem(position);
        if (view == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.item_message_with_username,
                    parent, false);

        }

        TextView username = (TextView) view.findViewById(R.id.message_username),
                 text     = (TextView) view.findViewById(R.id.message_text);

        username.setText(message.getUsername());
        text.setText(message.getText());

        return view;
    }

}
