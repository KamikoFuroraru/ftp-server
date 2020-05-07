import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;

public class Server {
    private ServerSocket serverSocket;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Invalid cmd format.\nFormat: [PORT]\n");
            return;
        }
        new Server(Integer.parseInt(args[0]));
    }

    public Server(Integer controlPort) {
        try {
            serverSocket = new ServerSocket(controlPort);
        } catch (IOException e) {
            System.out.println("Could not create server socket");
            System.exit(-1);
        }

        ConnectionHandler connectionHandler = new ConnectionHandler(serverSocket);
        connectionHandler.start();

        BufferedReader reader;
        String cmd = "";
        do {
            reader = new BufferedReader(new InputStreamReader(System.in));
            try {
                cmd = reader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } while (!cmd.equals("QUIT"));

        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            connectionHandler.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
