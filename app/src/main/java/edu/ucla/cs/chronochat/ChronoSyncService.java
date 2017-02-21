package edu.ucla.cs.chronochat;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.sync.ChronoSync2013;
import net.named_data.jndn.util.Blob;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public abstract class ChronoSyncService extends Service {

    private static final String FACE_URI = "localhost",
                                TAG = ChronoSyncService.class.getSimpleName();
    private static final long SESSION_NUM = 0; // FIXME?
    private static final double SYNC_LIFETIME = 5000.0; // FIXME?

    /* Intent constants */
    public static final String
            ACTION_SEND = "edu.ucla.cs.ChronoChat.ChronoSyncService.ACTION_SEND";

    protected String username = "testuser"; // FIXME
    private Face face = new Face(FACE_URI);
    private Name dataPrefix = new Name("/test/" + username),   // FIXME
                 broadcastPrefix = new Name("/test/broadcast"); // FIXME
    protected ChronoSync2013 sync;
    private boolean networkThreadShouldStop;
    private KeyChain keyChain;
    protected Map<String, Long> highestRequestedSeqNums;
    protected ArrayList<String> sentData = new ArrayList<String>();

    private final Thread networkThread = new Thread(new Runnable() {
        @Override
        public void run () {
            networkThreadShouldStop = false;
            Log.d(TAG, "network thread started");
            initializeKeyChain();
            setCommandSigningInfo();
            registerDataPrefix();
            setUpChronoSync();
            while (!networkThreadShouldStop) {
                publishSeqNumsIfNeeded();
                try {
                    face.processEvents();
                } catch (IOException | EncodingException e) {
                    Log.d(TAG, "Error calling processEvents()");
                    e.printStackTrace();
                }
                try {
                    Thread.sleep(500); // avoid hammering the CPU
                } catch (InterruptedException e) {
                    Log.d(TAG, "network thread interrupted");
                }
            }
            Log.d(TAG, "network thread stopped");
        }
    });

    private void startNetworkThread() {
        networkThread.start();
    }
    private void stopNetworkThread() {
        networkThreadShouldStop = true;
    }


    private void initializeKeyChain() {
        Log.d(TAG, "initializing keychain");
        MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
        MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
        IdentityManager identityManager = new IdentityManager(identityStorage, privateKeyStorage);
        keyChain = new KeyChain(identityManager);
        keyChain.setFace(face);
    }

    private void setCommandSigningInfo() {
        Log.d(TAG, "setting command signing info");
        Name defaultCertificateName;
        try {
            defaultCertificateName = keyChain.getDefaultCertificateName();
        } catch (SecurityException e) {
            Log.d(TAG, "unable to get default certificate name");

            // FIXME??? This is based on apps-NDN-Whiteboard/helpers/Utils.buildTestKeyChain()...
            Name testIdName = new Name("/test/identity");
            try {
                defaultCertificateName = keyChain.createIdentityAndCertificate(testIdName);
                keyChain.getIdentityManager().setDefaultIdentity(testIdName);
                Log.d(TAG, "created default ID: " + defaultCertificateName.toString());
            } catch (SecurityException e2) {
                Log.d(TAG, "unable to create default identity");
                e.printStackTrace();
                defaultCertificateName = new Name("/bogus/certificate/name");
            }
        }
        face.setCommandSigningInfo(keyChain, defaultCertificateName);
    }

    private void registerDataPrefix() {
        try {
            Log.d(TAG, "registering data prefix: " + dataPrefix.toString());
            face.registerPrefix(dataPrefix, OnDataInterest, OnDataPrefixRegisterFailed);
            Log.d(TAG, "successfully registered data prefix");
        } catch (IOException | SecurityException e) {
            Log.d(TAG, "exception when registering data prefix: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setUpChronoSync() {
        try {
            sync = new ChronoSync2013(OnReceivedChronoSyncState, OnChronoSyncInitialized,
                    dataPrefix, broadcastPrefix, SESSION_NUM, face, keyChain,
                    keyChain.getDefaultCertificateName(), SYNC_LIFETIME,
                    OnBroadcastPrefixRegisterFailed);
        } catch (IOException | SecurityException e) {
            Log.d(TAG, "exception when setting up ChronoSync: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processSyncState(ChronoSync2013.SyncState syncState) {
        String dataPrefix = syncState.getDataPrefix();
        long syncSeqNum = syncState.getSequenceNo();

        if (dataPrefix.contains(username)) {
            // Ignore sync state for our own user (FIXME is this really needed?)
            Log.d(TAG, "ignoring sync state for own user");
        } else if (!highestRequestedSeqNums.keySet().contains(dataPrefix)) {
            // If we don't know about this user yet, add them to our list
            Log.d(TAG, "adding newly discovered sync prefix " + dataPrefix);
            highestRequestedSeqNums.put(dataPrefix, syncSeqNum);
        } else {
            requestMissingSeqNums(dataPrefix, syncSeqNum);
        }
    }

    private void requestMissingSeqNums(String dataPrefix, long syncSeqNum) {
        long highestRequestedSeqNum = highestRequestedSeqNums.get(dataPrefix);
        while (syncSeqNum > highestRequestedSeqNum) {
            long missingSeqNum = highestRequestedSeqNum + 1;
            String missingDataNameStr = dataPrefix + "/" + missingSeqNum;
            Name missingDataName = new Name(missingDataNameStr);
            Log.d(TAG, "requesting missing seqnum " + missingSeqNum);
            expressDataInterest(missingDataName);
            highestRequestedSeqNum = missingSeqNum;
        }
        highestRequestedSeqNums.put(dataPrefix, syncSeqNum);
    }


    private void expressDataInterest(Name dataName) {
        Log.d(TAG, "requesting interest for " + dataName.toString());
        try {
            face.expressInterest(new Name(dataPrefix), OnReceivedSyncData,
                    OnSyncDataInterestTimeout);
        } catch (IOException e) {
            Log.d(TAG, "failed to express data interest");
            e.printStackTrace();
        }
    }

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

    public int lastDataSeqNum() { return sentData.size() - 1; }
    public long lastPublishedSeqNum() { return sync.getSequenceNo(); }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // TODO handle intent
        Log.d(TAG, "received intent");
        startNetworkThread();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent _) { return null; }


    /***** Callbacks for NDN network thread *****/

    private final OnInterestCallback OnDataInterest = new OnInterestCallback() {
        @Override
        public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                               InterestFilter filterData) {
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
    };

    private final ChronoSync2013.OnReceivedSyncState OnReceivedChronoSyncState =
            new ChronoSync2013.OnReceivedSyncState() {
                @Override
                public void onReceivedSyncState(List syncStates, boolean isRecovery) {
                    Log.d(TAG, "sync states received");
                    // FIXME handle recovery states properly (?)
                    for (ChronoSync2013.SyncState syncState :
                            (List<ChronoSync2013.SyncState>) syncStates) {
                        Log.d(TAG, "received sync state " + syncState.toString());
                        processSyncState(syncState);
                    }
                    Log.d(TAG, "finished processing " + syncStates.size() + " sync states");
                }
            };

    private final ChronoSync2013.OnInitialized OnChronoSyncInitialized =
            new ChronoSync2013.OnInitialized() {
                @Override
                public void onInitialized() {
                    Log.d(TAG, "ChronoSync Initialized");
                    // TODO: Announce success via broadcast intent?
                }
            };

    private final OnRegisterFailed OnDataPrefixRegisterFailed =
            new OnRegisterFailed() {
                @Override
                public void onRegisterFailed(Name prefix) {
                    stopNetworkThread();
                    Log.d(TAG, "failed to register application prefix " + prefix.toString());
                    // TODO: Announce failure via broadcast intent?
                }
            };

    private final OnRegisterFailed OnBroadcastPrefixRegisterFailed = new OnRegisterFailed() {
        @Override
        public void onRegisterFailed(Name prefix) {
            stopNetworkThread();
            Log.d(TAG, "failed to register broadcast prefix " + prefix.toString());
            // TODO: Announce failure via broadcast intent?
        }
    };

    public final OnData OnReceivedSyncData = new OnData() {
        @Override
        public void onData(Interest interest, Data data) {
            // TODO: Broadcast received data
        }
    };

    public final OnTimeout OnSyncDataInterestTimeout = new OnTimeout() {
        @Override
        public void onTimeout(Interest interest) {
            Log.d(TAG, "data interest timeout");
            // FIXME? we just try again...
            expressDataInterest(interest.getName());
        }
    };
}
