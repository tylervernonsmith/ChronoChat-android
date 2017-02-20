package edu.ucla.cs.chronochat;

import android.util.Log;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.util.Blob;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class ChronoChatService extends ChronoSyncService {

    private static final String TAG = ChronoChatService.class.getSimpleName();

    @Override
    protected void onDataInterest(Name prefix, Interest interest, Face face,
                                  long interestFilterId, InterestFilter filterData) {

        Name interestName = interest.getName();
        Log.d(TAG, "data interest received: " + interestName.toString());

        Name.Component lastInterestComponent = interestName.get(-1);
        int requestedSeqNum = Integer.parseInt(lastInterestComponent.toEscapedString());

        if (lastPublishedSeqNum() >= requestedSeqNum) {
            Log.d(TAG, "responding to data interest");
            Data response = new Data(interestName);
            Blob content = new Blob(sentData.get(requestedSeqNum).getBytes());
            response.setContent(content);
            try {
                face.putData(response);
            } catch (IOException e) {
                Log.d(TAG, "failure when responding to data interest");
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onReceivedChronoSyncState(List syncStates, boolean isRecovery) {
        Log.d(TAG, "sync state received");
        // TODO
    }

    @Override
    protected void onChronoSyncInitialized() {
        Log.d(TAG, "ChronoSync Initialized");
        // TODO: Announce success via broadcast intent?
    }

    @Override
    protected void onDataPrefixRegisterFailed(Name prefix) {
        Log.d(TAG, "failed to register application prefix " + prefix.toString());
        // TODO: Announce failure via broadcast intent?
    }

    @Override
    protected void onBroadcastPrefixRegisterFailed(Name prefix) {
        Log.d(TAG, "failed to register broadcast prefix " + prefix.toString());
        // TODO: Announce failure via broadcast intent?
    }

    @Override
    protected void publishSeqNumsIfNeeded() {
        while(lastPublishedSeqNum() < lastDataSeqNum()) {
            try {
                sync.publishNextSequenceNo();
                Log.d(TAG, "published seqnum " + lastPublishedSeqNum());
            } catch (IOException | SecurityException e) {
                Log.d(TAG, "failed to publish seqnum " + (lastPublishedSeqNum() + 1));
                e.printStackTrace();
            }
        }
    }
}
