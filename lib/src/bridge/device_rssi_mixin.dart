part of _internal;

mixin RssiMixin on FlutterBLE {
  Future<int> rssi(Peripheral peripheral, String transactionId) async {
    return await BackgroundService.backgroundChannel
        .invokeMethod(MethodName.rssi, <String, dynamic>{
      ArgumentName.deviceIdentifier: peripheral.identifier,
      ArgumentName.transactionId: transactionId
    }).catchError((errorJson) =>
            Future.error(BleError.fromJson(jsonDecode(errorJson.details))));
  }
}
