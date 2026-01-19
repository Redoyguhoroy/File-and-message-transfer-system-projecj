import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class FileChatClient {
    private Socket socket;
    private DataInputStream reader;
    private DataOutputStream writer;

    private JFrame frame;
    private JPanel chatPanel;
    private JTextField messageField;
    private JButton sendButton, fileButton;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;

    public FileChatClient(String serverIP, int serverPort) {
        // Prompt for username at the top
        String username = JOptionPane.showInputDialog(null, "Enter your username:");
        if (username == null || username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "Username cannot be empty. Exiting.");
            System.exit(0);
        }

        setupGUI();

        try {
            socket = new Socket(serverIP, serverPort);
            reader = new DataInputStream(socket.getInputStream());
            writer = new DataOutputStream(socket.getOutputStream());

            // Send username to server
            writer.writeUTF(username);

            // Start a thread to handle incoming messages
            new Thread(this::handleServerResponses).start();
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Unable to connect to server.");
        }
    }

    private void setupGUI() {
        frame = new JFrame("Chat and File Transfer Application");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout());

        // Chat panel with vertical layout, no extra space between messages
        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(Color.WHITE);
        chatPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0)); // No gap between messages

        // Add a scroll pane to the chat panel
        JScrollPane chatScrollPane = new JScrollPane(chatPanel);
        mainPanel.add(chatScrollPane, BorderLayout.CENTER);

        // Right panel for user list
        JPanel rightPanel = new JPanel(new BorderLayout());
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        JScrollPane userScrollPane = new JScrollPane(userList);
        rightPanel.add(new JLabel("Online Users:"), BorderLayout.NORTH);
        rightPanel.add(userScrollPane, BorderLayout.CENTER);

        mainPanel.add(rightPanel, BorderLayout.EAST);

        // Input panel for message field and buttons
        JPanel inputPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        messageField.setFont(new Font("Arial", Font.PLAIN, 14));

        sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());

        fileButton = new JButton("Send File");
        fileButton.addActionListener(e -> sendFile());

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        inputPanel.add(fileButton, BorderLayout.WEST);

        mainPanel.add(inputPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setVisible(true);
    }

    private void handleServerResponses() {
        try {
            while (true) {
                String message = reader.readUTF();
                if (message.startsWith("USERS:")) {
                    updateUserList(message.substring(6));
                } else if (message.startsWith("FILE:")) {
                    handleIncomingFile();
                } else {
                    addChatMessage(message, null);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateUserList(String users) {
        String[] userArray = users.split(",");
        userListModel.clear();
        for (String user : userArray) {
            userListModel.addElement(user);
        }
    }

    private void sendMessage() {
        try {
            String message = messageField.getText();
            String recipient = userList.getSelectedValue();

            if (recipient == null) {
                JOptionPane.showMessageDialog(frame, "Please select a user to send the message.");
                return;
            }

            if (message == null || message.trim().isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Message cannot be empty.");
                return;
            }

            writer.writeUTF("MSG:" + recipient + ":" + message);
            addChatMessage("Me to " + recipient + ": " + message, null);
            messageField.setText("");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendFile() {
        try {
            String recipient = userList.getSelectedValue();

            if (recipient == null) {
                JOptionPane.showMessageDialog(frame, "Please select a user to send the file.");
                return;
            }

            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                writer.writeUTF("FILE:" + recipient + ":" + file.getName());
                writer.writeLong(file.length());

                try (FileInputStream fileInput = new FileInputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fileInput.read(buffer)) > 0) {
                        writer.write(buffer, 0, bytesRead);
                    }
                }

                addChatMessage("Me to " + recipient + ": Sent file " + file.getName(), null);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleIncomingFile() {
        try {
            // Read file details
            String fileName = reader.readUTF();
            long fileSize = reader.readLong();

            JButton downloadButton = new JButton("Download");
            downloadButton.addActionListener(e -> downloadFile(fileName, fileSize));

            addChatMessage("Received file: " + fileName, downloadButton);
        } catch (IOException e) {
            addChatMessage("Error receiving a file.", null);
            e.printStackTrace();
        }
    }

    private void downloadFile(String fileName, long fileSize) {
        try {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(fileName)); // Default file name

            int userChoice = fileChooser.showSaveDialog(frame);
            if (userChoice != JFileChooser.APPROVE_OPTION) {
                return; // User canceled download
            }

            File saveFile = fileChooser.getSelectedFile();
            try (FileOutputStream fileOutput = new FileOutputStream(saveFile)) {
                byte[] buffer = new byte[1024];
                long bytesReceived = 0;

                while (bytesReceived < fileSize) {
                    int bytesRead = reader.read(buffer, 0, (int) Math.min(buffer.length, fileSize - bytesReceived));
                    fileOutput.write(buffer, 0, bytesRead);
                    bytesReceived += bytesRead;
                }

                addChatMessage("File saved to: " + saveFile.getAbsolutePath(), null);
            }
        } catch (IOException e) {
            addChatMessage("Error saving the file.", null);
            e.printStackTrace();
        }
    }

    private void addChatMessage(String message, JButton button) {
        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1)); // Adds spacing around messages

        JLabel messageLabel = new JLabel(message);
        messageLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        messagePanel.add(messageLabel, BorderLayout.CENTER);

        if (button != null) {
            messagePanel.add(button, BorderLayout.EAST);
        }

        chatPanel.add(messagePanel);
        chatPanel.revalidate();
        chatPanel.repaint();

        // Automatically scroll to the bottom
        JScrollPane parentScrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, chatPanel);
        if (parentScrollPane != null) {
            JScrollBar verticalScrollBar = parentScrollPane.getVerticalScrollBar();
            verticalScrollBar.setValue(verticalScrollBar.getMaximum());
        }
    }

    public static void main(String[] args) {
        String serverIP = JOptionPane.showInputDialog(null, "Enter Server IP Address:");
        if (serverIP == null || serverIP.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "IP Address cannot be empty.");
            System.exit(0);
        }
        int serverPort = 12346;
        new FileChatClient(serverIP, serverPort);
    }
}
