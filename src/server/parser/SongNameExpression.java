package server.parser;

import java.util.ArrayList;

import server.library.Song;
import server.library.SongLibrary;

public class SongNameExpression implements CommandExpression {

  private SongLibrary library;

  public SongNameExpression() {
    this.library = SongLibrary.getInstance();
  }

  @Override
  public String interpret(String context) {
    String songName = "";
    if (context.toLowerCase().startsWith("play ")) {
      songName = context.substring(5).strip(); // Strip command to get song name
    }

    try {
      Song song = library.findByTitle(songName); // If no error is thrown the song exists
      return "PLAY:" + song.getSong().getPath();
    } catch (Exception e) {
      return "ERROR: Unknown Song";
    }
  }
}
