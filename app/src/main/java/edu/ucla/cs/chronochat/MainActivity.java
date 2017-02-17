package edu.ucla.cs.chronochat;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

//    NDNService service;
//    boolean serviceBound = false;
//
//    private ServiceConnection serviceConn = new ServiceConnection() {
//        @Override
//        public void onServiceConnected(ComponentName name, IBinder binder) {
//            NDNService.NDNBinder ndnBinder = (NDNService.NDNBinder) binder;
//            service = ndnBinder.getService();
//            serviceBound = true;
//        }
//
//        @Override
//        public void onServiceDisconnected(ComponentName name) {
//            serviceBound = false;
//        }
//    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();
//        // Bind to NDNService
//        Intent intent = new Intent(this, NDNService.class);
//        bindService(intent, serviceConn, Context.BIND_AUTO_CREATE);
        Intent intent = new Intent(this, PingService.class);
        this.startService(intent);
    }
//
//    @Override
//    protected void onStop() {
//        super.onStop();
//        if (serviceBound) {
//            unbindService(serviceConn);
//            serviceBound = false;
//        }
//    }
}
