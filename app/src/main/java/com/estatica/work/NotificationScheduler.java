package com.estatica.work;

import android.content.Context;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.estatica.data.session.GameSession;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Schedules notifications while the app is closed and cancels them when it opens.
 * Only situations and returns are notified (GDD §9: no "we miss you").
 */
@Singleton
public final class NotificationScheduler {

    private static final String TAG = "estatica-notice";

    private final Context context;

    @Inject
    public NotificationScheduler(@ApplicationContext Context context) {
        this.context = context;
    }

    public void schedule(List<GameSession.ScheduledNotice> notices, long nowMillis) {
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.cancelAllWorkByTag(TAG);
        for (GameSession.ScheduledNotice notice : notices) {
            Data data = new Data.Builder()
                    .putString(NotifyWorker.KEY_ID, notice.key)
                    .putString(NotifyWorker.KEY_TITLE, notice.title)
                    .putString(NotifyWorker.KEY_TEXT, notice.text)
                    .build();
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(NotifyWorker.class)
                    .setInitialDelay(Math.max(0, notice.atMillis - nowMillis), TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .addTag(TAG)
                    .build();
            workManager.enqueue(request);
        }
    }

    /** The player is looking at the terminal: pending notices are stale. */
    public void cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG);
    }
}
