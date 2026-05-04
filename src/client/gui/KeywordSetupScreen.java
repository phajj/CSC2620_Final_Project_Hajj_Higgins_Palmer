package client.gui;

import client.network.ServerConnection;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.regex.Pattern;

public class KeywordSetupScreen {

	private JFrame frame;
	private JTextField keywordField;
	private JLabel messageLabel;
	private JLabel hintLabel;
	private String keyword = "";
	private ServerConnection serverConn;
	private String username;
    private boolean redirectToMain = false;
    private String initialKeyword = null;

	public static final Pattern KEYWORD_PATTERN = Pattern.compile("^[A-Za-z0-9 ]{1,32}$");

	public KeywordSetupScreen() {
		this(new ServerConnection(LoginScreen.DEFAULT_HOST, LoginScreen.DEFAULT_PORT), null, null, false);
	}

	public KeywordSetupScreen(ServerConnection conn) {
		this(conn, null, null, false);
	}

	public KeywordSetupScreen(ServerConnection conn, String username) {
		this(conn, username, null, false);
	}

	public KeywordSetupScreen(ServerConnection conn, String username, String initialKeyword, boolean redirectToMain) {
		this.serverConn = conn;
		this.username = username;
		this.initialKeyword = initialKeyword;
		this.redirectToMain = redirectToMain;
		initUI();
	}

	private void initUI() {
		frame = new JFrame("Keyword Setup");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setSize(400, 160);
		frame.setLocationRelativeTo(null);

		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(6, 8, 6, 8);
		gbc.fill = GridBagConstraints.HORIZONTAL;

		int row = 0;
		if (username != null) {
			gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
			JLabel userLabel = new JLabel("User: " + username);
			panel.add(userLabel, gbc);
			row++;
		}

		gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
		panel.add(new JLabel("Keyword:"), gbc);
		gbc.gridx = 1;
		keywordField = new JTextField(20);
		if (initialKeyword != null) keywordField.setText(initialKeyword);
		panel.add(keywordField, gbc);
		row++;

		// Inline hint showing validation rules
		gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
		hintLabel = new JLabel("Allowed: 1-32 chars — letters, digits, spaces");
		hintLabel.setForeground(Color.DARK_GRAY);
		panel.add(hintLabel, gbc);
		row++;

		gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
		messageLabel = new JLabel(" ");
		messageLabel.setForeground(Color.RED);
		panel.add(messageLabel, gbc);
		row++;

		JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton saveBtn = new JButton("Save");
		JButton backBtn = new JButton("Back");
		buttonRow.add(backBtn);
		buttonRow.add(saveBtn);

		gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
		panel.add(buttonRow, gbc);

		saveBtn.addActionListener(e -> onSave());
		backBtn.addActionListener(e -> {
			frame.dispose();
			new LoginScreen().display();
		});

		if (username != null) {
			JLabel userLabel = new JLabel("User: " + username);
			gbc.gridx = 0; gbc.gridy = -1; // add above (GridBag will normalize)
			panel.add(userLabel, gbc);
		}

		frame.getContentPane().add(panel);
	}

	public void display() {
		SwingUtilities.invokeLater(() -> {
			frame.setVisible(true);
			// copy suggested keyword to clipboard if provided
			if (initialKeyword != null && !initialKeyword.isEmpty()) {
				try {
					Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(initialKeyword), null);
					// show transient toast confirming copy
					JWindow toast = new JWindow(frame);
					JPanel tpanel = new JPanel(new BorderLayout());
					tpanel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
					tpanel.setBackground(new Color(0,0,0,0));
					JLabel msg = new JLabel("Keyword copied to clipboard");
					msg.setOpaque(true);
					msg.setBackground(new Color(60,63,65));
					msg.setForeground(Color.WHITE);
					msg.setBorder(BorderFactory.createEmptyBorder(8,12,8,12));
					tpanel.add(msg, BorderLayout.CENTER);
					toast.getContentPane().add(tpanel);
					toast.pack();
					Point loc = frame.getLocationOnScreen();
					int x = loc.x + frame.getWidth() - toast.getWidth() - 20;
					int y = loc.y + frame.getHeight() - toast.getHeight() - 20;
					toast.setLocation(x, y);
					toast.setAlwaysOnTop(true);
					toast.setVisible(true);
					new javax.swing.Timer(1500, ev -> toast.dispose()).start();
				} catch (Exception ignored) {
				}
			}
		});
	}

	private void onSave() {
		final String kw = keywordField.getText().trim();
		if (!KEYWORD_PATTERN.matcher(kw).matches()) {
			messageLabel.setText("Keyword must be 1-32 chars (letters, digits, spaces)");
			return;
		}

		messageLabel.setText("Saving...");

		new Thread(() -> {
			boolean connected = (serverConn != null) && serverConn.connect();
			if (connected) {
				String resp = serverConn.sendCommand("SET_KEYWORD " + kw);
					if (resp != null && (resp.equalsIgnoreCase("OK") || resp.toUpperCase().startsWith("OK") || resp.toUpperCase().startsWith("SUCCESS"))) {
					this.keyword = kw;
					SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Keyword saved on server.", "Saved", JOptionPane.INFORMATION_MESSAGE));
					if (redirectToMain) {
						SwingUtilities.invokeLater(() -> {
							frame.dispose();
							try { new MainScreen(serverConn, username, "Keyword saved.").display(); } catch (Exception ex) { }
						});
					}
				} else {
					final String err = (resp == null) ? "No response from server" : resp;
					SwingUtilities.invokeLater(() -> messageLabel.setText("Save failed: " + err));
				}
			} else {
				this.keyword = kw;
				SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Saved locally (server unavailable).", "Saved", JOptionPane.INFORMATION_MESSAGE));
				if (redirectToMain) {
					SwingUtilities.invokeLater(() -> {
						frame.dispose();
						try { new MainScreen(serverConn, username, "Keyword saved (offline).").display(); } catch (Exception ex) { }
					});
				}
			}

			SwingUtilities.invokeLater(() -> messageLabel.setText(" "));
		}).start();
	}

	public String getKeyword() {
		return keyword;
	}

	public void setKeyword(String keyword) {
		this.keyword = keyword;
		SwingUtilities.invokeLater(() -> keywordField.setText(keyword));
	}


}
