package com.mytv.home;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** A soft two-note bell, made in code (no sound file needed). */
public final class Chime {
    public static void play() {
        new Thread(() -> {
            int rate = 44100, n = (int) (rate * 1.6);
            short[] pcm = new short[n];
            double[][] notes = {{1046.5, 0}, {1318.5, 0.18}}; // C6 then E6
            for (double[] note : notes) {
                int start = (int) (note[1] * rate);
                for (int i = start; i < n; i++) {
                    double t = (i - start) / (double) rate;
                    double env = Math.exp(-t * 3.2) * Math.min(1, t * 200);
                    double v = env * (Math.sin(2 * Math.PI * note[0] * t) + 0.3 * Math.sin(2 * Math.PI * note[0] * 2.01 * t));
                    int s = pcm[i] + (int) (v * 5200);
                    pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s));
                }
            }
            try {
                AudioTrack tr = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                        .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(n * 2).setTransferMode(AudioTrack.MODE_STATIC).build();
                tr.write(pcm, 0, n);
                tr.play();
                Thread.sleep(1800);
                tr.release();
            } catch (Exception ignored) { }
        }).start();
    }
}
