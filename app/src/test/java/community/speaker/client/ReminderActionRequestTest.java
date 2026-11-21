package community.speaker.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ReminderActionRequestTest {
    @Test public void recognizesNearestAndNumberedReminderCancellation() {
        assertEquals(1, ReminderActionRequest.parse("取消下一個提醒").number);
        assertEquals(1, ReminderActionRequest.parse("刪除最近的本機提醒事項").number);
        assertEquals(2, ReminderActionRequest.parse("請幫我取消第二筆提醒").number);
        assertEquals(12, ReminderActionRequest.parse("刪掉第十二個提醒").number);
    }

    @Test public void findsRemindersByTheirSpokenTask() {
        ReminderActionRequest request = ReminderActionRequest.parse("取消拿包裹的提醒");
        assertEquals("拿包裹", request.task);
        org.junit.Assert.assertTrue(request.matchesMessage("明天拿包裹"));
        org.junit.Assert.assertFalse(request.matchesMessage("繳交帳單"));

        request = ReminderActionRequest.parse("請幫我取消提醒我三點集合");
        assertEquals("三點集合", request.task);
        org.junit.Assert.assertTrue(request.matchesMessage("三點集合"));
    }

    @Test public void leavesBroadOrInvalidRequestsForExistingClarification() {
        assertNull(ReminderActionRequest.parse("取消提醒"));
        assertNull(ReminderActionRequest.parse("取消本機提醒"));
        assertNull(ReminderActionRequest.parse("取消第0個提醒"));
        assertNull(ReminderActionRequest.parse("明天早上九點提醒我拿包裹"));
        assertNull(ReminderActionRequest.parse(null));
    }
}
