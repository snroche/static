package com.estatica.work;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.estatica.R;
import com.estatica.ui.terminal.TerminalActivity;

/**
 * Posts one precomputed notification. Deliberately knows nothing about the game: the text
 * was decided when the app went to background, so no simulation happens here.
 */
public final class NotifyWorker extends Worker {

    static final String KEY_TITLE = "title";
    static final String KEY_TEXT = "text";
    static final String KEY_ID = "id";
    static final String CHANNEL_ID = "transmissions";

    public NotifyWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return Result.success();
        }
        ensureChannel(context);

        Intent open = new Intent(context, TerminalActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String id = getInputData().getString(KEY_ID);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_radio)
                .setContentTitle(getInputData().getString(KEY_TITLE))
                .setContentText(getInputData().getString(KEY_TEXT))
                .setColor(ContextCompat.getColor(context, R.color.amber))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE);
        //noinspection MissingPermission: checked above
        NotificationManagerCompat.from(context).notify(id != null ? id.hashCode() : 0, builder.build());
        return Result.success();
    }

    static void ensureChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(context.getString(R.string.notification_channel_description));
            manager.createNotificationChannel(channel);
        }
    }
}
