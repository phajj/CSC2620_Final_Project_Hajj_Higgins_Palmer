package client.audio;

// Speech-to-text model,Vosk
import org.vosk.Model;
import org.vosk.Recognizer;

import client.network.ServerConnection;

import javax.sound.sampled.*;
import java.io.IOException;

/**
 * This class handles microphone input using the Vosk library.
 * 
 * @author Peter Hajj
 */
public class MicrophoneListener {

  public interface CommandListener {
    void onCommandCaptured(String command);
  }

  private static final float SAMPLE_RATE = 16000f;
  private static final int BUFFER_SIZE = 4096;
  // ~128 ms per buffer read at 16 kHz/16-bit, so this is checked ~8x per second
  private static final long SILENCE_MS = 3000L;

  private final String modelPath;
  private final KeywordDetector keywordDetector;
  private CommandListener commandListener;
  private ServerConnection serverConnection;
  private AudioPlayer audioPlayer;
  private volatile boolean running;
  private volatile boolean recordingMode;
  private volatile long lastSpeechTime = 0L;
  private Thread listenerThread;
  private final StringBuilder commandBuffer = new StringBuilder();

  public MicrophoneListener(String modelPath, KeywordDetector keywordDetector) {
    this.modelPath = modelPath;
    this.keywordDetector = keywordDetector;
    if (keywordDetector != null) {
      keywordDetector.setRecordingListener(() -> {
        recordingMode = true;
        commandBuffer.setLength(0);
        lastSpeechTime = System.currentTimeMillis();
        System.out.println("\n[Keyword detected] Recording command...");
      });
    }
  }

  public MicrophoneListener(String modelPath) {
    this(modelPath, null);
  }

  public MicrophoneListener() {
    this("model", null);
  }

  public void setCommandListener(CommandListener listener) {
    this.commandListener = listener;
  }

  public void setServerConnection(ServerConnection serverConnection) {
    this.serverConnection = serverConnection;
  }

  public void setAudioPlayer(AudioPlayer audioPlayer) {
    this.audioPlayer = audioPlayer;
  }

  /**
   * Starts the microphone listener on a background daemon thread.
   */
  public void startListening() {
    if (running)
      return;
    running = true;
    listenerThread = new Thread(this::listen, "MicrophoneListener");
    listenerThread.setDaemon(true);
    listenerThread.start();
  }

  /**
   * Signals the listener thread to stop and interrupts it if it is blocked on
   * I/O.
   */
  public void stopListening() {
    running = false;
    if (listenerThread != null) {
      listenerThread.interrupt();
    }
  }

  /**
   * Captures audio from the default microphone and feeds it to the Vosk
   * recognizer.
   * Runs until running is false or the thread is interrupted.
   */
  private void listen() {
    AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

    if (!AudioSystem.isLineSupported(info)) {
      System.err.println("Microphone not supported on this system.");
      return;
    }

    try (Model model = new Model(modelPath);
        Recognizer recognizer = new Recognizer(model, SAMPLE_RATE);
        TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info)) {

      microphone.open(format);
      microphone.start();
      System.out.println("Listening... (speak into the microphone)");

      byte[] buffer = new byte[BUFFER_SIZE];
      while (running && !Thread.currentThread().isInterrupted()) {
        int bytesRead = microphone.read(buffer, 0, buffer.length);
        if (bytesRead > 0) {
          if (recognizer.acceptWaveForm(buffer, bytesRead)) {
            String transcript = extractText(recognizer.getResult());
            if (!transcript.isEmpty()) {
              lastSpeechTime = System.currentTimeMillis();
              handleTranscript(transcript);
            }
          } else {
            String partial = extractText(recognizer.getPartialResult());
            if (!partial.isEmpty()) {
              // Reset the silence clock whenever the recognizer is
              // actively picking up speech, even mid-utterance.
              lastSpeechTime = System.currentTimeMillis();
            }
            if (!recordingMode) {
              System.out.print("\r[Listening]  " + padRight(partial, 60));
            }
          }

          if (recordingMode && lastSpeechTime > 0 &&
              System.currentTimeMillis() - lastSpeechTime > SILENCE_MS) {
            finalizeCommand();
          }
        }
      }

      String finalResult = extractText(recognizer.getFinalResult());
      if (!finalResult.isEmpty()) {
        handleTranscript(finalResult);
      }
      System.out.println("\nStopped listening.");

    } catch (LineUnavailableException e) {
      System.err.println("Microphone unavailable: " + e.getMessage());
    } catch (IOException e) {
      // Prompt to Vosk download page
      System.err.println("Error loading Vosk model from '" + modelPath + "': " + e.getMessage());
      System.err
          .println("Download a model from https://alphacephei.com/vosk/models and unzip it to the 'model/' directory.");
    }
  }

  /**
   * @param transcript spoken words
   *                   Routes a completed transcript: checks for the keyword when
   *                   idle, or accumulates
   *                   it into the command buffer when in recording mode.
   */
  private void handleTranscript(String transcript) {
    if (recordingMode) {
      if (commandBuffer.length() > 0)
        commandBuffer.append(' ');
      commandBuffer.append(transcript);
      System.out.println("\n[Recording]  " + commandBuffer);
    } else {
      System.out.println("\n[Transcript] " + transcript);
      if (keywordDetector != null && keywordDetector.detect(transcript)) {
        // Words spoken in the same utterance after the keyword are captured
        // immediately so nothing said right after the keyword is lost.
        String remainder = extractAfterKeyword(transcript, keywordDetector.getKeyword());
        if (!remainder.isEmpty()) {
          commandBuffer.append(remainder);
          System.out.println("\n[Recording]  " + commandBuffer);
        }
      }
    }
  }

  /**
   * @param transcript spoken words
   * @param keyword    wake up word
   *                   Returns all words in {@code transcript} that follow the
   *                   first occurrence of {@code keyword}
   */
  private String extractAfterKeyword(String transcript, String keyword) {
    String[] words = transcript.split("\\s+");
    for (int i = 0; i < words.length; i++) {
      if (words[i].toLowerCase().equals(keyword)) {
        StringBuilder sb = new StringBuilder();
        for (int j = i + 1; j < words.length; j++) {
          if (sb.length() > 0)
            sb.append(' ');
          sb.append(words[j]);
        }
        return sb.toString();
      }
    }
    return "";
  }

  /**
   * Called after 3 seconds of silence in recording mode. Resets state for the
   * next keyword activation.
   */
  private void finalizeCommand() {
    recordingMode = false;
    if (keywordDetector != null)
      keywordDetector.resetRecordingMode();
    lastSpeechTime = 0L;
    String command = commandBuffer.toString().trim();
    commandBuffer.setLength(0);
    System.out.println("\n[Command complete] " + command);
    if (commandListener != null && !command.isEmpty()) {
      commandListener.onCommandCaptured(command);
    }
    if (serverConnection != null && !command.isEmpty()) {
      sendToServer(command);
    }
  }

  /**
   * Sends the transcribed command to the server and handles the response.
   * If the server responds with a PLAY acknowledgement it reads the subsequent
   * FILE_SIZE header and receives the audio bytes, then plays them if an
   * {@link AudioPlayer} has been set.
   *
   * @param command the transcribed voice command to send
   */
  private void sendToServer(String command) {
    String response = serverConnection.sendCommand(command);
    if (response == null) {
      System.err.println("[Server] No response received.");
      return;
    }
    System.out.println("[Server] " + response);

    String upperCommand = command.trim().toUpperCase();
    if (response.equals("OK") && audioPlayer != null) {
      switch (upperCommand) {
        case "STOP":
          audioPlayer.stop();
          break;
        case "PAUSE":
          audioPlayer.pause();
          break;
        case "RESUME":
          audioPlayer.resume();
          break;
      }
    }

    if (response.startsWith("PLAY:")) {
      String fileSizeHeader = serverConnection.readLine();
      if (fileSizeHeader == null || !fileSizeHeader.startsWith("FILE_SIZE:")) {
        System.err.println("[Server] Expected FILE_SIZE header, got: " + fileSizeHeader);
        return;
      }
      try {
        long size = Long.parseLong(fileSizeHeader.substring("FILE_SIZE:".length()).trim());
        byte[] audioData = serverConnection.receiveAudioData(size);
        if (audioData == null) {
          System.err.println("[Server] Failed to receive audio data.");
        } else if (audioPlayer != null) {
          audioPlayer.play(audioData);
        } else {
          System.err.println("[Client] No AudioPlayer configured; cannot play received audio.");
        }
      } catch (NumberFormatException e) {
        System.err.println("[Server] Malformed FILE_SIZE header: " + fileSizeHeader);
      }
    }
  }

  /**
   * Extracts the transcript string from Vosk's JSON output.
   *
   * @param json the raw JSON string returned by the Vosk recognizer
   * @return the transcript value, or an empty string if not found
   */
  private String extractText(String json) {
    for (String key : new String[] { "\"text\"", "\"partial\"" }) {
      int keyIdx = json.indexOf(key);
      if (keyIdx >= 0) {
        int colon = json.indexOf(':', keyIdx + key.length());
        int start = json.indexOf('"', colon + 1) + 1;
        int end = json.indexOf('"', start);
        if (start > 0 && end > start) {
          return json.substring(start, end);
        }
      }
    }
    return "";
  }

  /**
   * Left-justifies to keep the console line stable.
   */
  private String padRight(String s, int width) {
    return String.format("%-" + width + "s", s);
  }
}
