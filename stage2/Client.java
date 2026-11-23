import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Client {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java Client <server-ip> <port> <file-name> [download-folder]");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String fileName = args[2];
        String downloadFolder = args.length > 3 ? args[3] : "downloads";
        if (!new File(fileName).getName().equals(fileName)) {
            System.out.println("Enter only the file name, not a folder path.");
            return;
        }

        File outputFolder = new File(downloadFolder);
        outputFolder.mkdirs();
        Socket connection = new Socket(host, port);
        InputStream input = connection.getInputStream();
        OutputStream output = connection.getOutputStream();
        sendLine(output, "rdy");
        if (!readLine(input).equals("rdy")) {
            throw new Exception("Server did not accept the connection.");
        }
        sendLine(output, "fName");
        sendLine(output, fileName);
        String response = readLine(input);
        if (response.equals("error")) {
            throw new Exception("File not found on the server.");
        }
        if (!response.equals("fName")) {
            throw new Exception("Unexpected file name response.");
        }
        String receivedName = readLine(input);
        if (!readLine(input).equals("fsize")) {
            throw new Exception("Unexpected file size response.");
        }
        long fileSize = Long.parseLong(readLine(input));
        sendLine(output, "rdyD");

        File destination = new File(outputFolder, receivedName);
        FileOutputStream fileOutput = new FileOutputStream(destination);
        byte[] buffer = new byte[65536];
        long remaining = fileSize;
        while (remaining > 0) {
            int amount = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (amount == -1) {
                throw new Exception("Server disconnected before the file was complete.");
            }
            fileOutput.write(buffer, 0, amount);
            remaining -= amount;
        }
        fileOutput.close();
        sendLine(output, "close");
        connection.close();
        System.out.println("Downloaded " + receivedName + " (" + fileSize + " bytes) to " + destination);
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
