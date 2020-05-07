import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ConnectionHandler extends Thread {

    private ServerSocket serverSocket;

    public ConnectionHandler(ServerSocket socket) {
        this.serverSocket = socket;
    }

    public void run() {
        System.out.println("FTP Server started listening on port " + serverSocket.getLocalPort());

        int clientNumber = 0;
        while(true) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientNumber++;
                int dataPort = serverSocket.getLocalPort() + clientNumber;

                ClientHandler clientHandler = new ClientHandler(serverSocket, clientSocket, dataPort);
                clientHandler.start();

                System.out.println("New connection received.");
            } catch (IOException e) {
                System.out.println("Exception encountered on accept.");
                break;
            }
        }

    }
}
