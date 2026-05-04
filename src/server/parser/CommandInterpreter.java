package server.parser;

import server.auth.UserStore;

/**
 * This class handles the logic from commands sent by the client.
 * If the command is a song play request the string is passed to the
 * SongNameExpression class
 *
 * @author Matthew Palmer
 * @author Jackson Higgins
 */
public class CommandInterpreter implements CommandExpression {

  private final UserStore store;
  private final SongNameExpression songNameExpression;
  private String currentUser = null;

  public CommandInterpreter(UserStore store) {
    this.store = store;
    this.songNameExpression = new SongNameExpression();
  }

  /**
   * This method parses the command string and executes logic based on the command
   *
   * @param rawCommand the string representation of the command
   */
  @Override
  public String interpret(String rawCommand) {
    if (rawCommand == null)
      return "ERROR: empty";
    String trimmed = rawCommand.trim();
    if (trimmed.isEmpty())
      return "ERROR: empty";

    int firstSpace = trimmed.indexOf(' ');
    String cmd = (firstSpace == -1) ? trimmed.toUpperCase() : trimmed.substring(0, firstSpace).toUpperCase();
    String args = (firstSpace == -1) ? "" : trimmed.substring(firstSpace + 1);

    switch (cmd) {
      case "LOGIN":
        if (args.isEmpty())
          return "ERROR: LOGIN requires username and password";
        int sp = args.indexOf(' ');
        if (sp == -1)
          return "ERROR: LOGIN requires username and password";
        String user = args.substring(0, sp);
        String pass = args.substring(sp + 1);
        boolean ok = store.authenticateExistingUser(user, pass);
        if (ok) {
          currentUser = user;
          return "OK";
        } else {
          return "ERROR: invalid credentials";
        }
      case "REGISTER":
        if (args.isEmpty())
          return "ERROR: REGISTER requires username and password";
        int spReg = args.indexOf(' ');
        if (spReg == -1)
          return "ERROR: REGISTER requires username and password";
        String regUser = args.substring(0, spReg);
        String regPass = args.substring(spReg + 1);
        // Use explicit createUser for clearer semantics
        boolean created = store.createUser(regUser, regPass);
        return created ? "OK" : "ERROR: user exists";
      case "SET_KEYWORD":
        if (args.isEmpty())
          return "ERROR: SET_KEYWORD requires keyword";
        if (currentUser == null)
          return "ERROR: not authenticated";
        String kw = args;
        boolean saved = store.setKeyword(currentUser, kw);
        return saved ? "OK" : "ERROR: could not save keyword";
      case "GET_KEYWORD":
        if (currentUser == null)
          return "ERROR: not authenticated";
        String k = store.getKeyword(currentUser);
        return k == null ? "NONE" : (k.isEmpty() ? "NONE" : ("KEYWORD " + k));
      case "PING":
        return "PONG";
      case "CHANGE_PASSWORD":
        if (currentUser == null)
          return "ERROR: not authenticated";
        if (args.isEmpty())
          return "ERROR: CHANGE_PASSWORD requires old and new password";
        int sp2 = args.indexOf(' ');
        if (sp2 == -1)
          return "ERROR: CHANGE_PASSWORD requires old and new password";
        String oldp = args.substring(0, sp2);
        String newp = args.substring(sp2 + 1);
        boolean changed = store.changePassword(currentUser, oldp, newp);
        return changed ? "OK" : "ERROR: password not changed";
      case "DELETE_ACCOUNT":
        if (currentUser == null)
          return "ERROR: not authenticated";
        boolean removed = store.deleteUser(currentUser);
        if (removed) {
          currentUser = null;
          return "OK";
        } else {
          return "ERROR: could not delete account";
        }
      case "LOGOUT":
        currentUser = null;
        return "OK";
      case "STOP":
        return "OK";
      case "PAUSE":
        return "OK";
      case "RESUME":
        return "OK";
      default:
        return songNameExpression.interpret(trimmed);
    }
  }
}
