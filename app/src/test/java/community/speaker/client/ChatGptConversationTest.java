package community.speaker.client;

import org.junit.Test;

import java.io.IOException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ChatGptConversationTest {
    @Test public void successfulTurnsStayInMemoryForFollowUpQuestions() throws IOException {
        ChatGptConversation conversation = new ChatGptConversation();
        conversation.ask("第一題", messages -> "第一個回答");
        conversation.ask("補充問題", messages -> {
            assertEquals(3, messages.size());
            assertEquals("第一題", messages.get(0).content);
            assertEquals("第一個回答", messages.get(1).content);
            assertEquals("補充問題", messages.get(2).content);
            return "第二個回答";
        });

        assertEquals(2, conversation.turnCount());
        conversation.clear();
        assertEquals(0, conversation.turnCount());
    }

    @Test public void failedResponsesDoNotEnterConversationHistory() throws IOException {
        ChatGptConversation conversation = new ChatGptConversation();
        try {
            conversation.ask("未完成的問題", messages -> { throw new IOException("failure"); });
            fail("expected failure");
        } catch (IOException expected) {
            assertEquals(0, conversation.turnCount());
        }
    }

    @Test public void conversationHistoryIsBounded() throws IOException {
        ChatGptConversation conversation = new ChatGptConversation();
        for (int index = 0; index < 10; index++) {
            final int turn = index;
            conversation.ask("問題" + turn, messages -> "回答" + turn);
        }

        final int[] messagesSeen = { 0 };
        conversation.ask("最後一題", messages -> {
            messagesSeen[0] = messages.size();
            assertEquals("問題2", messages.get(0).content);
            return "最後回答";
        });
        assertEquals(15, messagesSeen[0]);
        assertEquals(8, conversation.turnCount());
    }
}
