package community.speaker.client;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ReminderRequestTest {
    @Test public void parsesDurationAndPreservesMessage() {
        ReminderRequest request = ReminderRequest.parse("幫我三分鐘後提醒我 關火！");
        assertEquals(180, request.seconds);
        assertEquals("關火", request.message);

        request = ReminderRequest.parse("一小時兩分鐘後提醒我拿快遞");
        assertEquals(3720, request.seconds);
        assertEquals("拿快遞", request.message);
    }

    @Test public void requiresActionAndDuration() {
        assertEquals(0, ReminderRequest.parse("三分鐘後提醒我").seconds);
        assertEquals(0, ReminderRequest.parse("明天提醒我拿包裹").seconds);
        assertEquals(0, ReminderRequest.parse("二十五小時後提醒我拿包裹").seconds);
        assertEquals(0, ReminderRequest.parse("三分鐘後提醒我。").seconds);
        assertNull(ReminderRequest.parse("倒數三分鐘"));
    }
}
