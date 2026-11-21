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

    @Test public void leavesBroadOrInvalidRequestsForExistingClarification() {
        assertNull(ReminderActionRequest.parse("取消提醒"));
        assertNull(ReminderActionRequest.parse("取消第0個提醒"));
        assertNull(ReminderActionRequest.parse("明天早上九點提醒我拿包裹"));
        assertNull(ReminderActionRequest.parse(null));
    }
}
