package edu.ucla.cs.chronochat;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;

import edu.ucla.cs.chronochat.ChatbufProto.ChatMessage;
import edu.ucla.cs.chronochat.ChatbufProto.ChatMessage.ChatMessageType;


/**
 * Created by tvsmith on 3/12/17.
 */

public class MessagesAdapter extends ArrayAdapter<ChatMessage> {

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

    public MessagesAdapter(Context context, ArrayList<ChatMessage> messages) {
        super(context, 0, messages);
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage thisMessage = getItem(position);
        boolean sent = thisMessage.getFrom().equals(loggedInUsername);
        if (position > 0) {
            ChatMessage previousMessage = getItem(position - 1);
            if (thisMessage.getFrom().equals(previousMessage.getFrom()))
                return (sent ? TYPE_SENT_MESSAGE_ONLY : TYPE_RECEIVED_MESSAGE_ONLY);
        }
        return (sent ? TYPE_SENT_MESSAGE_WITH_USERNAME : TYPE_RECEIVED_MESSAGE_WITH_USERNAME);
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        ChatMessage message = getItem(position);
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

        switch (message.getType().getNumber()) {
            case ChatMessageType.CHAT_VALUE:
                messageTextView.setText(message.getData());
                break;
            case ChatMessageType.JOIN_VALUE:
                messageTextView.setText("[joined the chat]");
                break;
            case ChatMessageType.LEAVE_VALUE:
                messageTextView.setText("[left the chat]");
                break;
            case ChatMessageType.HELLO_VALUE:
                messageTextView.setText("[HELLO]");
                break;
            case ChatMessageType.OTHER_VALUE:
                messageTextView.setText("[OTHER]");
                break;
            default:
                messageTextView.setText("[unhandled message type]");
        }

        if (usernameView != null) usernameView.setText(message.getFrom());

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

    public void addMessageToView(ChatMessage message) {
        add(message);
        notifyDataSetChanged();
    }
}
