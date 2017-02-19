package edu.ucla.cs.chronochat;

import android.util.Log;

import net.named_data.jndn.Data;
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
import net.named_data.jndn.util.Blob;

import java.io.IOException;


public class RegisterPrefixThreadHandler
        extends NDNThreadHandler
        implements OnInterestCallback, OnRegisterFailed {

    private static final String TAG = RegisterPrefixThreadHandler.class.getSimpleName();
    private KeyChain keyChain;

    public RegisterPrefixThreadHandler(NDNService service) {
        super(service);
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
        keyChain.setFace(service.getFace());
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
        service.getFace().setCommandSigningInfo(keyChain, defaultCertificateName);
    }

    private void registerPrefix() {
        try {
            final Name prefix = service.getPrefix();
            service.getFace().registerPrefix(prefix, this, this);
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
        Log.d(TAG, "prefix interest received");

//        Name interestName = interest.getName();
//        String lastComp = interestName.get(interestName.size() - 1).toEscapedString();
//        Log.d(TAG, "prefix interest received: " + lastComp);
//        int comp = Integer.parseInt(lastComp) - 1;
//
//        Data data = new Data();
//        data.setName(new Name(interestName));
//        Blob blob;
//        if (ndnActivity.dataHistory.size() > comp) {
//            blob = new Blob(ndnActivity.dataHistory.get(comp).getBytes());
//            data.setContent(blob);
//            try {
//                face.putData(data);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
        // TODO: Broadcast result
    }

    @Override
    public void onRegisterFailed(Name _) {
        stop();
        Log.d(TAG, "prefix registration failed");
        // TODO: Broadcast result
    }
}
