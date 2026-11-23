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

        new File(downloadFolder).mkdirs();
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
            throw new Exception("File not found or too small for the requested chunks.");
        }
        if (!response.equals("fName")) {
            throw new Exception("Unexpected file name response.");
        }
        String receivedName = readLine(input);
        if (!readLine(input).equals("chunks")) {
            throw new Exception("Unexpected chunk count response.");
        }
        int chunkCount = Integer.parseInt(readLine(input));
        sendLine(output, "rdyD");

        byte[][] chunks = new byte[chunkCount][];
        for (int position = 0; position < chunkCount; position++) {
            int index = Integer.parseInt(readLine(input));
            int size = Integer.parseInt(readLine(input));
            chunks[index] = readExact(input, size);
            System.out.println("Received chunk " + (position + 1) + "/" + chunkCount);
            if (position < chunkCount - 1) {
                sendLine(output, "next");
            }
        }

        File destination = new File(downloadFolder, receivedName);
        FileOutputStream fileOutput = new FileOutputStream(destination);
        for (byte[] chunk : chunks) {
            fileOutput.write(chunk);
        }
        fileOutput.close();
        sendLine(output, "close");
        connection.close();
        System.out.println("Reassembled file at " + destination.getPath());
    }

    private static byte[] readExact(InputStream input, int size) throws Exception {
        byte[] data = new byte[size];
        int offset = 0;
        while (offset < size) {
            int amount = input.read(data, offset, size - offset);
            if (amount == -1) {
                throw new Exception("Server disconnected during a chunk.");
            }
            offset += amount;
        }
        return data;
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
