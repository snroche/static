package com.estatica.data.repo;

import androidx.annotation.WorkerThread;

import com.estatica.data.db.LogDao;
import com.estatica.data.db.LogLineEntity;
import com.estatica.domain.log.LogChannel;
import com.estatica.domain.log.LogLine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Radio log persistence. All methods block: call them from the game executor. */
@Singleton
public final class LogRepository {

    private final LogDao dao;

    @Inject
    public LogRepository(LogDao dao) {
        this.dao = dao;
    }

    /** Persists drafts and returns them with their assigned seq. */
    @WorkerThread
    public List<LogLine> insert(List<LogLine> drafts) {
        List<LogLineEntity> entities = new ArrayList<>(drafts.size());
        for (LogLine line : drafts) {
            entities.add(toEntity(line));
        }
        List<Long> seqs = dao.insertAll(entities);
        List<LogLine> saved = new ArrayList<>(drafts.size());
        for (int i = 0; i < drafts.size(); i++) {
            saved.add(drafts.get(i).withSeq(seqs.get(i)));
        }
        return saved;
    }

    /** The newest {@code limit} lines, oldest first. */
    @WorkerThread
    public List<LogLine> latest(int limit) {
        return toAscending(dao.latest(limit));
    }

    /** Up to {@code limit} lines older than {@code beforeSeq}, oldest first. */
    @WorkerThread
    public List<LogLine> before(long beforeSeq, int limit) {
        return toAscending(dao.before(beforeSeq, limit));
    }

    private static List<LogLine> toAscending(List<LogLineEntity> newestFirst) {
        List<LogLine> lines = new ArrayList<>(newestFirst.size());
        for (LogLineEntity e : newestFirst) {
            lines.add(new LogLine(e.seq, e.uuid, e.timestampMillis,
                    LogChannel.fromName(e.channel), e.speaker, e.text, e.scoutId, e.thread));
        }
        Collections.reverse(lines);
        return lines;
    }

    private static LogLineEntity toEntity(LogLine line) {
        LogLineEntity e = new LogLineEntity();
        e.uuid = line.getId();
        e.timestampMillis = line.getTimestampMillis();
        e.channel = line.getChannel().name();
        e.speaker = line.getSpeaker();
        e.text = line.getText();
        e.scoutId = line.getScoutId();
        e.thread = line.getThread();
        return e;
    }
}
