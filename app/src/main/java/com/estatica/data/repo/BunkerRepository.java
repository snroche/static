package com.estatica.data.repo;

import androidx.annotation.WorkerThread;

import com.estatica.data.db.AffinityDao;
import com.estatica.data.db.AffinityEntity;
import com.estatica.data.db.ConversationDao;
import com.estatica.data.db.ConversationEntity;
import com.estatica.data.db.ItemDao;
import com.estatica.data.db.GearEntity;
import com.estatica.data.db.ItemEntity;
import com.estatica.domain.bunker.GearPiece;
import com.estatica.domain.bunker.ItemInstance;
import com.estatica.domain.dialogue.Conversation;
import com.estatica.domain.scouts.Affinities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Persistence for life in the bunker: conversations, personal objects, affinity between
 * scouts. Blocking: call from the game executor, inside a transaction when saving.
 */
@Singleton
public final class BunkerRepository {

    private final ConversationDao conversations;
    private final ItemDao items;
    private final AffinityDao affinity;

    @Inject
    public BunkerRepository(ConversationDao conversations, ItemDao items, AffinityDao affinity) {
        this.conversations = conversations;
        this.items = items;
        this.affinity = affinity;
    }

    @WorkerThread
    public List<Conversation> pendingConversations() {
        List<Conversation> out = new ArrayList<>();
        for (ConversationEntity e : conversations.pending()) {
            out.add(new Conversation(e.id, e.scoutId, e.dialogueId, e.startedAtMillis, e.initiated,
                    Conversation.Status.valueOf(e.status)));
        }
        return out;
    }

    @WorkerThread
    public void save(Conversation c) {
        ConversationEntity e = new ConversationEntity();
        e.id = c.getId();
        e.scoutId = c.getScoutId();
        e.dialogueId = c.getDialogueId();
        e.startedAtMillis = c.getStartedAtMillis();
        e.initiated = c.isInitiated();
        e.status = c.getStatus().name();
        conversations.upsert(e);
    }

    @WorkerThread
    public List<ItemInstance> items() {
        List<ItemInstance> out = new ArrayList<>();
        for (ItemEntity e : items.all()) {
            out.add(new ItemInstance(e.id, e.itemId, e.location, e.originalOwnerId, e.acquiredAtMillis));
        }
        return out;
    }

    @WorkerThread
    public void saveItems(Collection<ItemInstance> instances) {
        if (instances.isEmpty()) {
            return;
        }
        List<ItemEntity> entities = new ArrayList<>(instances.size());
        for (ItemInstance i : instances) {
            ItemEntity e = new ItemEntity();
            e.id = i.getId();
            e.itemId = i.getItemId();
            e.location = i.getLocation();
            e.originalOwnerId = i.getOriginalOwnerId();
            e.acquiredAtMillis = i.getAcquiredAtMillis();
            entities.add(e);
        }
        items.upsert(entities);
    }

    @WorkerThread
    public List<GearPiece> gear() {
        List<GearPiece> out = new ArrayList<>();
        for (GearEntity e : items.allGear()) {
            out.add(new GearPiece(e.id, e.gearId, GearPiece.Rarity.fromKey(e.rarity), e.condition, e.location,
                    e.originalOwnerId));
        }
        return out;
    }

    /** Upserts every piece; used-up ones are deleted. */
    @WorkerThread
    public void saveGear(Collection<GearPiece> pieces) {
        List<GearEntity> keep = new ArrayList<>();
        List<String> gone = new ArrayList<>();
        for (GearPiece p : pieces) {
            if (p.isGone()) {
                gone.add(p.getId());
                continue;
            }
            GearEntity e = new GearEntity();
            e.id = p.getId();
            e.gearId = p.getGearId();
            e.rarity = p.getRarity().name();
            e.condition = p.getCondition();
            e.location = p.getLocation();
            e.originalOwnerId = p.getOriginalOwnerId();
            keep.add(e);
        }
        if (!keep.isEmpty()) {
            items.upsertGear(keep);
        }
        if (!gone.isEmpty()) {
            items.deleteGear(gone);
        }
    }

    @WorkerThread
    public Affinities affinities(double max) {
        Affinities out = new Affinities(max);
        for (AffinityEntity e : affinity.all()) {
            out.put(e.scoutA, e.scoutB, e.value);
        }
        return out;
    }

    @WorkerThread
    public void saveAffinities(Affinities values) {
        List<AffinityEntity> entities = new ArrayList<>();
        for (Map.Entry<String, Double> entry : values.asMap().entrySet()) {
            String[] pair = entry.getKey().split("\\|", 2);
            AffinityEntity e = new AffinityEntity();
            e.scoutA = pair[0];
            e.scoutB = pair[1];
            e.value = entry.getValue();
            entities.add(e);
        }
        if (!entities.isEmpty()) {
            affinity.upsert(entities);
        }
    }
}
