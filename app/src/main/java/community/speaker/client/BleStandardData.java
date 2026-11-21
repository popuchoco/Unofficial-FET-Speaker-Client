package community.speaker.client;

import java.util.UUID;

final class BleStandardData {
    static final UUID BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    static final UUID BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    private BleStandardData() {}

    static Integer batteryPercent(byte[] value) {
        if (value == null || value.length != 1) return null;
        int percent = value[0] & 0xff;
        return percent <= 100 ? percent : null;
    }
}
