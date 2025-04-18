package id.flutter.background_service.event;

import id.flutter.background_service.ble.SingleCharacteristicResponse;
import id.flutter.background_service.converter.BleErrorJsonConverter;
import id.flutter.background_service.converter.SingleCharacteristicResponseJsonConverter;
import com.polidea.multiplatformbleadapter.errors.BleError;

import org.json.JSONException;

import io.flutter.plugin.common.EventChannel;

public class CharacteristicsMonitorStreamHandler implements EventChannel.StreamHandler {

    private EventChannel.EventSink eventSink;
    private SingleCharacteristicResponseJsonConverter characteristicResponseJsonConverter
            = new SingleCharacteristicResponseJsonConverter();
    private BleErrorJsonConverter bleErrorJsonConverter = new BleErrorJsonConverter();

    @Override
    synchronized public void onListen(Object o, EventChannel.EventSink eventSink) {
        this.eventSink = eventSink;
    }

    @Override
    synchronized public void onCancel(Object o) {
        eventSink = null;
    }

    synchronized public void onCharacteristicsUpdate(SingleCharacteristicResponse characteristic) throws JSONException {
        if (eventSink != null) {
            eventSink.success(characteristicResponseJsonConverter.toJson(characteristic));
        }
    }

    synchronized public void onError(BleError error, String transactionId) {
        if (eventSink != null) {
            eventSink.error(String.valueOf(error.errorCode.code), error.reason, bleErrorJsonConverter.toJson(error, transactionId));
        }
    }
}
