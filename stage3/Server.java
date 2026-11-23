import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Server {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5003;
        String folderName = args.length > 1 ? args[1] : "shared";
        int chunkCount = args.length > 2 ? Integer.parseInt(args[2]) : 100;
        if (chunkCount < 100) {
            throw new IllegalArgumentException("The chunk count must be at least 100.");
        }

        File folder = new File(folderName);
        folder.mkdirs();
        ServerSocket server = new ServerSocket(port);
        System.out.println("Serving " + folder.getAbsolutePath() + " on port " + port);
        while (true) {
            Socket connection = server.accept();
            new Thread(() -> handleClient(connection, folder, chunkCount)).start();
        }
    }

    private static void handleClient(Socket connection, File folder, int requestedChunks) {
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
            if (!file.getName().equals(fileName) || !file.isFile() || file.length() < requestedChunks) {
                sendLine(output, "error");
                connection.close();
                return;
            }

            List<byte[]> chunks = splitFile(file, requestedChunks);
            sendLine(output, "fName");
            sendLine(output, fileName);
            sendLine(output, "chunks");
            sendLine(output, Integer.toString(chunks.size()));
            if (!readLine(input).equals("rdyD")) {
                connection.close();
                return;
            }

            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                order.add(i);
            }
            Collections.shuffle(order);
            for (int position = 0; position < order.size(); position++) {
                int index = order.get(position);
                byte[] chunk = chunks.get(index);
                sendLine(output, Integer.toString(index));
                sendLine(output, Integer.toString(chunk.length));
                output.write(chunk);
                output.flush();
                if (position < order.size() - 1 && !readLine(input).equals("next")) {
                    connection.close();
                    return;
                }
            }

            if (readLine(input).equals("close")) {
                System.out.println("Sent " + chunks.size() + " chunks of " + fileName);
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

    private static List<byte[]> splitFile(File file, int count) throws Exception {
        List<byte[]> chunks = new ArrayList<>();
        int baseSize = (int) (file.length() / count);
        int extraBytes = (int) (file.length() % count);
        FileInputStream input = new FileInputStream(file);
        for (int index = 0; index < count; index++) {
            int size = baseSize + (index < extraBytes ? 1 : 0);
            byte[] chunk = new byte[size];
            int offset = 0;
            while (offset < size) {
                int amount = input.read(chunk, offset, size - offset);
                if (amount == -1) {
                    throw new Exception("Could not read the complete file.");
                }
                offset += amount;
            }
            chunks.add(chunk);
        }
        input.close();
        return chunks;
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
