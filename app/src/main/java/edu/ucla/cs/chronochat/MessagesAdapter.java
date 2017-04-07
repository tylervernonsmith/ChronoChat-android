package edu.ucla.cs.chronochat;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;

import edu.ucla.cs.chronochat.ChatbufProto.ChatMessage.ChatMessageType;


class MessagesAdapter extends ArrayAdapter<ChronoChatMessage> {

    private static class ViewHolder {
        TextView usernameView, messageTextView;
    }

    public static final String TAG = "MessagesAdapter";

    private String loggedInUsername;
    private static final int TYPE_SENT_MESSAGE_WITH_USERNAME = 0,
                             TYPE_SENT_MESSAGE_ONLY = 1,
                             TYPE_RECEIVED_MESSAGE_WITH_USERNAME = 2,
                             TYPE_RECEIVED_MESSAGE_ONLY = 3,
                             VIEW_TYPE_COUNT = 4;


    MessagesAdapter(Context context, ArrayList<ChronoChatMessage> messages) {
        super(context, 0, messages);
        // We want full control over notifyDataSetChanged() to prevent unnecessary calls when sorting
        setNotifyOnChange(false);
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        ChronoChatMessage thisMessage = getItem(position);
        boolean sent = thisMessage.getFrom().equals(loggedInUsername);
        if (position > 0) {
            ChronoChatMessage previousMessage = getItem(position - 1);
            if (thisMessage.getFrom().equals(previousMessage.getFrom()))
                return (sent ? TYPE_SENT_MESSAGE_ONLY : TYPE_RECEIVED_MESSAGE_ONLY);
        }
        return (sent ? TYPE_SENT_MESSAGE_WITH_USERNAME : TYPE_RECEIVED_MESSAGE_WITH_USERNAME);
    }

    @NonNull
    @Override
    public View getView(int position, View view, @NonNull ViewGroup parent) {
        ChronoChatMessage message = getItem(position);
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

        messageTextView.setText(message.getTimestampString() + ": ");
        switch (message.getType().getNumber()) {
            case ChatMessageType.CHAT_VALUE:
                messageTextView.append(message.getData());
                break;
            case ChatMessageType.JOIN_VALUE:
                messageTextView.append(getContext().getString(R.string.message_join));
                break;
            case ChatMessageType.LEAVE_VALUE:
                messageTextView.append(getContext().getString(R.string.message_leave));
                break;
            case ChatMessageType.OTHER_VALUE:
                messageTextView.append(getContext().getString(R.string.message_other));
                break;
            default:
                messageTextView.append(getContext().getString(R.string.message_unhandled));
        }

        if (usernameView != null) usernameView.setText(message.getFrom());

        return view;
    }

    @Override
    public void add(ChronoChatMessage message) {
        int itemCount = getCount();
        ChronoChatMessage lastMessage = null;
        if (itemCount > 0) lastMessage = getItem(itemCount - 1);
        super.add(message);
        // Avoid re-sorting if we're clearly adding a new message to the end of the list
        if (lastMessage != null &&
                ChronoChatMessage.timestampOrder.compare(message, lastMessage) >= 0) {
            notifyDataSetChanged();
        } else {
            sortByTimestamp();
        }
    }

    @Override
    public void addAll(ChronoChatMessage[] messages) {
        super.addAll(messages);
        sortByTimestamp();
    }

    @Override
    public void addAll(Collection<? extends ChronoChatMessage> messages) {
        super.addAll(messages);
        sortByTimestamp();
    }

    @Override
    public void insert(ChronoChatMessage message, int index) {
        super.insert(message, index);
        sortByTimestamp();
    }

    @Override
    public void remove(ChronoChatMessage message) {
        super.remove(message);
        notifyDataSetChanged();
    }

    @Override
    public void clear() {
        super.clear();
        notifyDataSetChanged();
    }

    @Override
    public void sort(Comparator<? super ChronoChatMessage> comparator) {
        super.sort(comparator);
        notifyDataSetChanged();
    }

    @Override
    public void notifyDataSetChanged() {
        Log.d(TAG, "notifyDataSetChanged()");
        super.notifyDataSetChanged();
        setNotifyOnChange(false); // this flag gets reset to "true" by the superclass method
    }

    public void sortByTimestamp() {
        sort(ChronoChatMessage.timestampOrder);
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

    void setLoggedInUsername(String username) { loggedInUsername = username; }
}
