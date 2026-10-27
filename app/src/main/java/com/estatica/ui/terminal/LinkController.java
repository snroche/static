package com.estatica.ui.terminal;

import android.os.Handler;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.estatica.data.link.NearbyLink;
import com.estatica.data.session.GameSession;
import com.estatica.domain.log.LogLine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * RADIO → LINK → NEARBY: a Bluetooth link with another bunker in the same room, and what the
 * two operators do over it. Main thread only; game work goes through the game executor.
 *
 * <p>Trades: each side builds an offer (a drop code, nothing taken yet) and sends it on every
 * change. Agreeing sends both offers as this side sees them; when each side has agreed to the
 * same pair, each phone hands over its own half. Joint runs: each side picks a place in its own
 * world and someone from the crew; once both have, each gets the other's wanderer waiting there.
 */
final class LinkController implements NearbyLink.Listener {

    /** Game work whose log lines go to the terminal. */
    interface Steps {
        void run(Supplier<List<LogLine>> work);
    }

    /** What the NEARBY screen shows. Replaced (not changed) on every update. */
    static final class View {
        NearbyLink.State state = NearbyLink.State.OFF;
        /** "LU4QXR (Santi)", once they said hello. */
        String peer;
        /** Found while searching: {address, name}. */
        List<String[]> devices = List.of();
        Map<String, Integer> goods = Map.of();
        Set<String> gear = Set.of();
        /** Our offer can't go as it is (more than the stores hold, or over the limits). */
        boolean offerInvalid;
        /** Their offer, described, or null until it comes. */
        String theirOffer;
        boolean myReady;
        boolean theirReady;
        /** Our joint run is set (waiting for theirs). */
        boolean jointSent;
        boolean theirJoint;
        /** The link dropped. */
        boolean failed;
    }

    private final GameSession session;
    private final NearbyLink nearby;
    private final ExecutorService game;
    private final Handler main;
    private final Steps steps;
    private final MutableLiveData<View> view = new MutableLiveData<>(new View());

    private final List<String[]> devices = new ArrayList<>();
    private final Map<String, Integer> goods = new LinkedHashMap<>();
    private final Set<String> gear = new LinkedHashSet<>();
    private String peer;
    private String myOffer;
    private String theirOffer;
    private String theirOfferText;
    private boolean offerInvalid;
    private boolean myReady;
    private boolean theirReady;
    private String myJoint;
    private String jointZone;
    private String theirJoint;
    private boolean failed;

    LinkController(GameSession session, NearbyLink nearby, ExecutorService game, Handler main, Steps steps) {
        this.session = session;
        this.nearby = nearby;
        this.game = game;
        this.main = main;
        this.steps = steps;
        nearby.setListener(this);
    }

    LiveData<View> view() {
        return view;
    }

    boolean available() {
        return nearby.available();
    }

    boolean enabled() {
        return nearby.enabled();
    }

    private void publish() {
        View v = new View();
        v.state = nearby.state();
        v.peer = peer;
        v.devices = new ArrayList<>(devices);
        v.goods = new LinkedHashMap<>(goods);
        v.gear = new LinkedHashSet<>(gear);
        v.offerInvalid = offerInvalid;
        v.theirOffer = theirOfferText;
        v.myReady = myReady;
        v.theirReady = theirReady;
        v.jointSent = myJoint != null;
        v.theirJoint = theirJoint != null;
        v.failed = failed;
        view.setValue(v);
    }

    private void reset() {
        devices.clear();
        goods.clear();
        gear.clear();
        peer = null;
        myOffer = null;
        theirOffer = null;
        theirOfferText = null;
        offerInvalid = false;
        myReady = false;
        theirReady = false;
        myJoint = null;
        jointZone = null;
        theirJoint = null;
    }

    void host() {
        reset();
        failed = false;
        nearby.host();
        publish();
    }

    void search() {
        reset();
        failed = false;
        nearby.search();
        publish();
    }

    void connect(String address) {
        failed = false;
        nearby.connect(address);
        publish();
    }

    void close() {
        if (nearby.state() == NearbyLink.State.CONNECTED) {
            nearby.send(NearbyLink.Frame.of(NearbyLink.Frame.BYE));
        }
        nearby.close();
        reset();
        publish();
    }

    /** Hands a code over the link (a message, drop, wanderer…). */
    void sendCode(String code) {
        NearbyLink.Frame f = NearbyLink.Frame.of(NearbyLink.Frame.CODE);
        f.code = code;
        nearby.send(f);
    }

    // ---- Trade ----

    void changeGood(String id, int delta, int have) {
        int n = Math.max(0, Math.min(have, goods.getOrDefault(id, 0) + delta));
        if (n == 0) {
            goods.remove(id);
        } else {
            goods.put(id, n);
        }
        offerChanged();
    }

    void toggleGear(String pieceId) {
        if (!gear.remove(pieceId)) {
            gear.add(pieceId);
        }
        offerChanged();
    }

    private void offerChanged() {
        myReady = false;
        theirReady = false;
        Map<String, Integer> g = new LinkedHashMap<>(goods);
        List<String> p = new ArrayList<>(gear);
        publish();
        game.execute(() -> {
            String code = session.linkOffer(g, p);
            main.post(() -> {
                // A newer change may have come meanwhile: only the latest counts.
                if (!g.equals(goods) || !p.equals(new ArrayList<>(gear))) {
                    return;
                }
                offerInvalid = code == null;
                myOffer = code;
                if (code != null) {
                    NearbyLink.Frame f = NearbyLink.Frame.of(NearbyLink.Frame.OFFER);
                    f.code = code;
                    nearby.send(f);
                }
                publish();
            });
        });
    }

    void agree() {
        if (myOffer == null || theirOffer == null) {
            return;
        }
        myReady = true;
        NearbyLink.Frame f = NearbyLink.Frame.of(NearbyLink.Frame.READY);
        f.ready = true;
        f.mine = myOffer;
        f.theirs = theirOffer;
        nearby.send(f);
        publish();
        maybeTrade();
    }

    private void maybeTrade() {
        if (!myReady || !theirReady) {
            return;
        }
        Map<String, Integer> g = new LinkedHashMap<>(goods);
        List<String> p = new ArrayList<>(gear);
        String theirs = theirOffer;
        steps.run(() -> session.linkTrade(g, p, theirs));
        goods.clear();
        gear.clear();
        theirOffer = null;
        theirOfferText = null;
        offerChanged();
    }

    // ---- Joint run ----

    void joint(String zoneId, String scoutId) {
        game.execute(() -> {
            String code = session.linkWandererCode(scoutId);
            main.post(() -> {
                if (code == null) {
                    return;
                }
                myJoint = code;
                jointZone = zoneId;
                NearbyLink.Frame f = NearbyLink.Frame.of(NearbyLink.Frame.JOINT);
                f.code = code;
                nearby.send(f);
                publish();
                maybeJoint();
            });
        });
    }

    private void maybeJoint() {
        if (myJoint == null || theirJoint == null) {
            return;
        }
        String zone = jointZone;
        String theirs = theirJoint;
        steps.run(() -> session.linkJoint(zone, theirs));
        myJoint = null;
        jointZone = null;
        theirJoint = null;
        publish();
    }

    // ---- NearbyLink.Listener (main thread) ----

    @Override
    public void onState(NearbyLink.State state) {
        if (state == NearbyLink.State.CONNECTED) {
            devices.clear();
            game.execute(() -> {
                NearbyLink.Frame hello = NearbyLink.Frame.of(NearbyLink.Frame.HELLO);
                hello.callsign = session.callsign();
                hello.name = session.operatorName();
                main.post(() -> {
                    nearby.send(hello);
                    offerChanged();
                });
            });
        }
        publish();
    }

    @Override
    public void onDevice(String address, String name) {
        for (String[] d : devices) {
            if (d[0].equals(address)) {
                return;
            }
        }
        devices.add(new String[]{address, name});
        publish();
    }

    @Override
    public void onFrame(NearbyLink.Frame f) {
        switch (f.kind) {
            case NearbyLink.Frame.HELLO:
                peer = (f.callsign != null ? f.callsign : "?") + (f.name != null ? " (" + f.name + ")" : "");
                break;
            case NearbyLink.Frame.CODE:
                if (f.code != null) {
                    String code = f.code;
                    steps.run(() -> session.linkReceiveNearby(code));
                }
                break;
            case NearbyLink.Frame.OFFER: {
                theirOffer = f.code;
                myReady = false;
                theirReady = false;
                String code = f.code;
                game.execute(() -> {
                    String text = session.linkDescribeOffer(code);
                    main.post(() -> {
                        if (Objects.equals(code, theirOffer)) {
                            theirOfferText = text;
                            publish();
                        }
                    });
                });
                break;
            }
            case NearbyLink.Frame.READY:
                theirReady = f.ready && Objects.equals(f.mine, theirOffer) && Objects.equals(f.theirs, myOffer);
                maybeTrade();
                break;
            case NearbyLink.Frame.JOINT:
                theirJoint = f.code;
                maybeJoint();
                break;
            case NearbyLink.Frame.BYE:
                nearby.close();
                reset();
                break;
            default:
                break;
        }
        publish();
    }

    @Override
    public void onFailed() {
        reset();
        failed = true;
        publish();
    }

    void release() {
        nearby.setListener(null);
        close();
    }
}
