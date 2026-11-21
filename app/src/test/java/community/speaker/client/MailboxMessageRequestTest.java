package community.speaker.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MailboxMessageRequestTest {
    @Test public void selectsLatestOrNumberedMessagesForPlayback() {
        MailboxMessageRequest latest = MailboxMessageRequest.parse("播放最新留言");
        assertEquals(MailboxMessageRequest.Action.PLAY, latest.action);
        assertEquals(1, latest.number);

        MailboxMessageRequest numbered = MailboxMessageRequest.parse("請幫我聽聽第二則家庭留言");
        assertEquals(MailboxMessageRequest.Action.PLAY, numbered.action);
        assertEquals(2, numbered.number);
    }

    @Test public void requestsConfirmationForDeletingAMessage() {
        MailboxMessageRequest request = MailboxMessageRequest.parse("刪除第3則留言");
        assertEquals(MailboxMessageRequest.Action.DELETE, request.action);
        assertEquals(3, request.number);
        assertEquals(1, MailboxMessageRequest.parse("刪掉最新留言").number);
    }

    @Test public void leavesAmbiguousMailboxActionsForTheMailboxList() {
        assertNull(MailboxMessageRequest.parse("播放家庭留言"));
        assertNull(MailboxMessageRequest.parse("刪除留言"));
        assertNull(MailboxMessageRequest.parse("播放第0則留言"));
        assertNull(MailboxMessageRequest.parse("家庭留言"));
    }
}
