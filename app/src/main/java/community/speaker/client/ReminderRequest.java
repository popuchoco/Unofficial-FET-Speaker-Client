package community.speaker.client;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReminderRequest {
    private static final Pattern REQUEST = Pattern.compile(
            "(?:請幫我|幫我|請)?(.*?)後\\s*提醒我\\s*(.+)", Pattern.DOTALL);
    final int seconds;
    final String message;

    private ReminderRequest(int seconds, String message) {
        this.seconds = seconds;
        this.message = message;
    }

    static ReminderRequest parse(String speech) {
        if (!speech.contains("提醒我")) return null;
        Matcher match = REQUEST.matcher(Normalizer.normalize(speech.trim(), Normalizer.Form.NFKC));
        if (match.matches()) {
            String message = match.group(2).replaceFirst("[。！？!?]+$", "").trim();
            ClockRequest duration = ClockRequest.parse("倒數" + match.group(1));
            if (duration != null && duration.kind == ClockRequest.Kind.TIMER
                    && !message.isEmpty() && message.length() <= 200) {
                return new ReminderRequest(duration.seconds, message);
            }
        }
        return new ReminderRequest(0, "");
    }
}
