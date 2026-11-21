package community.speaker.client;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class BleStandardData {
    static final UUID BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    static final UUID BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    static final UUID DEVICE_INFORMATION_SERVICE = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    static final UUID MODEL_NUMBER = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
    static final UUID FIRMWARE_REVISION = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
    static final UUID HARDWARE_REVISION = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb");
    static final UUID SOFTWARE_REVISION = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb");
    static final UUID MANUFACTURER_NAME = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");

    private BleStandardData() {}

    static Integer batteryPercent(byte[] value) {
        if (value == null || value.length != 1) return null;
        int percent = value[0] & 0xff;
        return percent <= 100 ? percent : null;
    }

    static String deviceInfoLabel(UUID characteristic) {
        if (MANUFACTURER_NAME.equals(characteristic)) return "廠牌";
        if (MODEL_NUMBER.equals(characteristic)) return "型號";
        if (FIRMWARE_REVISION.equals(characteristic)) return "韌體版本";
        if (HARDWARE_REVISION.equals(characteristic)) return "硬體版本";
        if (SOFTWARE_REVISION.equals(characteristic)) return "軟體版本";
        return null;
    }

    static String deviceInfoValue(byte[] value) {
        if (value == null || value.length == 0 || value.length > 512) return null;
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value)).toString();
            for (int index = 0; index < decoded.length(); index++) {
                if (Character.isISOControl(decoded.charAt(index))) return null;
            }
            decoded = decoded.trim();
            if (decoded.isEmpty() || decoded.length() > 128) return null;
            return decoded;
        } catch (CharacterCodingException invalidUtf8) {
            return null;
        }
    }
}
