package community.speaker.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BleStandardDataTest {
    @Test public void readsStandardBatteryPercentageAsUnsignedByte() {
        assertEquals(Integer.valueOf(0), BleStandardData.batteryPercent(new byte[] { 0 }));
        assertEquals(Integer.valueOf(100), BleStandardData.batteryPercent(new byte[] { 100 }));
        assertEquals(Integer.valueOf(100), BleStandardData.batteryPercent(new byte[] { (byte) 100 }));
    }

    @Test public void rejectsInvalidBatteryValues() {
        assertNull(BleStandardData.batteryPercent(null));
        assertNull(BleStandardData.batteryPercent(new byte[0]));
        assertNull(BleStandardData.batteryPercent(new byte[] { 100, 0 }));
        assertNull(BleStandardData.batteryPercent(new byte[] { (byte) 255 }));
    }
}
