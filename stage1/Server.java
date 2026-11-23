import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5001;
        ServerSocket server = new ServerSocket(port);
        System.out.println("Listening on port " + port);

        while (true) {
            Socket connection = server.accept();
            Thread clientThread = new Thread(() -> handleClient(connection));
            clientThread.start();
        }
    }

    private static void handleClient(Socket connection) {
        try {
            BufferedReader input = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"));
            PrintWriter output = new PrintWriter(connection.getOutputStream(), true);
            String message = input.readLine();
            if (message != null) {
                output.println(message.toUpperCase());
                System.out.println("Converted text from " + connection.getInetAddress());
            }
            connection.close();
        } catch (Exception error) {
            System.out.println("Client connection ended: " + error.getMessage());
        }
    }
}
