package community.speaker.client;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "local_reminders";
    private static final String FIRE = "community.speaker.client.DELIVER_REMINDER";

    static final class Entry {
        final String id;
        final long due;
        final String message;

        Entry(String id, long due, String message) {
            this.id = id;
            this.due = due;
            this.message = message;
        }
    }

    static boolean notificationsAvailable(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "本機提醒", NotificationManager.IMPORTANCE_DEFAULT));
            return manager.getNotificationChannel(CHANNEL).getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        return true;
    }

    static Entry add(Context context, ReminderRequest request) {
        Entry entry = new Entry(UUID.randomUUID().toString(),
                System.currentTimeMillis() + request.seconds * 1000L, request.message);
        if (!store(context).edit().putString(entry.id, entry.due + "\n" + entry.message).commit()) return null;
        schedule(context, entry);
        return entry;
    }

    static boolean cancel(Context context, Entry entry) {
        if (!store(context).edit().remove(entry.id).commit()) return false;
        PendingIntent alarm = pending(context, entry.id, PendingIntent.FLAG_NO_CREATE);
        if (alarm != null) {
            context.getSystemService(AlarmManager.class).cancel(alarm);
            alarm.cancel();
        }
        return true;
    }

    static List<Entry> list(Context context) {
        List<Entry> entries = new ArrayList<>();
        SharedPreferences preferences = store(context);
        for (String id : preferences.getAll().keySet()) {
            Entry entry = decode(id, preferences.getString(id, null));
            if (entry != null) entries.add(entry);
        }
        entries.sort(Comparator.comparingLong(entry -> entry.due));
        return entries;
    }

    static void restore(Context context) {
        for (Entry entry : list(context)) {
            if (entry.due <= System.currentTimeMillis()) {
                deliver(context, entry);
            } else if (pending(context, entry.id, PendingIntent.FLAG_NO_CREATE) == null) {
                schedule(context, entry);
            }
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            restore(context);
        } else if (FIRE.equals(intent.getAction()) && intent.getData() != null) {
            String id = intent.getData().getLastPathSegment();
            Entry entry = decode(id, store(context).getString(id, null));
            if (entry != null && entry.due <= System.currentTimeMillis()) deliver(context, entry);
        }
    }

    private static void schedule(Context context, Entry entry) {
        context.getSystemService(AlarmManager.class).setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, entry.due, pending(context, entry.id, 0));
    }

    private static PendingIntent pending(Context context, String id, int flags) {
        Intent intent = new Intent(context, ReminderReceiver.class).setAction(FIRE)
                .setData(new Uri.Builder().scheme("speaker-reminder").authority("local").appendPath(id).build());
        return PendingIntent.getBroadcast(context, 0, intent,
                flags | PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
    }

    @SuppressWarnings("deprecation")
    private static void deliver(Context context, Entry entry) {
        if (!notificationsAvailable(context)) return;
        PendingIntent open = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("愛狐說提醒")
                .setContentText(entry.message)
                .setStyle(new Notification.BigTextStyle().bigText(entry.message))
                .setContentIntent(open)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT < 26) builder.setDefaults(Notification.DEFAULT_ALL);
        try {
            context.getSystemService(NotificationManager.class).notify(entry.id, 0, builder.build());
            cancel(context, entry);
        } catch (SecurityException ignored) {
            // Keep the reminder if notification access is revoked during delivery.
        }
    }

    private static Entry decode(String id, String saved) {
        if (saved == null) return null;
        int separator = saved.indexOf('\n');
        if (separator < 0) return null;
        try {
            return new Entry(id, Long.parseLong(saved.substring(0, separator)), saved.substring(separator + 1));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static SharedPreferences store(Context context) {
        return context.getSharedPreferences("local_reminders", Context.MODE_PRIVATE);
    }
}
