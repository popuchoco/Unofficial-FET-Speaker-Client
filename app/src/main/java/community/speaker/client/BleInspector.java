package community.speaker.client;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class BleInspector {
    private final Context context;
    private final Consumer<String> status;
    private final BiConsumer<BluetoothDevice, String> deviceFound;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Set<String> seen = new HashSet<>();
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private BluetoothGatt connection;
    private BluetoothGattCharacteristic pendingStandardRead;
    private final List<BluetoothGattCharacteristic> standardReadQueue = new ArrayList<>();
    private final List<String> standardReadResults = new ArrayList<>();
    private String discoveredServices;
    private final Runnable scanTimeout;
    private final Runnable connectionTimeout;

    BleInspector(Context context, Consumer<String> status, BiConsumer<BluetoothDevice, String> deviceFound) {
        this.context = context;
        this.status = status;
        this.deviceFound = deviceFound;
        scanTimeout = () -> {
            stopScan();
            status.accept(seen.isEmpty() ? "沒有找到附近的 BLE 裝置。" : "掃描完成。選取裝置可查看服務。");
        };
        connectionTimeout = () -> finishConnection("連線或探索服務逾時。");
    }

    void scan() {
        reset();
        seen.clear();
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        try {
            scanner = manager == null || manager.getAdapter() == null
                    ? null : manager.getAdapter().getBluetoothLeScanner();
            if (scanner == null) {
                status.accept("BLE 無法使用。請確認手機支援 BLE，並已開啟藍牙。");
                return;
            }
            scanCallback = new ScanCallback() {
                @Override public void onScanResult(int type, ScanResult result) {
                    ui.post(() -> {
                        if (scanCallback != this) return;
                        BluetoothDevice device = result.getDevice();
                        if (!seen.add(device.getAddress())) return;
                        String name = result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
                        String label = name == null || name.isEmpty() ? "未命名裝置" : name;
                        deviceFound.accept(device, label);
                    });
                }

                @Override public void onScanFailed(int code) {
                    ui.post(() -> {
                        if (scanCallback != this) return;
                        stopScan();
                        status.accept("BLE 掃描失敗，錯誤代碼 " + code + "。");
                    });
                }
            };
            scanner.startScan(scanCallback);
            status.accept("正在掃描附近裝置…");
            ui.postDelayed(scanTimeout, 10_000);
        } catch (SecurityException error) {
            stopScan();
            status.accept("請重新授予附近裝置或位置權限。");
        }
    }

    void inspect(BluetoothDevice device) {
        reset();
        status.accept("正在連線及探索服務…");
        try {
            connection = device.connectGatt(context, false, new BluetoothGattCallback() {
                @Override public void onConnectionStateChange(BluetoothGatt gatt, int code, int state) {
                    ui.post(() -> {
                        if (connection != gatt) return;
                        if (code != BluetoothGatt.GATT_SUCCESS || state == BluetoothProfile.STATE_DISCONNECTED) {
                            finishConnection("GATT 連線已中斷，狀態代碼 " + code + "。");
                        } else if (state == BluetoothProfile.STATE_CONNECTED) {
                            try {
                                if (!gatt.discoverServices()) finishConnection("無法開始探索 GATT 服務。");
                            } catch (SecurityException error) {
                                finishConnection("BLE 連線權限已取消。");
                            }
                        }
                    });
                }

                @Override public void onServicesDiscovered(BluetoothGatt gatt, int code) {
                    ui.post(() -> {
                        if (connection != gatt) return;
                        if (code != BluetoothGatt.GATT_SUCCESS) {
                            finishConnection("無法探索 GATT 服務，錯誤代碼 " + code + "。");
                            return;
                        }
                        StringBuilder summary = new StringBuilder();
                        for (BluetoothGattService service : gatt.getServices()) {
                            summary.append("\n\n服務 ").append(service.getUuid());
                            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                                summary.append("\n").append(characteristic.getUuid())
                                        .append(" · ").append(properties(characteristic.getProperties()));
                            }
                        }
                        String details = summary.length() == 0 ? "裝置未列出 GATT 服務。"
                                : "GATT 服務與特徵" + summary;
                        discoveredServices = details;
                        standardReadQueue.clear();
                        standardReadResults.clear();

                        BluetoothGattService deviceInfo = gatt.getService(BleStandardData.DEVICE_INFORMATION_SERVICE);
                        if (deviceInfo == null) {
                            standardReadResults.add("沒有標準裝置資訊服務。");
                        } else {
                            int knownCharacteristics = 0;
                            for (java.util.UUID characteristicUuid : new java.util.UUID[] {
                                    BleStandardData.MANUFACTURER_NAME,
                                    BleStandardData.MODEL_NUMBER,
                                    BleStandardData.FIRMWARE_REVISION,
                                    BleStandardData.HARDWARE_REVISION,
                                    BleStandardData.SOFTWARE_REVISION
                            }) {
                                BluetoothGattCharacteristic characteristic = deviceInfo.getCharacteristic(characteristicUuid);
                                if (characteristic == null) continue;
                                knownCharacteristics++;
                                queueStandardRead(characteristic);
                            }
                            if (knownCharacteristics == 0) {
                                standardReadResults.add("沒有標準廠牌、型號或版本特徵。");
                            }
                        }

                        BluetoothGattService batteryService = gatt.getService(BleStandardData.BATTERY_SERVICE);
                        BluetoothGattCharacteristic battery = batteryService == null ? null
                                : batteryService.getCharacteristic(BleStandardData.BATTERY_LEVEL);
                        if (battery == null) {
                            standardReadResults.add("沒有標準電量特徵。");
                        } else {
                            queueStandardRead(battery);
                        }
                        readNextStandardCharacteristic(gatt);
                    });
                }

                @SuppressWarnings("deprecation")
                @Override public void onCharacteristicRead(BluetoothGatt gatt,
                                                           BluetoothGattCharacteristic characteristic,
                                                           int code) {
                    byte[] value = characteristic.getValue();
                    ui.post(() -> finishStandardRead(gatt, characteristic, value, code));
                }

                @Override public void onCharacteristicRead(BluetoothGatt gatt,
                                                           BluetoothGattCharacteristic characteristic,
                                                           byte[] value, int code) {
                    ui.post(() -> finishStandardRead(gatt, characteristic, value, code));
                }
            }, BluetoothDevice.TRANSPORT_LE);
            if (connection == null) {
                status.accept("無法開始 GATT 連線。");
                return;
            }
            ui.postDelayed(connectionTimeout, 15_000);
        } catch (SecurityException error) {
            finishConnection("BLE 連線權限已取消。");
        }
    }

    private static String properties(int flags) {
        java.util.StringJoiner labels = new java.util.StringJoiner("、");
        if ((flags & BluetoothGattCharacteristic.PROPERTY_READ) != 0) labels.add("讀取");
        if ((flags & (BluetoothGattCharacteristic.PROPERTY_WRITE
                | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) labels.add("寫入");
        if ((flags & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) labels.add("通知");
        if ((flags & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) labels.add("指示");
        return labels.length() == 0 ? "0x" + Integer.toHexString(flags) : labels.toString();
    }

    private void queueStandardRead(BluetoothGattCharacteristic characteristic) {
        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
            String label = BleStandardData.BATTERY_LEVEL.equals(characteristic.getUuid())
                    ? "電量" : BleStandardData.deviceInfoLabel(characteristic.getUuid());
            standardReadResults.add("標準" + label + "特徵不可讀取。");
            return;
        }
        standardReadQueue.add(characteristic);
    }

    private void readNextStandardCharacteristic(BluetoothGatt gatt) {
        if (connection != gatt || pendingStandardRead != null) return;
        if (standardReadQueue.isEmpty()) {
            StringBuilder result = new StringBuilder(discoveredServices);
            for (String line : standardReadResults) result.append("\n").append(line);
            finishConnection(result.toString());
            return;
        }
        BluetoothGattCharacteristic next = standardReadQueue.remove(0);
        pendingStandardRead = next;
        try {
            if (!gatt.readCharacteristic(next)) {
                pendingStandardRead = null;
                addReadFailure(next, "無法開始讀取");
                readNextStandardCharacteristic(gatt);
            }
        } catch (SecurityException error) {
            finishConnection("BLE 連線權限已取消。");
        }
    }

    private void finishStandardRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                    byte[] value, int code) {
        if (connection != gatt || pendingStandardRead != characteristic) return;
        pendingStandardRead = null;
        if (code != BluetoothGatt.GATT_SUCCESS) {
            addReadFailure(characteristic, "讀取失敗，GATT 狀態 " + code);
        } else if (BleStandardData.BATTERY_LEVEL.equals(characteristic.getUuid())) {
            Integer percent = BleStandardData.batteryPercent(value);
            standardReadResults.add(percent == null ? "標準電量目前無法解讀。" : "標準電量 " + percent + "%。");
        } else {
            String label = BleStandardData.deviceInfoLabel(characteristic.getUuid());
            String text = BleStandardData.deviceInfoValue(value);
            standardReadResults.add(text == null ? "標準裝置資訊「" + label + "」目前無法解讀。"
                    : "標準裝置資訊「" + label + "」：" + text + "。");
        }
        readNextStandardCharacteristic(gatt);
    }

    private void addReadFailure(BluetoothGattCharacteristic characteristic, String reason) {
        String label = BleStandardData.BATTERY_LEVEL.equals(characteristic.getUuid())
                ? "電量" : BleStandardData.deviceInfoLabel(characteristic.getUuid());
        standardReadResults.add("標準" + label + reason + "。");
    }

    void pause() {
        boolean busy = scanCallback != null || connection != null;
        reset();
        if (busy) status.accept("診斷已停止。");
    }

    private void reset() {
        stopScan();
        closeConnection();
    }

    private void stopScan() {
        ui.removeCallbacks(scanTimeout);
        if (scanCallback != null && scanner != null) {
            try {
                scanner.stopScan(scanCallback);
            } catch (SecurityException ignored) {
                // The user may have revoked permissions while leaving the app.
            }
        }
        scanCallback = null;
        scanner = null;
    }

    private void finishConnection(String message) {
        closeConnection();
        status.accept(message);
    }

    private void closeConnection() {
        ui.removeCallbacks(connectionTimeout);
        pendingStandardRead = null;
        standardReadQueue.clear();
        standardReadResults.clear();
        discoveredServices = null;
        BluetoothGatt previous = connection;
        connection = null;
        if (previous != null) {
            try {
                previous.close();
            } catch (SecurityException ignored) {
                // The connection permission is no longer available.
            }
        }
    }
}
