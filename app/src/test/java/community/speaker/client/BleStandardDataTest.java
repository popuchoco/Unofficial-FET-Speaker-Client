package community.speaker.client;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

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

    @Test public void identifiesReadableDeviceInformationWithoutSerialNumber() {
        assertEquals("廠牌", BleStandardData.deviceInfoLabel(BleStandardData.MANUFACTURER_NAME));
        assertEquals("型號", BleStandardData.deviceInfoLabel(BleStandardData.MODEL_NUMBER));
        assertEquals("韌體版本", BleStandardData.deviceInfoLabel(BleStandardData.FIRMWARE_REVISION));
        assertEquals("硬體版本", BleStandardData.deviceInfoLabel(BleStandardData.HARDWARE_REVISION));
        assertEquals("軟體版本", BleStandardData.deviceInfoLabel(BleStandardData.SOFTWARE_REVISION));
        assertNull(BleStandardData.deviceInfoLabel(
                UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")));
    }

    @Test public void decodesDeviceInformationAsBoundedUtf8Text() {
        assertEquals("出門問問", BleStandardData.deviceInfoValue("出門問問".getBytes(StandardCharsets.UTF_8)));
        assertNull(BleStandardData.deviceInfoValue(null));
        assertNull(BleStandardData.deviceInfoValue(new byte[0]));
        assertNull(BleStandardData.deviceInfoValue(new byte[] { (byte) 0xff }));
        assertNull(BleStandardData.deviceInfoValue(new byte[] { 'x', '\n' }));
        assertNull(BleStandardData.deviceInfoValue(new byte[513]));
    }
}
