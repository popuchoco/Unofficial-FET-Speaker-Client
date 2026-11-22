package community.speaker.client;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReminderActionRequest {
    private static final Pattern CANCEL = Pattern.compile(
            "(?:取消|刪除|刪掉)(?:下一個|最近的?|最早的?|第([1-9][0-9]{0,3})(?:個|筆|則)?)"
                    + "(?:本機)?提醒(?:事項)?");
    private static final Pattern CANCEL_BY_TASK = Pattern.compile(
            "(?:取消|刪除|刪掉)(?:本機)?提醒我(.+)");
    private static final Pattern CANCEL_TASK_REMINDER = Pattern.compile(
            "(?:取消|刪除|刪掉)(.+?)(?:的)?(?:本機)?提醒(?:事項)?");
    private static final Pattern BROAD_CANCEL = Pattern.compile(
            "(?:取消|刪除|刪掉)(?:本機|所有)?提醒(?:事項)?");

    final int number;
    final String task;

    private ReminderActionRequest(int number, String task) {
        this.number = number;
        this.task = task;
    }

    static ReminderActionRequest parse(String speech) {
        if (speech == null) return null;
        String query = SpeechPrefix.strip(Normalizer.normalize(speech, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}，。！？?!,]", ""));
        if (BROAD_CANCEL.matcher(query).matches()) return null;
        query = query.replace("下一個", "最近的");
        Matcher cancel = CANCEL.matcher(LocalAnswer.replaceChineseNumbers(query));
        if (cancel.matches()) {
            return new ReminderActionRequest(cancel.group(1) == null ? 1 : Integer.parseInt(cancel.group(1)), null);
        }
        Matcher byTask = CANCEL_BY_TASK.matcher(query);
        if (byTask.matches()) return forTask(byTask.group(1));
        Matcher taskReminder = CANCEL_TASK_REMINDER.matcher(query);
        return taskReminder.matches() ? forTask(taskReminder.group(1)) : null;
    }

    boolean matchesMessage(String message) {
        return task != null && normalize(task).length() > 0 && normalize(message).contains(normalize(task));
    }

    private static ReminderActionRequest forTask(String task) {
        String requestedTask = task.trim();
        if (requestedTask.isEmpty() || requestedTask.length() > 200
                || requestedTask.matches("第[0-9]+(?:個|筆|則)?")) return null;
        return new ReminderActionRequest(0, requestedTask);
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}，。！？?!,「」『』\\\"']", "");
    }
}
