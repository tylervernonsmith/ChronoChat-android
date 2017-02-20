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
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.sync.ChronoSync2013;

import java.io.IOException;
import java.util.List;


public abstract class ChronoSyncService extends Service {

    private static final String FACE_URI = "localhost",
                                TAG = ChronoSyncService.class.getSimpleName();
    private static final long SESSION_NUM = 0; // FIXME?
    private static final double SYNC_LIFETIME = 5000.0; // FIXME?

    /* Intent constants */
    public static final String
            ACTION_SEND = "edu.ucla.cs.ChronoChat.ChronoSyncService.ACTION_SEND";

    private Face face = new Face(FACE_URI);
    private Name dataPrefix = new Name("/test"), broadcastPrefix = new Name("/testbroadcast"); // FIXME
    protected ChronoSync2013 sync;
    private boolean networkThreadShouldStop;
    private KeyChain keyChain;

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
                // TODO: publish seqnum if necessary
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

    private void logSeqNum() {
        Log.d(TAG, "current seqnum " + sync.getSequenceNo());
    }

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

    protected abstract void onDataInterest(Name prefix, Interest interest, Face face,
                                           long interestFilterId, InterestFilter filterData);
    protected abstract void onReceivedChronoSyncState(List syncStates, boolean isRecovery);
    protected abstract void onChronoSyncInitialized();
    protected abstract void onDataPrefixRegisterFailed(Name prefix);
    protected abstract void onBroadcastPrefixRegisterFailed(Name prefix);

    private final OnInterestCallback OnDataInterest = new OnInterestCallback() {
        @Override
        public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                               InterestFilter filterData) {
            onDataInterest(prefix, interest, face, interestFilterId, filterData);
        }
    };

    private final ChronoSync2013.OnReceivedSyncState OnReceivedChronoSyncState =
            new ChronoSync2013.OnReceivedSyncState() {
                @Override
                public void onReceivedSyncState(List syncStates, boolean isRecovery) {
                    onReceivedChronoSyncState(syncStates, isRecovery);
                }
            };

    private final ChronoSync2013.OnInitialized OnChronoSyncInitialized =
            new ChronoSync2013.OnInitialized() {
                @Override
                public void onInitialized() {
                    onChronoSyncInitialized();
                }
            };

    private final OnRegisterFailed OnDataPrefixRegisterFailed =
            new OnRegisterFailed() {
                @Override
                public void onRegisterFailed(Name prefix) {
                    stopNetworkThread();
                    onDataPrefixRegisterFailed(prefix);
                }
            };

    private final OnRegisterFailed OnBroadcastPrefixRegisterFailed = new OnRegisterFailed() {
        @Override
        public void onRegisterFailed(Name prefix) {
            stopNetworkThread();
            onBroadcastPrefixRegisterFailed(prefix);
        }
    };
}
