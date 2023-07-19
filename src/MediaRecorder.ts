
import { defineCustomEventTarget } from 'event-target-shim';
import { NativeModules } from 'react-native';

const { WebRTCModule } = NativeModules;

const MEDIA_DEVICES_EVENTS = [ 'devicechange' ];

class MediaRecorder {
    /**
     * W3C "Media Capture and Streams" compatible {@code enumerateDevices}
     * implementation.
     */
    start(recordAudio: boolean, resolution: number) {
        WebRTCModule.startRecord(recordAudio, resolution);
    }

    stop() {
        WebRTCModule.stopRecord();
    }

    setOrientation(orientation: number) {
        WebRTCModule.setOrientation(orientation);
    }
}

export default new MediaRecorder();
