package community.speaker.client;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AlarmManagementRequestTest {
    @Test public void recognizesRequestsToManageExistingAlarms() {
        assertTrue(AlarmManagementRequest.shouldShowAlarms("查看鬧鐘"));
        assertTrue(AlarmManagementRequest.shouldShowAlarms("請幫我取消所有鬧鐘"));
        assertTrue(AlarmManagementRequest.shouldShowAlarms("取消明天早上的鬧鐘"));
        assertTrue(AlarmManagementRequest.shouldShowAlarms("打開我的鬧鐘清單"));
    }

    @Test public void leavesNewAlarmAndUnrelatedRequestsToTheirHandlers() {
        assertFalse(AlarmManagementRequest.shouldShowAlarms("設定鬧鐘早上七點半"));
        assertFalse(AlarmManagementRequest.shouldShowAlarms("現在幾點"));
        assertFalse(AlarmManagementRequest.shouldShowAlarms(null));
    }
}
