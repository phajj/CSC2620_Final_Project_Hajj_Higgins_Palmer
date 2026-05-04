package client.gui;

import client.network.ServerConnection;
import javax.swing.*;
import java.awt.*;
import java.util.regex.Pattern;

public class LoginScreen {

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 5555;

    private JFrame frame;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JLabel messageLabel;
    private JLabel connStatusLabel;
    private ServerConnection serverConn;

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.{6,}$)(?=.*[A-Za-z])(?=.*\\d).*$");

    public LoginScreen() {
        serverConn = new ServerConnection(DEFAULT_HOST, DEFAULT_PORT);
        initUI();
    }

    private void initUI() {
        frame = new JFrame("Login");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 220);
        frame.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        usernameField = new JTextField(16);
        panel.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        passwordField = new JPasswordField(16);
        panel.add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        messageLabel = new JLabel(" ");
        messageLabel.setForeground(Color.RED);
        panel.add(messageLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        connStatusLabel = new JLabel("Disconnected");
        connStatusLabel.setForeground(Color.DARK_GRAY);
        panel.add(connStatusLabel, gbc);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton submitBtn = new JButton("Login");
        JButton registerBtn = new JButton("Register");
        JButton setupBtn = new JButton("Setup Keywords");
        buttonRow.add(setupBtn);
        buttonRow.add(registerBtn);
        buttonRow.add(submitBtn);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        panel.add(buttonRow, gbc);

        // Reconnect button next to setup
        JButton reconnectBtn = new JButton("Reconnect");
        reconnectBtn.addActionListener(e -> attemptConnect());
        buttonRow.add(reconnectBtn);

        submitBtn.addActionListener(e -> onSubmit());
        setupBtn.addActionListener(e -> openKeywordSetup());
        registerBtn.addActionListener(e -> openRegisterDialog());

        frame.getContentPane().add(panel);
    }

    public void display() {
        SwingUtilities.invokeLater(() -> {
            frame.setVisible(true);
            attemptConnect();
        });
    }

    private boolean validateInputs() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (!USERNAME_PATTERN.matcher(username).matches()) {
            messageLabel.setText("Username must be 3-16 chars: letters, digits or _");
            return false;
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            messageLabel.setText("Password must be ≥6 chars and include letters and digits");
            return false;
        }
        messageLabel.setText(" ");
        return true;
    }

    private void onSubmit() {
        if (!validateInputs()) return;

        final String username = usernameField.getText().trim();
        final String password = new String(passwordField.getPassword());
        messageLabel.setText("Connecting to server...");

        new Thread(() -> {
            boolean connected = serverConn.connect();
            SwingUtilities.invokeLater(() -> updateConnStatus(connected));
            if (!connected) {
                SwingUtilities.invokeLater(() -> messageLabel.setText("Unable to connect to server."));
                return;
            }

            String cmd = "LOGIN " + username + " " + password;
            String resp = serverConn.sendCommand(cmd);

            if (resp != null && (resp.equalsIgnoreCase("OK") || resp.toUpperCase().startsWith("OK") || resp.toUpperCase().startsWith("SUCCESS"))) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame, "Login successful.", "Login", JOptionPane.INFORMATION_MESSAGE);
                    frame.dispose();
                    try {
                        MainScreen main = new MainScreen(serverConn, username);
                        main.display();
                    } catch (Exception ex) {
                        // ignore
                    }
                });
            } else {
                final String err = (resp == null) ? "No response from server" : resp;
                SwingUtilities.invokeLater(() -> messageLabel.setText("Login failed: " + err));
            }
        }).start();
    }

    private void attemptConnect() {
        connStatusLabel.setText("Connecting...");
        new Thread(() -> {
            boolean ok = serverConn.connect();
            SwingUtilities.invokeLater(() -> updateConnStatus(ok));
        }).start();
    }

    private void updateConnStatus(boolean connected) {
        if (connected) {
            connStatusLabel.setText("Connected to " + DEFAULT_HOST + ":" + DEFAULT_PORT);
            connStatusLabel.setForeground(new Color(0, 128, 0));
        } else {
            connStatusLabel.setText("Disconnected");
            connStatusLabel.setForeground(Color.DARK_GRAY);
        }
    }

    private void openKeywordSetup() {
        frame.dispose();
        KeywordSetupScreen k = new KeywordSetupScreen(serverConn);
        k.display();
    }

    private void openRegisterDialog() {
        JTextField regUser = new JTextField();
        JPasswordField regPass = new JPasswordField();
        JPasswordField regConfirm = new JPasswordField();
        Object[] inputs = {
                "Username:", regUser,
                "Password:", regPass,
                "Confirm Password:", regConfirm
        };
        int result = JOptionPane.showConfirmDialog(frame, inputs, "Register", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        final String user = regUser.getText().trim();
        final String pass = new String(regPass.getPassword());
        final String confirm = new String(regConfirm.getPassword());

        if (!USERNAME_PATTERN.matcher(user).matches()) {
            JOptionPane.showMessageDialog(frame, "Username must be 3-16 chars: letters, digits or _", "Invalid", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!PASSWORD_PATTERN.matcher(pass).matches()) {
            JOptionPane.showMessageDialog(frame, "Password must be ≥6 chars and include letters and digits", "Invalid", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!pass.equals(confirm)) {
            JOptionPane.showMessageDialog(frame, "Passwords do not match", "Invalid", JOptionPane.ERROR_MESSAGE);
            return;
        }

        messageLabel.setText("Registering...");
        new Thread(() -> {
            boolean connected = serverConn.connect();
            SwingUtilities.invokeLater(() -> updateConnStatus(connected));
            if (!connected) {
                SwingUtilities.invokeLater(() -> messageLabel.setText("Unable to connect to server."));
                return;
            }

            String cmd = "REGISTER " + user + " " + pass;
            String resp = serverConn.sendCommand(cmd);
            if (resp != null && resp.toUpperCase().startsWith("OK")) {
                // auto-login after register
                String loginResp = serverConn.sendCommand("LOGIN " + user + " " + pass);
                if (loginResp != null && loginResp.toUpperCase().startsWith("OK")) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(frame, "Registered and logged in. Please set your keyword.", "Success", JOptionPane.INFORMATION_MESSAGE);
                        frame.dispose();
                        try {
                            String suggested = user + "kw";
                            String input = suggested;
                            // Prompt and validate with inline hint; re-prompt if invalid. Cancel returns to login.
                            while (true) {
                                JPanel panel = new JPanel(new BorderLayout(8, 8));
                                JPanel labels = new JPanel(new GridLayout(0, 1));
                                labels.add(new JLabel("Choose a keyword (1-32 chars):"));
                                labels.add(new JLabel("Allowed characters: letters, digits, spaces"));
                                panel.add(labels, BorderLayout.WEST);
                                JTextField dialogField = new JTextField(input, 20);
                                panel.add(dialogField, BorderLayout.CENTER);

                                int dlg = JOptionPane.showConfirmDialog(frame, panel, "Choose Keyword", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                                if (dlg != JOptionPane.OK_OPTION) {
                                    input = "";
                                    break;
                                }
                                input = dialogField.getText().trim();
                                if (KeywordSetupScreen.KEYWORD_PATTERN.matcher(input).matches()) break;
                                JOptionPane.showMessageDialog(frame, "Invalid keyword. Use 1-32 letters, digits or spaces.", "Invalid", JOptionPane.ERROR_MESSAGE);
                            }
                            String initial = input;
                            KeywordSetupScreen k = new KeywordSetupScreen(serverConn, user, initial, true);
                            k.display();
                        } catch (Exception ex) {
                            // ignore
                        }
                    });
                } else {
                    final String err = (loginResp == null) ? "No response from server" : loginResp;
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Registered but login failed: " + err, "Warning", JOptionPane.WARNING_MESSAGE));
                }
            } else {
                final String err = (resp == null) ? "No response from server" : resp;
                SwingUtilities.invokeLater(() -> messageLabel.setText("Register failed: " + err));
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginScreen().display());
    }
}
