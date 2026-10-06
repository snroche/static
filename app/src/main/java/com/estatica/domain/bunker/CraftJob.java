package com.estatica.domain.bunker;

import com.estatica.core.Ids;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Something being made in the workshop (GDD §6.5). Progress is in minutes of work at ×1 speed. */
public final class CraftJob {

    private final String id;
    private final String recipeId;
    private double progressMinutes;
    private final long startedAtMillis;

    public CraftJob(String id, String recipeId, double progressMinutes, long startedAtMillis) {
        this.id = Objects.requireNonNull(id);
        this.recipeId = Objects.requireNonNull(recipeId);
        this.progressMinutes = progressMinutes;
        this.startedAtMillis = startedAtMillis;
    }

    public static CraftJob start(String recipeId, long now) {
        return new CraftJob(Ids.newId(), recipeId, 0, now);
    }

    public String getId() {
        return id;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public double getProgressMinutes() {
        return progressMinutes;
    }

    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    /**
     * Works on every job in parallel (one per slot) for {@code hours} at {@code speed}.
     *
     * @param minutesNeeded recipe minutes per job, same order as {@code jobs}
     * @return the jobs that finished
     */
    public static List<CraftJob> work(List<CraftJob> jobs, List<Double> minutesNeeded, double hours, double speed) {
        List<CraftJob> done = new ArrayList<>();
        for (int i = 0; i < jobs.size(); i++) {
            CraftJob job = jobs.get(i);
            job.progressMinutes = Math.min(minutesNeeded.get(i), job.progressMinutes + hours * 60 * speed);
            if (job.progressMinutes >= minutesNeeded.get(i)) {
                done.add(job);
            }
        }
        return done;
    }

    /** Minutes of real time left at {@code speed}, or -1 if paused. */
    public double minutesLeft(double minutesNeeded, double speed) {
        if (speed <= 0) {
            return -1;
        }
        return Math.max(0, minutesNeeded - progressMinutes) / speed;
    }
}
