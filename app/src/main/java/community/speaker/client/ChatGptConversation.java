package community.speaker.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ChatGptConversation {
    private static final int MAX_CONTEXT_MESSAGES = 16;
    private final List<ChatGptProvider.Message> history = new ArrayList<>();

    synchronized String ask(String question, Responder responder) throws IOException {
        if (question == null || question.trim().isEmpty()) throw new IOException("請先說出問題。");
        if (question.length() > 4000) throw new IOException("問題太長，請分段再試。");
        List<ChatGptProvider.Message> request = new ArrayList<>(history);
        while (request.size() >= MAX_CONTEXT_MESSAGES) {
            request.remove(0);
            if (!request.isEmpty()) request.remove(0);
        }
        request.add(new ChatGptProvider.Message("user", question.trim()));
        String answer = responder.respond(Collections.unmodifiableList(request));
        if (answer == null || answer.trim().isEmpty()) throw new IOException("ChatGPT 沒有提供文字回答。");
        history.add(new ChatGptProvider.Message("user", question.trim()));
        history.add(new ChatGptProvider.Message("assistant", answer.trim()));
        while (history.size() > MAX_CONTEXT_MESSAGES) {
            history.remove(0);
            if (!history.isEmpty()) history.remove(0);
        }
        return answer.trim();
    }

    synchronized void clear() {
        history.clear();
    }

    synchronized int turnCount() {
        return history.size() / 2;
    }

    interface Responder {
        String respond(List<ChatGptProvider.Message> messages) throws IOException;
    }
}
