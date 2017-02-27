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
import net.named_data.jndn.OnRegisterSuccess;
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
import java.util.HashMap;
import java.util.List;
import java.util.Random;


public abstract class ChronoSyncService extends Service {

    private static final String FACE_URI = "localhost",
                                TAG = ChronoSyncService.class.getSimpleName(),
                                BROADCAST_BASE_PREFIX = "/ndn/broadcast";
    private static final long SESSION_NUM = 0; // FIXME?
    private static final double SYNC_LIFETIME = 5000.0; // FIXME?

    /* Intent constants */
    public static final String
            INTENT_PREFIX = "edu.ucla.cs.ChronoChat." + TAG + ".",
            ACTION_SEND = INTENT_PREFIX + "ACTION_SEND",
            BCAST_RECEIVED = INTENT_PREFIX + "BCAST_RECEIVED",
            EXTRA_MESSAGE = INTENT_PREFIX + "EXTRA_MESSAGE",
            EXTRA_DATA_NAME = INTENT_PREFIX + "EXTRA_DATA_NAME";
    protected static final String
            EXTRA_USER_PREFIX_COMPONENT = INTENT_PREFIX + "EXTRA_USER_PREFIX_COMPONENT",
            EXTRA_GROUP_PREFIX_COMPONENT = INTENT_PREFIX + "EXTRA_GROUP_PREFIX_COMPONENT";

    protected String userPrefixComponent, groupPrefixComponent;
    private Face face;
    private Name dataPrefix, broadcastPrefix;

    protected ChronoSync2013 sync;
    private boolean networkThreadShouldStop;
    protected boolean syncInitialized = false;
    private KeyChain keyChain;
    protected HashMap<String, Long> highestRequestedSeqNums;
    protected ArrayList<String> sentData;
    protected long registeredDataPrefixId;

    private final Thread networkThread = new Thread(new Runnable() {
        @Override
        public void run () {
            networkThreadShouldStop = false;
            Log.d(TAG, "network thread started");
            try {
                initializeKeyChain();
                setCommandSigningInfo();
                registerDataPrefix();
                setUpChronoSync();
            } catch (Exception e) {
                stopNetworkThread();
                e.printStackTrace();
                // TODO raise some kind of error/uninitialized flag?
            }
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

    private void initializeService() {
        Log.d(TAG, "initializing service...");
        face = new Face(FACE_URI);
        dataPrefix = new Name(userPrefixComponent + groupPrefixComponent);
        broadcastPrefix = new Name(BROADCAST_BASE_PREFIX + groupPrefixComponent);
        highestRequestedSeqNums = new HashMap<>();
        sentData = new ArrayList<>();
        send(null); // create placeholder for seqnum 0
        startNetworkThread();
        Log.d(TAG, "service initialized");
    }

    private void startNetworkThread() {
        if (!networkThread.isAlive()) {
            networkThreadShouldStop = false;
            networkThread.start();
        }
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
                defaultCertificateName = new Name("/bogus/certificate/name"); // FIXME
            }
        }
        face.setCommandSigningInfo(keyChain, defaultCertificateName);
    }

    private void registerDataPrefix() {
        try {
            Log.d(TAG, "registering data prefix...");
            registeredDataPrefixId = face.registerPrefix(dataPrefix, OnDataInterest,
                    OnDataPrefixRegisterFailed, OnDataPrefixRegisterSuccess);
        } catch (IOException | SecurityException e) {
            Log.d(TAG, "exception when registering data prefix: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setUpChronoSync() {
        Log.d(TAG, "initializing ChronoSync...");
        try {
            sync = new ChronoSync2013(OnReceivedChronoSyncState, OnChronoSyncInitialized,
                    dataPrefix, broadcastPrefix, SESSION_NUM, face, keyChain,
                    keyChain.getDefaultCertificateName(), SYNC_LIFETIME,
                    OnBroadcastPrefixRegisterFailed);
        } catch (IOException | SecurityException e) {
            Log.d(TAG, "exception when initializing ChronoSync: " + e.getMessage());
            stopNetworkThread();
        }
    }

    private void processSyncState(ChronoSync2013.SyncState syncState) {
        String syncDataPrefix = syncState.getDataPrefix();
        long syncSeqNum = syncState.getSequenceNo();

        if (syncDataPrefix.toString().equals(this.dataPrefix.toString())) {
            // Ignore sync state for our own user (FIXME is this really needed?)
            Log.d(TAG, "ignoring sync state for own user");
        } else {
            if (!highestRequestedSeqNums.keySet().contains(syncDataPrefix)) {
                // If we don't know about this user yet, add them to our list
                Log.d(TAG, "recording newly discovered sync prefix " + syncDataPrefix);
                highestRequestedSeqNums.put(syncDataPrefix, 0l);
            }
            requestMissingSeqNums(syncDataPrefix, syncSeqNum);
        }
    }

    private void requestMissingSeqNums(String syncDataPrefix, long syncSeqNum) {
        long highestRequestedSeqNum = highestRequestedSeqNums.get(syncDataPrefix);
        while (syncSeqNum > highestRequestedSeqNum) {
            long missingSeqNum = highestRequestedSeqNum + 1;
            String missingDataNameStr = syncDataPrefix + "/" + missingSeqNum;
            Name missingDataName = new Name(missingDataNameStr);
            Log.d(TAG, "requesting missing seqnum " + missingSeqNum);
            expressDataInterest(missingDataName);
            highestRequestedSeqNum = missingSeqNum;
        }
        highestRequestedSeqNums.put(syncDataPrefix, syncSeqNum);
    }


    private void expressDataInterest(Name dataName) {
        Log.d(TAG, "expressing interest for " + dataName.toString());
        try {
            face.expressInterest(dataName, OnReceivedSyncData,
                    OnSyncDataInterestTimeout);
        } catch (IOException e) {
            Log.d(TAG, "failed to express data interest");
            e.printStackTrace();
        }
    }

    protected void publishSeqNumsIfNeeded() {
        if (!syncInitialized) return;
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

        if (intent != null) {
            Log.d(TAG, "received intent " + intent.getAction());

            String userPrefixComponentFromIntent = intent.getStringExtra(EXTRA_USER_PREFIX_COMPONENT),
                    groupPrefixComponentFromIntent = intent.getStringExtra(EXTRA_GROUP_PREFIX_COMPONENT);

            if (userPrefixComponent == null
                    || groupPrefixComponent == null
                    || !userPrefixComponent.equals(userPrefixComponentFromIntent)
                    || !groupPrefixComponent.equals(groupPrefixComponentFromIntent)) {

                Log.d(TAG, "new user/group prefix detected...");
                userPrefixComponent = userPrefixComponentFromIntent;
                groupPrefixComponent = groupPrefixComponentFromIntent;
                shutdown();
                initializeService();
            }
            if (intent.getAction() == ACTION_SEND) {
                String message = intent.getStringExtra(EXTRA_MESSAGE);
                send(message);
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // attempt to clean up after ourselves
        Log.d(TAG, "onDestroy() cleaning up...");
        shutdown();
        Log.d(TAG, "exiting onDestroy");
    }

    @Override
    public IBinder onBind(Intent _) { return null; }

    private void shutdown() {
        syncInitialized = false;
        if (sync != null) sync.shutdown();
        if (face != null) {
            face.removeRegisteredPrefix(registeredDataPrefixId);
            face.shutdown();
        }
        stopNetworkThread();
        while (networkThread.isAlive()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.d(TAG, "interruption while waiting for network thread to stop");
                e.printStackTrace();
            }
        }
        face = null;
        sync = null;
    }

    protected void send(String message) {
        sentData.add(message);
        Log.d(TAG, "sending \"" + message + "\"");
    }


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
                        Log.d(TAG, "received sync state for " + syncState.getDataPrefix());
                        processSyncState(syncState);
                    }
                    Log.d(TAG, "finished processing " + syncStates.size() + " sync states");
                }
            };

    private final ChronoSync2013.OnInitialized OnChronoSyncInitialized =
            new ChronoSync2013.OnInitialized() {
                @Override
                public void onInitialized() {
                    Log.d(TAG, "ChronoSync initialization complete; seqnum is now " +
                            sync.getSequenceNo());
                    syncInitialized = true;
                    // TODO: Announce success via broadcast intent?
                }
            };

    private final OnRegisterSuccess OnDataPrefixRegisterSuccess = new OnRegisterSuccess() {
        @Override
        public void onRegisterSuccess(Name prefix, long registeredPrefixId) {
            Log.d(TAG, "successfully registered data prefix: " + prefix);
        }
    };

    private final OnRegisterFailed OnDataPrefixRegisterFailed = new OnRegisterFailed() {
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
            Blob blob = data.getContent();
            String receivedStr = blob.toString(), dataName = interest.getName().toString();
            Log.d(TAG, "received sync data for " + dataName + ":\n" + receivedStr);
            Intent bcast = new Intent(BCAST_RECEIVED);
            bcast.putExtra(EXTRA_MESSAGE, receivedStr)
                 .putExtra(EXTRA_DATA_NAME, dataName);
            LocalBroadcastManager.getInstance(ChronoSyncService.this).sendBroadcast(bcast);
        }
    };

    public final OnTimeout OnSyncDataInterestTimeout = new OnTimeout() {
        @Override
        public void onTimeout(Interest interest) {
            Name name = interest.getName();
            Log.d(TAG, "timed out waiting for " + name);
            // FIXME? we just try again...
            expressDataInterest(name);
        }
    };
}
