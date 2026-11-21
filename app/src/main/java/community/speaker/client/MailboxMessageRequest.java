package community.speaker.client;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MailboxMessageRequest {
    enum Action { PLAY, DELETE }

    private static final Pattern PLAY = Pattern.compile(
            "(?:播放|聽|聽聽)(?:最新|第([1-9][0-9]{0,5})則?)(?:家庭)?留言");
    private static final Pattern DELETE = Pattern.compile(
            "(?:刪除|刪掉)(?:最新|第([1-9][0-9]{0,5})則?)(?:家庭)?留言");

    final Action action;
    final int number;

    private MailboxMessageRequest(Action action, int number) {
        this.action = action;
        this.number = number;
    }

    static MailboxMessageRequest parse(String speech) {
        if (speech == null) return null;
        String query = Normalizer.normalize(speech, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}，。！？?!,]", "")
                .replaceFirst("^(請問|請幫我|幫我|請|麻煩|我要|我想)", "");
        query = LocalAnswer.replaceChineseNumbers(query);
        Matcher play = PLAY.matcher(query);
        if (play.matches()) return request(Action.PLAY, play.group(1));
        Matcher delete = DELETE.matcher(query);
        if (delete.matches()) return request(Action.DELETE, delete.group(1));
        return null;
    }

    private static MailboxMessageRequest request(Action action, String number) {
        return new MailboxMessageRequest(action, number == null ? 1 : Integer.parseInt(number));
    }
}
