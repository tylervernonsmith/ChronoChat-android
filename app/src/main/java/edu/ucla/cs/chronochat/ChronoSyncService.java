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
import net.named_data.jndn.NetworkNack;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnNetworkNack;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.OnRegisterSuccess;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.encrypt.ConsumerDb;
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


public abstract class ChronoSyncService extends Service {

    private static final String FACE_URI = "localhost",
                                TAG = "ChronoSyncService",
                                BROADCAST_BASE_PREFIX = "/ndn/broadcast";
    private static final double SYNC_LIFETIME = 5000.0; // FIXME?

    /* Intent constants */
    public static final String
            INTENT_PREFIX = "edu.ucla.cs.ChronoChat." + TAG + ".",
            ACTION_SEND = INTENT_PREFIX + "ACTION_SEND",
            BCAST_RECEIVED = INTENT_PREFIX + "BCAST_RECEIVED",
            BCAST_ERROR = INTENT_PREFIX + "BCAST_ERROR",
            EXTRA_MESSAGE = INTENT_PREFIX + "EXTRA_MESSAGE",
            EXTRA_DATA_NAME = INTENT_PREFIX + "EXTRA_DATA_NAME",
            EXTRA_ERROR_CODE = INTENT_PREFIX + "EXTRA_ERROR_CODE";
    protected static final String
            EXTRA_USER_PREFIX_COMPONENT = INTENT_PREFIX + "EXTRA_USER_PREFIX_COMPONENT",
            EXTRA_GROUP_PREFIX_COMPONENT = INTENT_PREFIX + "EXTRA_GROUP_PREFIX_COMPONENT";

    public enum ErrorCode { NFD_PROBLEM, OTHER_EXCEPTION }

    private ErrorCode raisedErrorCode = null;

    protected String userPrefixComponent, groupPrefixComponent;
    private Face face;
    private Name dataPrefix, broadcastPrefix;

    protected ChronoSync2013 sync;
    private boolean networkThreadShouldStop;
    protected boolean syncInitialized = false;
    private KeyChain keyChain;
    protected HashMap<String, Long> nextSeqNumToRequest;
    protected ArrayList<String> sentData;
    protected long registeredDataPrefixId;
    private long session;

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
                raiseError("error during network thread initialization",
                        ErrorCode.OTHER_EXCEPTION, e);
            }
            while (!networkThreadShouldStop) {
                publishSeqNumsIfNeeded();
                try {
                    face.processEvents();
                    Thread.sleep(500); // avoid hammering the CPU
                } catch (IOException e) {
                    raiseError("error in processEvents loop", ErrorCode.NFD_PROBLEM, e);
                } catch (Exception e) {
                    raiseError("error in processEvents loop", ErrorCode.OTHER_EXCEPTION, e);
                }
            }
            broadcastIntentIfErrorRaised();
            Log.d(TAG, "network thread stopped");
        }
    });

    private void raiseError(String logMessage, ErrorCode code, Throwable exception) {
        if (exception == null) Log.e(TAG, logMessage);
        else Log.e(TAG, logMessage, exception);
        raisedErrorCode = code;
        stopNetworkThread();
    }

    private void raiseError(String logMessage, ErrorCode code) {
        raiseError(logMessage, code, null);
    }

    private void initializeService() {
        Log.d(TAG, "initializing service...");
        face = new Face(FACE_URI);
        dataPrefix = new Name(userPrefixComponent + groupPrefixComponent);
        broadcastPrefix = new Name(BROADCAST_BASE_PREFIX + groupPrefixComponent);
        nextSeqNumToRequest = new HashMap<>();
        sentData = new ArrayList<>();
        session = System.currentTimeMillis();
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
                Log.e(TAG, "unable to create default identity", e);
                defaultCertificateName = new Name("/bogus/certificate/name"); // FIXME
            }
        }
        face.setCommandSigningInfo(keyChain, defaultCertificateName);
    }

    private void registerDataPrefix () {
        Log.d(TAG, "registering data prefix...");
        try {
            registeredDataPrefixId = face.registerPrefix(dataPrefix, OnDataInterest,
                    OnDataPrefixRegisterFailed, OnDataPrefixRegisterSuccess);
        } catch (IOException | SecurityException e) {
            Log.d(TAG, "exception registering data prefix"); // will also be handled in callback
            stopNetworkThread(); // just in case
        }

    }

    private void setUpChronoSync() {
        try {
            sync = new ChronoSync2013(OnReceivedChronoSyncState, OnChronoSyncInitialized,
                    dataPrefix, broadcastPrefix, session, face, keyChain,
                    keyChain.getDefaultCertificateName(), SYNC_LIFETIME,
                    OnBroadcastPrefixRegisterFailed);
        } catch (IOException | SecurityException e) {
            Log.d(TAG, "exception setting up ChronoSync"); // will also be handled in callback
            stopNetworkThread(); // just in case
        }
    }

    private void processSyncState(ChronoSync2013.SyncState syncState, boolean isRecovery) {

        long syncSession = syncState.getSessionNo(),
                syncSeqNum = syncState.getSequenceNo();
        String syncDataPrefix = syncState.getDataPrefix(),
                syncDataId = syncDataPrefix + "/" + syncSession;

        Log.d(TAG, "received" + (isRecovery ? " RECOVERY " : " ") + "sync state for " +
                syncState.getDataPrefix() + "/" + syncSession + "/" + syncSeqNum);

        if (syncDataPrefix.toString().equals(this.dataPrefix.toString())) {
            Log.d(TAG, "ignoring sync state for own user");
            return;
        }

        if (isRecovery) {
            nextSeqNumToRequest.put(syncDataId, syncSeqNum); // skip requesting seqnum again
        }
        requestMissingSeqNums(syncDataId, syncSeqNum);

    }

    private void requestMissingSeqNums(String syncDataId, long availableSeqNum) {
        Long seqNumToRequest = nextSeqNumToRequest.get(syncDataId);
        if (seqNumToRequest == null) seqNumToRequest = 0l;
        while (availableSeqNum > seqNumToRequest) {
            String missingDataNameStr = syncDataId + "/" + seqNumToRequest;
            Name missingDataName = new Name(missingDataNameStr);
            Log.d(TAG, "requesting missing seqnum " + seqNumToRequest);
            expressDataInterest(missingDataName);
            seqNumToRequest++;
        }
        nextSeqNumToRequest.put(syncDataId, seqNumToRequest);
    }


    private void expressDataInterest(Name dataName) {
        Log.d(TAG, "expressing interest for " + dataName.toString());
        try {
            face.expressInterest(dataName, OnReceivedSyncData,
                    OnSyncDataInterestTimeout, OnSyncDataInterestNack);
        } catch (IOException e) {
            raiseError("failed to express data interest", ErrorCode.NFD_PROBLEM, e);
        }
    }

    protected void publishSeqNumsIfNeeded() {
        if (!syncInitialized) return;
        while(nextSyncSeqNum() < nextDataSeqNum()) {
            long seqNumToPublish = nextSyncSeqNum();
            try {
                sync.publishNextSequenceNo();
                Log.d(TAG, "published seqnum " + seqNumToPublish);
            } catch (IOException | SecurityException e) {
                raiseError("failed to publish seqnum " + seqNumToPublish, ErrorCode.NFD_PROBLEM, e);
            }
        }
    }

    public int nextDataSeqNum() { return sentData.size(); }
    public long nextSyncSeqNum() { return sync.getSequenceNo(); }

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
        Log.d(TAG, "shutting down/resetting service...");
        syncInitialized = false;
        if (sync != null) sync.shutdown();
        if (face != null) {
            face.removeRegisteredPrefix(registeredDataPrefixId);
            face.shutdown();
        }
        stopNetworkThread();
        Log.d(TAG, "waiting for network thread to finish...");
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
        raisedErrorCode = null;
        Log.d(TAG, "service shutdown complete");
    }

    protected void send(String message) {
        sentData.add(message);
        Log.d(TAG, "sending \"" + message + "\"");
    }

    protected void broadcastIntentIfErrorRaised() {
        if (raisedErrorCode == null) return;

        Log.d(TAG, "broadcasting error intent w/code = " + raisedErrorCode + "...");
        Intent bcast = new Intent(BCAST_ERROR);
        bcast.putExtra(EXTRA_ERROR_CODE, raisedErrorCode);
        LocalBroadcastManager.getInstance(ChronoSyncService.this).sendBroadcast(bcast);
    }


    /***** Callbacks for NDN network thread *****/

    private final OnInterestCallback OnDataInterest = new OnInterestCallback() {
        @Override
        public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                               InterestFilter filterData) {
            Name interestName = interest.getName();
            Log.d(TAG, "data interest received: " + interestName.toString());

            Name.Component seqNumComponent = interestName.get(-1);
            Name.Component sessionComponent = interestName.get(-2);
            int requestedSeqNum = Integer.parseInt(seqNumComponent.toEscapedString());
            long requestedSession = Long.parseLong(sessionComponent.toEscapedString());

            String requestedData = sentData.get(requestedSeqNum);
            if (requestedData != null && session == requestedSession) {
                Log.d(TAG, "responding to data interest");
                Data response = new Data(interestName);
                Blob content = new Blob(requestedData.getBytes());
                response.setContent(content);
                try {
                    face.putData(response);
                } catch (IOException e) {
                    raiseError("failure when responding to data interest",
                            ErrorCode.NFD_PROBLEM, e);
                }
            }
        }
    };

    private final ChronoSync2013.OnReceivedSyncState OnReceivedChronoSyncState =
            new ChronoSync2013.OnReceivedSyncState() {
                @Override
                public void onReceivedSyncState(List syncStates, boolean isRecovery) {
                    Log.d(TAG, "sync states received");
                    for (ChronoSync2013.SyncState syncState :
                            (List<ChronoSync2013.SyncState>) syncStates) {
                        processSyncState(syncState, isRecovery);
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
                    // Ensure that sentData is in sync with the initial seqnum
                    while (nextDataSeqNum() < nextSyncSeqNum()) {
                        sentData.add(null);
                    }
                    syncInitialized = true;
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
            raiseError("failed to register application prefix " + prefix.toString(),
                    ErrorCode.NFD_PROBLEM);
        }
    };

    private final OnRegisterFailed OnBroadcastPrefixRegisterFailed = new OnRegisterFailed() {
        @Override
        public void onRegisterFailed(Name prefix) {
            raiseError("failed to register broadcast prefix " + prefix.toString(),
                    ErrorCode.NFD_PROBLEM);
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
            // FIXME? should we do something other than give up?
        }
    };

    public final OnNetworkNack OnSyncDataInterestNack = new OnNetworkNack() {
        @Override
        public void onNetworkNack(Interest interest, NetworkNack networkNack) {
            Name name = interest.getName();
            Log.d(TAG, "received NACK for " + name);
            // FIXME? should we do something other than give up?
        }
    };
}
