package com.estatica.data.link;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Two bunkers in the same room, over Bluetooth (GDD §12): one hosts, the other finds it and
 * connects. Plain RFCOMM with an insecure socket, so no pairing is needed. Frames are small
 * JSON objects; what they mean is up to the caller (codes, trade offers, joint runs).
 *
 * <p>Sockets block, so they run on their own threads, never the game executor; every callback
 * comes back on the main thread. The Activity asks for the runtime permissions first, hence the
 * suppressed lint check.
 */
@Singleton
@SuppressLint("MissingPermission")
public final class NearbyLink {

    public enum State { OFF, HOSTING, SEARCHING, CONNECTING, CONNECTED }

    /** One message between bunkers. Only the fields its kind needs are set. */
    public static final class Frame {
        public static final String HELLO = "HELLO";
        public static final String CODE = "CODE";
        public static final String OFFER = "OFFER";
        public static final String READY = "READY";
        public static final String JOINT = "JOINT";
        public static final String BYE = "BYE";

        public String kind;
        public String callsign;
        public String name;
        /** A link code: a packet, an offer or a wanderer. */
        public String code;
        /** READY: the two offers agreed on, as the sender sees them. */
        public String mine;
        public String theirs;
        public boolean ready;
        public int protocol = PROTOCOL;

        public static Frame of(String kind) {
            Frame f = new Frame();
            f.kind = kind;
            return f;
        }
    }

    public interface Listener {
        void onState(State state);

        /** A bunker found nearby while searching. */
        void onDevice(String address, String name);

        void onFrame(Frame frame);

        /** Something went wrong: the link is closed. */
        void onFailed();
    }

    static final int PROTOCOL = 1;
    private static final UUID SERVICE = UUID.fromString("6f3c2a1e-5b7d-4e0a-9c41-e57a71ca0801");
    private static final String SERVICE_NAME = "Estatica link";
    private static final int MAX_FRAME = 16 * 1024;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    /** Writes in order, off the main thread. */
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private Listener listener;
    private State state = State.OFF;
    private BluetoothServerSocket server;
    private BluetoothSocket socket;
    private DataOutputStream out;
    private boolean receiverOn;

    @Inject
    NearbyLink(@ApplicationContext Context context) {
        this.context = context;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public State state() {
        return state;
    }

    private BluetoothAdapter adapter() {
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager != null ? manager.getAdapter() : null;
    }

    /** False when the phone has no Bluetooth at all. */
    public boolean available() {
        return adapter() != null;
    }

    public boolean enabled() {
        BluetoothAdapter a = adapter();
        return a != null && a.isEnabled();
    }

    private void setState(State s) {
        state = s;
        main.post(() -> {
            if (listener != null) {
                listener.onState(s);
            }
        });
    }

    private void failed() {
        close();
        main.post(() -> {
            if (listener != null) {
                listener.onFailed();
            }
        });
    }

    /** Waits for the other bunker to connect. The Activity makes the phone discoverable. */
    public void host() {
        close();
        BluetoothAdapter a = adapter();
        if (a == null) {
            failed();
            return;
        }
        try {
            server = a.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, SERVICE);
        } catch (IOException | SecurityException e) {
            failed();
            return;
        }
        setState(State.HOSTING);
        BluetoothServerSocket listening = server;
        new Thread(() -> {
            try {
                BluetoothSocket s = listening.accept();
                listening.close();
                connected(s);
            } catch (IOException | SecurityException e) {
                // Closed on purpose (cancel), or the radio went off.
                if (state == State.HOSTING) {
                    failed();
                }
            }
        }, "link-host").start();
    }

    /** Looks for bunkers nearby: paired phones first, then whatever discovery turns up. */
    public void search() {
        close();
        BluetoothAdapter a = adapter();
        if (a == null) {
            failed();
            return;
        }
        setState(State.SEARCHING);
        try {
            for (BluetoothDevice d : a.getBondedDevices()) {
                found(d);
            }
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(discovery, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(discovery, filter);
            }
            receiverOn = true;
            a.startDiscovery();
        } catch (SecurityException e) {
            failed();
        }
    }

    private final BroadcastReceiver discovery = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (d != null) {
                found(d);
            }
        }
    };

    private void found(BluetoothDevice d) {
        String name;
        try {
            name = d.getName();
        } catch (SecurityException e) {
            name = null;
        }
        String address = d.getAddress();
        String shown = name != null ? name : address;
        main.post(() -> {
            if (listener != null && state == State.SEARCHING) {
                listener.onDevice(address, shown);
            }
        });
    }

    public void connect(String address) {
        BluetoothAdapter a = adapter();
        if (a == null) {
            failed();
            return;
        }
        stopDiscovery();
        setState(State.CONNECTING);
        new Thread(() -> {
            try {
                a.cancelDiscovery();
                BluetoothSocket s = a.getRemoteDevice(address).createInsecureRfcommSocketToServiceRecord(SERVICE);
                s.connect();
                connected(s);
            } catch (IOException | SecurityException | IllegalArgumentException e) {
                failed();
            }
        }, "link-connect").start();
    }

    private void connected(BluetoothSocket s) throws IOException {
        socket = s;
        out = new DataOutputStream(s.getOutputStream());
        DataInputStream in = new DataInputStream(s.getInputStream());
        setState(State.CONNECTED);
        new Thread(() -> read(s, in), "link-read").start();
    }

    private void read(BluetoothSocket s, DataInputStream in) {
        try {
            while (true) {
                int length = in.readInt();
                if (length <= 0 || length > MAX_FRAME) {
                    throw new IOException("bad frame");
                }
                byte[] data = new byte[length];
                in.readFully(data);
                Frame frame = gson.fromJson(new String(data, java.nio.charset.StandardCharsets.UTF_8), Frame.class);
                if (frame == null || frame.kind == null) {
                    continue;
                }
                main.post(() -> {
                    if (listener != null && socket == s) {
                        listener.onFrame(frame);
                    }
                });
                if (Frame.BYE.equals(frame.kind)) {
                    break;
                }
            }
        } catch (IOException | RuntimeException e) {
            // The other side went away.
        }
        if (socket == s) {
            close();
        }
    }

    public void send(Frame frame) {
        DataOutputStream stream = out;
        if (stream == null) {
            return;
        }
        byte[] data = gson.toJson(frame).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writer.execute(() -> {
            try {
                synchronized (stream) {
                    stream.writeInt(data.length);
                    stream.write(data);
                    stream.flush();
                }
            } catch (IOException e) {
                failed();
            }
        });
    }

    private void stopDiscovery() {
        if (receiverOn) {
            try {
                context.unregisterReceiver(discovery);
            } catch (IllegalArgumentException ignored) {
                // Already gone.
            }
            receiverOn = false;
        }
        BluetoothAdapter a = adapter();
        try {
            if (a != null) {
                a.cancelDiscovery();
            }
        } catch (SecurityException ignored) {
            // Permission revoked: nothing to cancel.
        }
    }

    /** Hangs up, stops hosting or searching. */
    public void close() {
        stopDiscovery();
        State was = state;
        state = State.OFF;
        try {
            if (server != null) {
                server.close();
            }
        } catch (IOException ignored) {
            // Closing anyway.
        }
        server = null;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // Closing anyway.
        }
        socket = null;
        out = null;
        if (was != State.OFF) {
            setState(State.OFF);
        }
    }
}
