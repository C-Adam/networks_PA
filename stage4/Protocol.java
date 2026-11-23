import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Protocol {
    public static final int CHUNK_SIZE = 10 * 1024;

    public static void sendLine(DataOutputStream output, String line) throws Exception {
        output.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    public static String readLine(DataInputStream input) throws Exception {
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

    public static byte[] readExact(DataInputStream input, int size) throws Exception {
        byte[] data = new byte[size];
        input.readFully(data);
        return data;
    }

    public static File chunkFile(File folder, int chunkId) {
        return new File(folder, String.format("chunk_%06d.bin", chunkId));
    }

    public static List<Integer> getChunkIds(File folder) {
        List<Integer> ids = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) {
            return ids;
        }
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("chunk_") && name.endsWith(".bin")) {
                String number = name.substring(6, name.length() - 4);
                try {
                    ids.add(Integer.parseInt(number));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        Collections.sort(ids);
        return ids;
    }

    public static String makeIdList(List<Integer> ids) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                text.append(",");
            }
            text.append(ids.get(i));
        }
        return text.toString();
    }

    public static List<Integer> parseIdList(String text) {
        List<Integer> ids = new ArrayList<>();
        if (text.isEmpty()) {
            return ids;
        }
        String[] parts = text.split(",");
        for (String part : parts) {
            ids.add(Integer.parseInt(part));
        }
        return ids;
    }

    public static void writeSummary(File peerFolder, File chunkFolder, String peerId, int totalChunks)
            throws Exception {
        List<Integer> ids = getChunkIds(chunkFolder);
        File summaryFile = new File(peerFolder, "summary.txt");
        FileOutputStream output = new FileOutputStream(summaryFile);
        String text = "Peer: " + peerId + "\n"
                + "Total file chunks: " + totalChunks + "\n"
                + "Chunks held (" + ids.size() + "):\n"
                + makeIdList(ids) + "\n";
        output.write(text.getBytes(StandardCharsets.UTF_8));
        output.close();
    }

    public static boolean assembleFile(File chunkFolder, int totalChunks, File outputFile)
            throws Exception {
        List<Integer> ids = getChunkIds(chunkFolder);
        if (ids.size() != totalChunks) {
            return false;
        }
        for (int i = 0; i < totalChunks; i++) {
            if (ids.get(i) != i) {
                return false;
            }
        }

        File parent = outputFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        FileOutputStream output = new FileOutputStream(outputFile);
        byte[] buffer = new byte[65536];
        for (int i = 0; i < totalChunks; i++) {
            FileInputStream chunkInput = new FileInputStream(chunkFile(chunkFolder, i));
            int amount;
            while ((amount = chunkInput.read(buffer)) != -1) {
                output.write(buffer, 0, amount);
            }
            chunkInput.close();
        }
        output.close();
        return true;
    }
}
