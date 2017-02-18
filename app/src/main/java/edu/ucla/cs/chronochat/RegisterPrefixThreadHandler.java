package edu.ucla.cs.chronochat;

import android.util.Log;

import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;

import java.io.IOException;


public class RegisterPrefixThreadHandler
        extends NDNThreadHandler
        implements OnInterestCallback, OnRegisterFailed {

    private static final String TAG = RegisterPrefixThreadHandler.class.getSimpleName();
    private final Name prefix;
    private KeyChain keyChain;

    public RegisterPrefixThreadHandler(Face face, Name prefix) {
        super(face);
        this.prefix = prefix;
    }

    @Override
    protected void onThreadStart() {
        initializeKeyChain();
        setCommandSigningInfo();
        registerPrefix();
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

    private void registerPrefix() {
        try {
            face.registerPrefix(prefix, this, this);
            Log.d(TAG, "prefix registered: " + prefix.toString());
        } catch (IOException | SecurityException e) {
            Log.d(TAG, "exception when registering prefix: " + e.getMessage());
            e.printStackTrace();
        }
    }


    @Override
    public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId,
                           InterestFilter filterData) {
        // NOTE: this thread continues and should be explicitly stopped by the NDNService!
        Log.d(TAG, "prefix interest recv'd");
        // TODO: Broadcast result
    }

    @Override
    public void onRegisterFailed(Name _) {
        stop();
        Log.d(TAG, "prefix registration failed");
        // TODO: Broadcast result
    }
}
