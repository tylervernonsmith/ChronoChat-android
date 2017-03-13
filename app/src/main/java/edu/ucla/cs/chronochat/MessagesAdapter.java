package edu.ucla.cs.chronochat;

import android.content.Context;
import android.util.Log;
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

    private String loggedInUsername;
    private static final String TAG = "MessagesAdapter";
    private static final int TYPE_SENT_MESSAGE_WITH_USERNAME = 0,
                             TYPE_SENT_MESSAGE_ONLY = 1,
                             TYPE_RECEIVED_MESSAGE_WITH_USERNAME = 2,
                             TYPE_RECEIVED_MESSAGE_ONLY = 3,
                             VIEW_TYPE_COUNT = 4;

    private static class ViewHolder {
        TextView usernameView, messageTextView;
    }

    public MessagesAdapter(Context context, ArrayList<Message> messages) {
        super(context, 0, messages);
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        Message thisMessage = getItem(position);
        boolean sent = thisMessage.getUsername().equals(loggedInUsername);
        if (position > 0) {
            Message previousMessage = getItem(position - 1);
            if (thisMessage.getUsername().equals(previousMessage.getUsername()))
                return (sent ? TYPE_SENT_MESSAGE_ONLY : TYPE_RECEIVED_MESSAGE_ONLY);
        }
        return (sent ? TYPE_SENT_MESSAGE_WITH_USERNAME : TYPE_RECEIVED_MESSAGE_WITH_USERNAME);
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        Message message = getItem(position);
        ViewHolder viewHolder;

        if (view == null) {
            viewHolder = new ViewHolder();
            view = getInflatedView(position, parent);
            viewHolder.usernameView = (TextView) view.findViewById(R.id.message_username);
            viewHolder.messageTextView = (TextView) view.findViewById(R.id.message_text);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        TextView usernameView = viewHolder.usernameView,
                 messageTextView = viewHolder.messageTextView;

        messageTextView.setText(message.getText());
        if (usernameView != null) usernameView.setText(message.getUsername());

        return view;
    }

    private View getInflatedView(int position, ViewGroup parent) {
        int type = getItemViewType(position), layout;
        switch (type) {
            case TYPE_SENT_MESSAGE_WITH_USERNAME:
                layout = R.layout.item_sent_message_with_username;
                break;
            case TYPE_SENT_MESSAGE_ONLY:
                layout = R.layout.item_sent_message;
                break;
            case TYPE_RECEIVED_MESSAGE_WITH_USERNAME:
                layout = R.layout.item_received_message_with_username;
                break;
            default:
                layout = R.layout.item_received_message;
                break;
        }
        return LayoutInflater.from(getContext()).inflate(layout, parent, false);
    }

    public void setLoggedInUsername(String username) { loggedInUsername = username; }

}
