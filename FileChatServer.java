import java.io.*;
import java.net.*;
import java.util.*;

public class FileChatServer {
    private static final int PORT = 12346;
    private static final Map<String, ClientHandler> userHandlers = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("Server is starting...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Broadcast updated user list to all clients
    private static synchronized void updateUserList() {
        String userList = "USERS:" + String.join(",", userHandlers.keySet());
        for (ClientHandler handler : userHandlers.values()) {
            handler.sendMessage(userList);
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private DataOutputStream writer;
        private DataInputStream reader;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                reader = new DataInputStream(socket.getInputStream());
                writer = new DataOutputStream(socket.getOutputStream());

                // Request username from client
                writer.writeUTF("Enter your username:");
                username = reader.readUTF();

                if (username == null || username.trim().isEmpty()) {
                    username = "Anonymous" + socket.getPort();
                }

                synchronized (userHandlers) {
                    userHandlers.put(username, this);
                }

                System.out.println(username + " has joined the chat.");
                updateUserList(); // Update all users about the new user

                while (true) {
                    String message = reader.readUTF();

                    if (message.startsWith("MSG:")) {
                        String[] parts = message.substring(4).split(":", 2);
                        String recipient = parts[0];
                        String msg = parts[1];

                        sendPrivateMessage(recipient, username + ": " + msg);
                    } else if (message.startsWith("FILE:")) {
                        String[] parts = message.split(":", 3);
                        String recipient = parts[1];
                        String fileName = parts[2];
                        receiveAndForwardFile(recipient, fileName);
                    }
                }
            } catch (IOException e) {
                System.out.println(username + " has disconnected.");
            } finally {
                synchronized (userHandlers) {
                    userHandlers.remove(username);
                }
                updateUserList(); // Update user list after someone leaves
            }
        }

        // Send private message to a specific user
        private void sendPrivateMessage(String recipient, String message) {
            ClientHandler recipientHandler = userHandlers.get(recipient);
            if (recipientHandler != null) {
                recipientHandler.sendMessage(message);
            } else {
                sendMessage("User " + recipient + " is not online.");
            }
        }

        // Receive file from client and forward it to recipient
        private void receiveAndForwardFile(String recipient, String fileName) throws IOException {
            ClientHandler recipientHandler = userHandlers.get(recipient);
            if (recipientHandler != null) {
                recipientHandler.sendMessage("FILE:" + username + ":" + fileName);

                long fileSize = reader.readLong(); // Get file size
                recipientHandler.sendFile(fileName, fileSize, reader);
            } else {
                sendMessage("User " + recipient + " is not online.");
            }
        }

        public void sendFile(String fileName, long fileSize, DataInputStream senderReader) throws IOException {
            writer.writeUTF("FILE:" + fileName);
            writer.writeLong(fileSize);

            byte[] buffer = new byte[1024];
            long bytesReceived = 0;
            while (bytesReceived < fileSize) {
                int bytesRead = senderReader.read(buffer, 0, Math.min(buffer.length, (int) (fileSize - bytesReceived)));
                writer.write(buffer, 0, bytesRead);
                bytesReceived += bytesRead;
            }
            writer.flush();
        }

        public void sendMessage(String message) {
            try {
                writer.writeUTF(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
