package community.speaker.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LocalActionRequestTest {
    @Test public void opensMailboxFromDirectVoiceRequests() {
        assertEquals(LocalActionRequest.Action.OPEN_MAILBOX, LocalActionRequest.parse("家庭留言"));
        assertEquals(LocalActionRequest.Action.OPEN_MAILBOX, LocalActionRequest.parse("請幫我查看本機留言清單"));
        assertEquals(LocalActionRequest.Action.OPEN_MAILBOX, LocalActionRequest.parse("播放家庭留言"));
    }

    @Test public void opensReminderListWithoutConfusingNewReminders() {
        assertEquals(LocalActionRequest.Action.OPEN_REMINDERS, LocalActionRequest.parse("查看本機提醒"));
        assertEquals(LocalActionRequest.Action.OPEN_REMINDERS, LocalActionRequest.parse("我有哪些提醒事項"));
        assertEquals(LocalActionRequest.Action.OPEN_REMINDERS, LocalActionRequest.parse("幫我取消提醒"));
        assertNull(LocalActionRequest.parse("明天早上九點提醒我拿包裹"));
    }

    @Test public void ignoresUnrelatedQuestions() {
        assertNull(LocalActionRequest.parse("家庭留言機如何設定"));
        assertNull(LocalActionRequest.parse("今天幾點提醒我吃藥"));
        assertNull(LocalActionRequest.parse(null));
    }
}
