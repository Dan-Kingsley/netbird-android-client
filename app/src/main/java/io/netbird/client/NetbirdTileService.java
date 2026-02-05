package io.netbird.client;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.IBinder;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import io.netbird.client.tool.ServiceStateListener;
import io.netbird.client.tool.VPNService;

public class NetbirdTileService extends TileService {

    private static final String TAG = "NetbirdTileService";
    private VPNService.MyLocalBinder mBinder;
    private boolean isBound = false;
    private boolean pendingClick = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mBinder = (VPNService.MyLocalBinder) binder;
            isBound = true;
            mBinder.addServiceStateListener(serviceStateListener);
            updateTile();

            if (pendingClick) {
                pendingClick = false;
                handleToggle();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBinder = null;
            isBound = false;
            updateTile();
        }
    };

    private final ServiceStateListener serviceStateListener = new ServiceStateListener() {
        @Override
        public void onStarted() {
            updateTile();
        }

        @Override
        public void onStopped() {
            updateTile();
        }

        @Override
        public void onError(String msg) {
            updateTile();
        }
    };

    @Override
    public void onStartListening() {
        super.onStartListening();
        Log.d(TAG, "onStartListening");
        bindToVpnService();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        Log.d(TAG, "onStopListening");
        unbindFromVpnService();
    }

    @Override
    public void onClick() {
        super.onClick();
        Log.d(TAG, "onClick");

        if (VpnService.prepare(this) != null) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(intent);
            return;
        }

        handleToggle();
    }

    private void handleToggle() {
        if (mBinder != null) {
            if (mBinder.isRunning()) {
                mBinder.stopEngine();
            } else {
                mBinder.runEngine(null, false);
            }
        } else {
            pendingClick = true;
            startAndRunVpnService();
        }
    }

    private void bindToVpnService() {
        Intent intent = new Intent(this, VPNService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindFromVpnService() {
        if (isBound) {
            if (mBinder != null) {
                mBinder.removeServiceStateListener(serviceStateListener);
            }
            unbindService(serviceConnection);
            isBound = false;
            mBinder = null;
        }
    }

    private void startAndRunVpnService() {
        Intent intent = new Intent(this, VPNService.class);
        intent.setAction(VPNService.INTENT_ACTION_START);
        startService(intent);
        bindToVpnService();
    }

    private boolean isVpnRunning() {
        return mBinder != null && isBound && mBinder.isRunning();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        boolean running = isVpnRunning();

        tile.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
