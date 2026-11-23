import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Server {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5002;
        String folderName = args.length > 1 ? args[1] : "shared";
        File folder = new File(folderName);
        folder.mkdirs();

        ServerSocket server = new ServerSocket(port);
        System.out.println("Serving " + folder.getAbsolutePath() + " on port " + port);
        while (true) {
            Socket connection = server.accept();
            new Thread(() -> handleClient(connection, folder)).start();
        }
    }

    private static void handleClient(Socket connection, File folder) {
        try {
            InputStream input = connection.getInputStream();
            OutputStream output = connection.getOutputStream();
            if (!readLine(input).equals("rdy")) {
                connection.close();
                return;
            }
            sendLine(output, "rdy");

            if (!readLine(input).equals("fName")) {
                connection.close();
                return;
            }
            String fileName = readLine(input);
            File file = new File(folder, fileName);
            if (!file.getName().equals(fileName) || !file.isFile()) {
                sendLine(output, "error");
                connection.close();
                return;
            }

            sendLine(output, "fName");
            sendLine(output, fileName);
            sendLine(output, "fsize");
            sendLine(output, Long.toString(file.length()));
            if (!readLine(input).equals("rdyD")) {
                connection.close();
                return;
            }

            FileInputStream fileInput = new FileInputStream(file);
            byte[] buffer = new byte[65536];
            int bytesRead;
            while ((bytesRead = fileInput.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            output.flush();
            fileInput.close();

            if (readLine(input).equals("close")) {
                System.out.println("Sent " + fileName + " to " + connection.getInetAddress());
            }
            connection.close();
        } catch (Exception error) {
            System.out.println("Client connection ended: " + error.getMessage());
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static String readLine(InputStream input) throws Exception {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1 && value != '\n') {
            if (value != '\r') {
                line.write(value);
            }
        }
        if (value == -1 && line.size() == 0) {
            throw new Exception("Connection closed.");
        }
        return new String(line.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void sendLine(OutputStream output, String line) throws Exception {
        output.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }
}
