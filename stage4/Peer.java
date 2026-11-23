import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Peer {
    private static class TransferInfo {
        String fileName;
        int totalChunks;

        TransferInfo(String fileName, int totalChunks) {
            this.fileName = fileName;
            this.totalChunks = totalChunks;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            System.out.println("Usage: java Peer <peer-id> <tracker-ip> <tracker-port> "
                    + "<upload-port> <download-neighbor-ip> <download-neighbor-port> "
                    + "[peer-folder] [output-folder]");
            return;
        }

        String peerId = args[0];
        String trackerHost = args[1];
        int trackerPort = Integer.parseInt(args[2]);
        int uploadPort = Integer.parseInt(args[3]);
        String downloadHost = args[4];
        int downloadPort = Integer.parseInt(args[5]);
        File peerFolder = new File(args.length > 6 ? args[6] : "peer_" + peerId);
        File outputFolder = new File(args.length > 7 ? args[7] : "completed");
        File chunkFolder = new File(peerFolder, "chunks");
        chunkFolder.mkdirs();
        removeOldChunks(chunkFolder);

        TransferInfo info = receiveFromTracker(peerId, trackerHost, trackerPort, peerFolder, chunkFolder);
        UploadServer uploadServer = new UploadServer(uploadPort, chunkFolder);
        Thread uploadThread = new Thread(uploadServer);
        uploadThread.start();

        Exception[] downloadError = new Exception[1];
        Thread downloadThread = new Thread(() -> {
            try {
                downloadAllChunks(peerId, downloadHost, downloadPort, chunkFolder,
                        peerFolder, outputFolder, info);
            } catch (Exception error) {
                downloadError[0] = error;
                System.out.println("Download failed: " + error.getMessage());
                uploadServer.stop();
            }
        });
        downloadThread.start();
        downloadThread.join();
        if (downloadError[0] != null) {
            uploadThread.join();
            throw downloadError[0];
        }
        uploadThread.join();
        System.out.println("Peer " + peerId + " finished.");
    }

    private static void removeOldChunks(File chunkFolder) {
        File[] files = chunkFolder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.getName().startsWith("chunk_") && file.getName().endsWith(".bin")) {
                file.delete();
            }
        }
    }

    private static TransferInfo receiveFromTracker(String peerId, String host, int port,
            File peerFolder, File chunkFolder) throws Exception {
        Socket connection = new Socket(host, port);
        DataInputStream input = new DataInputStream(connection.getInputStream());
        DataOutputStream output = new DataOutputStream(connection.getOutputStream());
        Protocol.sendLine(output, "rdy");
        if (!Protocol.readLine(input).equals("rdy")) {
            throw new Exception("Tracker did not accept the peer.");
        }
        Protocol.sendLine(output, "fName");
        Protocol.sendLine(output, peerId);
        String response = Protocol.readLine(input);
        if (response.equals("error")) {
            throw new Exception("Tracker already has the requested number of peers.");
        }
        if (!response.equals("fName")) {
            throw new Exception("Unexpected response from tracker.");
        }
        String fileName = Protocol.readLine(input);
        if (!Protocol.readLine(input).equals("chunks")) {
            throw new Exception("Tracker did not send the total chunk count.");
        }
        int totalChunks = Integer.parseInt(Protocol.readLine(input));
        if (!Protocol.readLine(input).equals("assigned")) {
            throw new Exception("Tracker did not send the assigned chunk count.");
        }
        int assignedChunks = Integer.parseInt(Protocol.readLine(input));
        Protocol.sendLine(output, "rdyD");

        for (int position = 0; position < assignedChunks; position++) {
            int chunkId = Integer.parseInt(Protocol.readLine(input));
            int size = Integer.parseInt(Protocol.readLine(input));
            byte[] data = Protocol.readExact(input, size);
            FileOutputStream chunkOutput = new FileOutputStream(Protocol.chunkFile(chunkFolder, chunkId));
            chunkOutput.write(data);
            chunkOutput.close();
            System.out.println("Received tracker chunk " + chunkId);
            if (position < assignedChunks - 1) {
                Protocol.sendLine(output, "next");
            }
        }
        Protocol.sendLine(output, "close");
        connection.close();
        Protocol.writeSummary(peerFolder, chunkFolder, peerId, totalChunks);
        System.out.println("Peer " + peerId + " has " + Protocol.getChunkIds(chunkFolder).size()
                + "/" + totalChunks + " chunks after tracker transfer.");
        return new TransferInfo(fileName, totalChunks);
    }

    private static class UploadServer implements Runnable {
        private final int port;
        private final File chunkFolder;
        private ServerSocket server;

        UploadServer(int port, File chunkFolder) {
            this.port = port;
            this.chunkFolder = chunkFolder;
        }

        public void run() {
            try {
                server = new ServerSocket(port);
                System.out.println("Upload server listening on port " + port);
                while (true) {
                    Socket connection = server.accept();
                    boolean neighborDone = serveNeighbor(connection);
                    if (neighborDone) {
                        server.close();
                        return;
                    }
                }
            } catch (Exception error) {
                System.out.println("Upload server stopped: " + error.getMessage());
            }
        }

        public void stop() {
            try {
                if (server != null) {
                    server.close();
                }
            } catch (Exception ignored) {
            }
        }

        private boolean serveNeighbor(Socket connection) {
            try {
                DataInputStream input = new DataInputStream(connection.getInputStream());
                DataOutputStream output = new DataOutputStream(connection.getOutputStream());
                if (!Protocol.readLine(input).equals("rdy")) {
                    connection.close();
                    return false;
                }
                Protocol.sendLine(output, "rdy");
                System.out.println("Accepted upload neighbor " + connection.getInetAddress());

                while (true) {
                    String command = Protocol.readLine(input);
                    if (command.equals("chunkIDList")) {
                        sendChunkList(output);
                    } else if (command.equals("rdyD")) {
                        boolean done = serveRequests(input, output);
                        if (done) {
                            connection.close();
                            return true;
                        }
                    } else if (command.equals("done")) {
                        System.out.println("Upload neighbor has all chunks.");
                        connection.close();
                        return true;
                    } else if (command.equals("close")) {
                        connection.close();
                        return false;
                    } else {
                        throw new Exception("Unknown peer command: " + command);
                    }
                }
            } catch (Exception error) {
                System.out.println("Upload neighbor disconnected: " + error.getMessage());
                try {
                    connection.close();
                } catch (Exception ignored) {
                }
                return false;
            }
        }

        private boolean serveRequests(DataInputStream input, DataOutputStream output) throws Exception {
            while (true) {
                String request = Protocol.readLine(input);
                if (request.equals("index")) {
                    int chunkId = Integer.parseInt(Protocol.readLine(input));
                    File chunkFile = Protocol.chunkFile(chunkFolder, chunkId);
                    if (!chunkFile.isFile()) {
                        Protocol.sendLine(output, "error");
                        continue;
                    }
                    byte[] data = new byte[(int) chunkFile.length()];
                    java.io.FileInputStream chunkInput = new java.io.FileInputStream(chunkFile);
                    int offset = 0;
                    while (offset < data.length) {
                        int amount = chunkInput.read(data, offset, data.length - offset);
                        if (amount == -1) {
                            throw new Exception("Could not read chunk " + chunkId);
                        }
                        offset += amount;
                    }
                    chunkInput.close();
                    Protocol.sendLine(output, Integer.toString(data.length));
                    output.write(data);
                    output.flush();
                    System.out.println("Received request for chunk " + chunkId);
                    System.out.println("Sent chunk " + chunkId);
                } else if (request.equals("chunkIDList")) {
                    sendChunkList(output);
                    return false;
                } else if (request.equals("done")) {
                    System.out.println("Upload neighbor has all chunks.");
                    return true;
                } else if (request.equals("close")) {
                    return false;
                } else {
                    throw new Exception("Unknown chunk request: " + request);
                }
            }
        }

        private void sendChunkList(DataOutputStream output) throws Exception {
            List<Integer> ids = Protocol.getChunkIds(chunkFolder);
            Protocol.sendLine(output, "list");
            Protocol.sendLine(output, Protocol.makeIdList(ids));
            System.out.println("Sent chunk ID list (" + ids.size() + " chunks)");
        }
    }

    private static void downloadAllChunks(String peerId, String host, int port,
            File chunkFolder, File peerFolder, File outputFolder, TransferInfo info)
            throws Exception {
        Socket connection = null;
        while (connection == null) {
            try {
                connection = new Socket(host, port);
            } catch (Exception error) {
                System.out.println("Waiting for download neighbor at " + host + ":" + port);
                Thread.sleep(2000);
            }
        }

        DataInputStream input = new DataInputStream(connection.getInputStream());
        DataOutputStream output = new DataOutputStream(connection.getOutputStream());
        Protocol.sendLine(output, "rdy");
        if (!Protocol.readLine(input).equals("rdy")) {
            throw new Exception("Download neighbor did not accept the connection.");
        }

        while (true) {
            Protocol.sendLine(output, "chunkIDList");
            System.out.println("Sent chunk ID list request");
            List<Integer> neighborIds = readChunkList(input);
            List<Integer> ownIds = Protocol.getChunkIds(chunkFolder);
            List<Integer> missingIds = new ArrayList<>();
            for (int id : neighborIds) {
                if (!ownIds.contains(id)) {
                    missingIds.add(id);
                }
            }

            Protocol.sendLine(output, "rdyD");
            if (!missingIds.isEmpty()) {
                System.out.println("Requesting " + missingIds.size() + " missing chunks");
            }
            for (int chunkId : missingIds) {
                Protocol.sendLine(output, "index");
                Protocol.sendLine(output, Integer.toString(chunkId));
                System.out.println("Requested chunk " + chunkId);
                String sizeLine = Protocol.readLine(input);
                if (sizeLine.equals("error")) {
                    continue;
                }
                int size = Integer.parseInt(sizeLine);
                byte[] data = Protocol.readExact(input, size);
                FileOutputStream chunkOutput = new FileOutputStream(Protocol.chunkFile(chunkFolder, chunkId));
                chunkOutput.write(data);
                chunkOutput.close();
                System.out.println("Received chunk " + chunkId);
            }

            Protocol.writeSummary(peerFolder, chunkFolder, peerId, info.totalChunks);
            List<Integer> currentIds = Protocol.getChunkIds(chunkFolder);
            if (currentIds.size() == info.totalChunks) {
                boolean complete = true;
                for (int i = 0; i < info.totalChunks; i++) {
                    if (currentIds.get(i) != i) {
                        complete = false;
                    }
                }
                if (complete) {
                    File destination = new File(outputFolder, info.fileName);
                    if (Protocol.assembleFile(chunkFolder, info.totalChunks, destination)) {
                        System.out.println("Reassembled complete file at " + destination.getPath());
                    }
                    Protocol.sendLine(output, "done");
                    connection.close();
                    return;
                }
            }

            Protocol.sendLine(output, "chunkIDList");
            System.out.println("Requested updated chunk ID list");
            readChunkList(input);
            Thread.sleep(1000);
        }
    }

    private static List<Integer> readChunkList(DataInputStream input) throws Exception {
        if (!Protocol.readLine(input).equals("list")) {
            throw new Exception("Neighbor did not send a chunk ID list.");
        }
        List<Integer> ids = Protocol.parseIdList(Protocol.readLine(input));
        System.out.println("Received chunk ID list (" + ids.size() + " chunks)");
        return ids;
    }
}
