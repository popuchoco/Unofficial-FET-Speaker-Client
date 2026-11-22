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
import java.util.TimeZone;
import java.util.UUID;

public final class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "local_reminders";
    private static final String FIRE = "community.speaker.client.DELIVER_REMINDER";

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

    static ReminderEntry add(Context context, ReminderRequest request) {
        long due = request.dueAtMillis > 0 ? request.dueAtMillis
                : System.currentTimeMillis() + request.seconds * 1000L;
        ReminderEntry entry = new ReminderEntry(UUID.randomUUID().toString(), due, request.message, request.repeatDays,
                request.scheduleHour, request.scheduleMinute);
        if (!store(context).edit().putString(entry.id, entry.encode()).commit()) return null;
        schedule(context, entry);
        return entry;
    }

    static boolean cancel(Context context, ReminderEntry entry) {
        if (!store(context).edit().remove(entry.id).commit()) return false;
        cancelAlarm(context, entry.id);
        context.getSystemService(NotificationManager.class).cancel(entry.id, 0);
        return true;
    }

    static List<ReminderEntry> list(Context context) {
        List<ReminderEntry> entries = new ArrayList<>();
        SharedPreferences preferences = store(context);
        for (String id : preferences.getAll().keySet()) {
            ReminderEntry entry = ReminderEntry.decode(id, preferences.getString(id, null));
            if (entry != null) entries.add(entry);
        }
        entries.sort(Comparator.comparingLong(entry -> entry.due));
        return entries;
    }

    static void restore(Context context) {
        for (ReminderEntry entry : list(context)) {
            if (entry.due <= System.currentTimeMillis()) {
                deliver(context, entry);
            } else {
                schedule(context, entry);
            }
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            restore(context);
        } else if (Intent.ACTION_TIME_CHANGED.equals(intent.getAction())
                || Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())
                || Intent.ACTION_DATE_CHANGED.equals(intent.getAction())) {
            rescheduleRepeating(context);
            restore(context);
        } else if (FIRE.equals(intent.getAction()) && intent.getData() != null) {
            String id = intent.getData().getLastPathSegment();
            ReminderEntry entry = ReminderEntry.decode(id, store(context).getString(id, null));
            if (entry != null && entry.due <= System.currentTimeMillis()) deliver(context, entry);
        }
    }

    private static void schedule(Context context, ReminderEntry entry) {
        context.getSystemService(AlarmManager.class).setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, entry.due, pending(context, entry.id, 0));
    }

    private static void rescheduleRepeating(Context context) {
        long now = System.currentTimeMillis();
        for (ReminderEntry entry : list(context)) {
            if (!entry.isRepeating()) continue;
            long due = ReminderSchedule.nextDue(now, entry.repeatDays,
                    entry.scheduleHour, entry.scheduleMinute, TimeZone.getDefault());
            ReminderEntry updated = new ReminderEntry(entry.id, due, entry.message, entry.repeatDays,
                    entry.scheduleHour, entry.scheduleMinute);
            store(context).edit().putString(entry.id, updated.encode()).commit();
            schedule(context, updated);
        }
    }

    private static PendingIntent pending(Context context, String id, int flags) {
        Intent intent = new Intent(context, ReminderReceiver.class).setAction(FIRE)
                .setData(new Uri.Builder().scheme("speaker-reminder").authority("local").appendPath(id).build());
        return PendingIntent.getBroadcast(context, 0, intent,
                flags | PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
    }

    @SuppressWarnings("deprecation")
    private static void deliver(Context context, ReminderEntry entry) {
        if (!notificationsAvailable(context)) return;
        PendingIntent open = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        String reminderText = entry.isRepeating()
                ? ReminderSchedule.describe(entry.repeatDays) + "：" + entry.message : entry.message;
        builder.setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("愛狐說提醒")
                .setContentText(reminderText)
                .setStyle(new Notification.BigTextStyle().bigText(reminderText))
                .setContentIntent(open)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT < 26) builder.setDefaults(Notification.DEFAULT_ALL);
        try {
            context.getSystemService(NotificationManager.class).notify(entry.id, 0, builder.build());
            if (entry.isRepeating()) {
                long nextDue = ReminderSchedule.nextDue(System.currentTimeMillis(), entry.repeatDays,
                        entry.scheduleHour, entry.scheduleMinute, TimeZone.getDefault());
                ReminderEntry next = new ReminderEntry(entry.id, nextDue, entry.message, entry.repeatDays,
                        entry.scheduleHour, entry.scheduleMinute);
                store(context).edit().putString(entry.id, next.encode()).commit();
                schedule(context, next);
            } else {
                store(context).edit().remove(entry.id).commit();
            }
        } catch (SecurityException ignored) {
            // Keep the reminder if notification access is revoked during delivery.
        }
    }

    private static void cancelAlarm(Context context, String id) {
        PendingIntent alarm = pending(context, id, PendingIntent.FLAG_NO_CREATE);
        if (alarm != null) {
            context.getSystemService(AlarmManager.class).cancel(alarm);
            alarm.cancel();
        }
    }

    private static SharedPreferences store(Context context) {
        return context.getSharedPreferences("local_reminders", Context.MODE_PRIVATE);
    }
}
