package client.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.ByteArrayInputStream;

public class AudioPlayer {

    private Clip clip;

    public AudioPlayer() {
    }

    public void play(byte[] audioData) {
        stop(); // stop any current playback before starting a new one
        System.out.println("[AudioPlayer] Received " + audioData.length + " bytes, attempting playback.");
        try {
            AudioInputStream mp3Stream = AudioSystem.getAudioInputStream(
                    new ByteArrayInputStream(audioData));
            AudioFormat baseFormat = mp3Stream.getFormat();
            System.out.println("[AudioPlayer] Decoded base format: " + baseFormat);
            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false);
            AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcmFormat, mp3Stream);
            clip = AudioSystem.getClip();
            clip.open(pcmStream);
            System.out.println("[AudioPlayer] Clip loaded, duration: " + clip.getMicrosecondLength() / 1_000_000.0 + "s");
            clip.start();
            System.out.println("[AudioPlayer] Playback started.");
        } catch (Exception e) {
            System.err.println("[AudioPlayer] Playback error: " + e);
            e.printStackTrace();
        }
    }

    public void stop() {
        if (clip != null) {
            if (clip.isRunning()) {
                clip.stop();
            }
            clip.close();
            clip = null;
        }
    }
}
