package server.network;

import server.auth.UserStore;
import server.parser.CommandInterpreter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {

  private final Socket socket;

  private BufferedReader in;
  private BufferedWriter out;
  private CommandInterpreter interpreter;

  public ClientHandler(Socket socket, UserStore store) {
    this.socket = socket;
    this.interpreter = new CommandInterpreter(store);
  }

  @Override
  public void run() {
    try {
      in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

      String line;
      while ((line = in.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty())
          continue;
        String resp = interpreter.interpret(line);
        if (resp == null) {
          resp = "ERROR: internal";
        }
        if (resp.startsWith("PLAY:")) {
          String filePath = resp.substring("PLAY:".length()).trim();
          out.write(resp);
          out.write("\n");
          out.flush();
          new FileSender(socket, filePath).start();
          continue;
        }
        out.write(resp);
        out.write("\n");
        out.flush();
      }
    } catch (IOException e) {
      // client disconnected or IO error
    } finally {
      try {
        socket.close();
      } catch (IOException ignored) {
      }
    }
  }
}
