import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class Tracker {
    private static class PeerRecord {
        String peerId;
        Socket connection;
        List<Integer> chunkIds = new ArrayList<>();

        PeerRecord(String peerId, Socket connection) {
            this.peerId = peerId;
            this.connection = connection;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java Tracker <source-file> [port] [number-of-peers]");
            return;
        }

        File sourceFile = new File(args[0]);
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9000;
        int peerCount = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        if (!sourceFile.isFile()) {
            throw new Exception("Source file not found: " + sourceFile);
        }
        if (peerCount < 2) {
            throw new Exception("At least two peers are required.");
        }

        File chunkFolder = new File("tracker_chunks");
        int totalChunks = splitFile(sourceFile, chunkFolder);
        if (totalChunks < 250) {
            throw new Exception("The file must contain at least 250 full 10 KB chunks.");
        }
        System.out.println("Split " + sourceFile.getName() + " into " + totalChunks + " chunks.");

        List<PeerRecord> peers = new ArrayList<>();
        CountDownLatch peersReady = new CountDownLatch(1);
        ServerSocket server = new ServerSocket(port);
        System.out.println("Tracker listening on port " + port);

        while (true) {
            Socket connection = server.accept();
            Thread peerThread = new Thread(() -> handlePeer(
                    connection, sourceFile.getName(), totalChunks, peerCount,
                    chunkFolder, peers, peersReady));
            peerThread.start();
        }
    }

    private static int splitFile(File sourceFile, File chunkFolder) throws Exception {
        chunkFolder.mkdirs();
        File[] oldChunks = chunkFolder.listFiles();
        if (oldChunks != null) {
            for (File file : oldChunks) {
                if (file.getName().startsWith("chunk_") && file.getName().endsWith(".bin")) {
                    file.delete();
                }
            }
        }

        FileInputStream input = new FileInputStream(sourceFile);
        byte[] buffer = new byte[Protocol.CHUNK_SIZE];
        int chunkId = 0;
        int amount;
        while ((amount = input.read(buffer)) != -1) {
            FileOutputStream output = new FileOutputStream(Protocol.chunkFile(chunkFolder, chunkId));
            output.write(buffer, 0, amount);
            output.close();
            chunkId++;
        }
        input.close();
        return chunkId;
    }

    private static void handlePeer(Socket connection, String fileName, int totalChunks,
            int peerCount, File chunkFolder, List<PeerRecord> peers,
            CountDownLatch peersReady) {
        try {
            DataInputStream input = new DataInputStream(connection.getInputStream());
            DataOutputStream output = new DataOutputStream(connection.getOutputStream());
            if (!Protocol.readLine(input).equals("rdy")) {
                connection.close();
                return;
            }
            Protocol.sendLine(output, "rdy");
            if (!Protocol.readLine(input).equals("fName")) {
                connection.close();
                return;
            }
            String peerId = Protocol.readLine(input);
            PeerRecord peer = new PeerRecord(peerId, connection);

            synchronized (peers) {
                if (peers.size() >= peerCount) {
                    Protocol.sendLine(output, "error");
                    connection.close();
                    return;
                }
                peers.add(peer);
                System.out.println("Peer " + peerId + " connected (" + peers.size() + "/" + peerCount + ")");

                if (peers.size() == peerCount) {
                    assignChunks(peers, totalChunks);
                    peersReady.countDown();
                }
            }

            peersReady.await();
            sendChunks(peer, input, output, fileName, totalChunks, chunkFolder);
        } catch (Exception error) {
            System.out.println("Peer connection ended: " + error.getMessage());
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void assignChunks(List<PeerRecord> peers, int totalChunks) {
        List<Integer> chunkIds = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            chunkIds.add(i);
        }
        Collections.shuffle(chunkIds, new Random());
        for (int i = 0; i < chunkIds.size(); i++) {
            peers.get(i % peers.size()).chunkIds.add(chunkIds.get(i));
        }
        for (PeerRecord peer : peers) {
            Collections.sort(peer.chunkIds);
        }
    }

    private static void sendChunks(PeerRecord peer, DataInputStream input,
            DataOutputStream output, String fileName, int totalChunks, File chunkFolder)
            throws Exception {
        Protocol.sendLine(output, "fName");
        Protocol.sendLine(output, fileName);
        Protocol.sendLine(output, "chunks");
        Protocol.sendLine(output, Integer.toString(totalChunks));
        Protocol.sendLine(output, "assigned");
        Protocol.sendLine(output, Integer.toString(peer.chunkIds.size()));
        if (!Protocol.readLine(input).equals("rdyD")) {
            throw new Exception("Peer was not ready for its chunks.");
        }

        for (int position = 0; position < peer.chunkIds.size(); position++) {
            int chunkId = peer.chunkIds.get(position);
            FileInputStream chunkInput = new FileInputStream(Protocol.chunkFile(chunkFolder, chunkId));
            byte[] data = new byte[(int) Protocol.chunkFile(chunkFolder, chunkId).length()];
            int offset = 0;
            while (offset < data.length) {
                int amount = chunkInput.read(data, offset, data.length - offset);
                if (amount == -1) {
                    throw new Exception("Could not read chunk " + chunkId);
                }
                offset += amount;
            }
            chunkInput.close();

            Protocol.sendLine(output, Integer.toString(chunkId));
            Protocol.sendLine(output, Integer.toString(data.length));
            output.write(data);
            output.flush();
            System.out.println("Sent chunk " + chunkId + " to peer " + peer.peerId);
            if (position < peer.chunkIds.size() - 1 && !Protocol.readLine(input).equals("next")) {
                throw new Exception("Peer did not request the next chunk.");
            }
        }

        if (Protocol.readLine(input).equals("close")) {
            System.out.println("Finished initial transfer to peer " + peer.peerId);
        }
        peer.connection.close();
    }
}
