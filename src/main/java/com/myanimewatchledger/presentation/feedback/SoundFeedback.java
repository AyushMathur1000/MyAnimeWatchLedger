package com.myanimewatchledger.presentation.feedback;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.ByteArrayInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plays a short, restrained click for activated buttons.
 *
 * Audio initialization and playback are kept off the JavaFX Application Thread.
 * If the host has no usable audio output, feedback quietly disables itself.
 */
public final class SoundFeedback {
    private static final Logger LOGGER = Logger.getLogger(SoundFeedback.class.getName());
    private static final float SAMPLE_RATE = 22_050f;
    private static final int SAMPLE_COUNT = 529; // approximately 24 ms
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    private static final ExecutorService AUDIO_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ui-sound-feedback");
        thread.setDaemon(true);
        return thread;
    });

    private static Clip clickClip;
    private static volatile boolean unavailable;
    private static volatile boolean enabled = true;
    private static volatile boolean closed;

    private SoundFeedback() {
    }

    /** Updates the live preference; no audio work is performed on the caller thread. */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /** Schedules a click without blocking the caller or delaying the UI action. */
    public static void playClick() {
        if (!enabled || closed || unavailable) {
            return;
        }
        try {
            AUDIO_EXECUTOR.execute(() -> {
                if (!enabled || closed || unavailable) {
                    return;
                }
                try {
                    if (clickClip == null) {
                        clickClip = createClickClip();
                    }
                    if (clickClip.isRunning()) {
                        clickClip.stop();
                    }
                    clickClip.setFramePosition(0);
                    clickClip.start();
                } catch (Exception failure) {
                    unavailable = true;
                    closeClip();
                    LOGGER.log(Level.FINE, "UI sound feedback is unavailable on this device.", failure);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Application shutdown raced with a final UI event; no action is needed.
        }
    }

    /** Releases the audio line during application shutdown. */
    public static void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            AUDIO_EXECUTOR.execute(SoundFeedback::closeClip);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            closeClip();
        }
        AUDIO_EXECUTOR.shutdown();
    }

    private static Clip createClickClip() throws Exception {
        byte[] audio = createClickSamples();
        Clip clip = AudioSystem.getClip();
        try (var input = new javax.sound.sampled.AudioInputStream(
                new ByteArrayInputStream(audio), FORMAT, SAMPLE_COUNT)) {
            clip.open(input);
        }
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            gain.setValue(Math.max(gain.getMinimum(), Math.min(-8.0f, gain.getMaximum())));
        }
        return clip;
    }

    private static byte[] createClickSamples() {
        byte[] audio = new byte[SAMPLE_COUNT * 2];
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            double time = i / (double) SAMPLE_RATE;
            double envelope = Math.exp(-time * 185.0);
            double transientNoise = i < 90 ? (Math.random() * 2.0 - 1.0) * 0.12 : 0.0;
            double tone = Math.sin(2.0 * Math.PI * 1_850.0 * time) * 0.48
                    + Math.sin(2.0 * Math.PI * 2_750.0 * time) * 0.22;
            short sample = (short) (Math.max(-1.0, Math.min(1.0,
                    (tone + transientNoise) * envelope * 0.34)) * Short.MAX_VALUE);
            audio[i * 2] = (byte) (sample & 0xff);
            audio[i * 2 + 1] = (byte) ((sample >>> 8) & 0xff);
        }
        return audio;
    }

    private static void closeClip() {
        if (clickClip != null) {
            clickClip.stop();
            clickClip.close();
            clickClip = null;
        }
    }
}
