import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java Client <server-ip> <port> <text>");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        StringBuilder message = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) {
                message.append(" ");
            }
            message.append(args[i]);
        }

        Socket connection = new Socket(host, port);
        PrintWriter output = new PrintWriter(connection.getOutputStream(), true);
        BufferedReader input = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), "UTF-8"));
        output.println(message.toString().toLowerCase());
        System.out.println(input.readLine());
        connection.close();
    }
}
