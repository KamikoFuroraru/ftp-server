import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ClientHandler extends Thread {

    // control connection
    private ServerSocket controlServerSocket;
    private Socket controlClientSocket;
    private PrintWriter controlOutWriter;
    private BufferedReader controlInReader;

    // data Connection
    private int dataPort;
    private ServerSocket dataServerSocket;
    private Socket dataClientSocket;
    private PrintWriter dataOutWriter;

    private String transferMode = "ASCII";

    private String currDirectory;
    private String separator = "\\";

    // users
    private List<String> users = Arrays.asList("Vera", "ftp");
    private List<String> passwords = Arrays.asList("123", "ftp");
    private int userNumber = -1;
    private int priority = -1; // 0 - user, 1 - ftp (can't dele, mkd, rmd, stor)

    private boolean quit = false;

    public ClientHandler(ServerSocket sSocket, Socket cSocket, int port) {
        this.controlClientSocket = cSocket;
        this.controlServerSocket = sSocket;
        this.dataPort = port;
        this.currDirectory = "";
    }

    public void run() {
        try {
            controlInReader = new BufferedReader(new InputStreamReader(controlClientSocket.getInputStream()));
            controlOutWriter = new PrintWriter(controlClientSocket.getOutputStream(), true);

            sendControlToClient("220 Welcome to the FTP-Server");

            while (!quit && !controlServerSocket.isClosed()) executeCommand(controlInReader.readLine());

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                controlInReader.close();
                controlOutWriter.close();
                controlClientSocket.close();
                System.out.println("Sockets closed and worker stopped");
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Could not close sockets");
            }
        }

    }

    private void executeCommand(String str) {
        int index = str.indexOf(' ');
        String command = ((index == -1) ? str.toUpperCase() : (str.substring(0, index)).toUpperCase());
        String args = ((index == -1) ? null : str.substring(index + 1));

        System.out.println("Command: " + command + " Args: " + args);

        switch (command) {
            case "USER":
                execUser(args);
                break;

            case "PASS":
                execPass(args);
                break;

            case "PASV":
                execPasv();
                break;

            case "LIST":
                execList("-l");
                break;

            case "NLST":
                execList("-n");
                break;

            case "PWD":
                execPwd();
                break;

            case "CWD":
                execCwd(args);
                break;

            case "MKD":
                execMkd(args);
                break;

            case "RMD":
                execRmd(args);
                break;

            case "STOR":
                execStor(args);
                break;

            case "RETR":
                execRetr(args);
                break;

            case "DELE":
                execDele(args);
                break;

            case "TYPE":
                execType(args);
                break;

            case "QUIT":
                execQuit();
                break;

            default:
                sendControlToClient("501 Unknown command");
                break;
        }
    }

    private void execUser(String username) {
        if (users.contains(username)) {
            userNumber = users.indexOf(username);
            sendControlToClient("331 Username okay, need password");
        } else sendControlToClient("530 Not logged in");
    }

    private void execPass(String password) {
        if (passwords.get(userNumber).equals(password)) {
            if (users.get(userNumber).equals("ftp")) priority = 1;
            else priority = 0;
            sendControlToClient("230 User logged in successfully");
        } else {
            sendControlToClient("530 Not logged in");
        }
    }

    private void execPasv() {
        String ip = "127.0.0.1";
        String[] splitIp = ip.split("\\.");
        int num1 = dataPort / 256;
        int num2 = dataPort % 256;

        sendControlToClient("227 Entering Passive Mode (" + splitIp[0] + "," + splitIp[1] + "," + splitIp[2] + "," + splitIp[3] + "," + num1 + "," + num2 + ")");
        openDataConnection(dataPort);
    }

    private void openDataConnection(int port) {
        try {
            dataServerSocket = new ServerSocket(port);
            dataClientSocket = dataServerSocket.accept();
            dataOutWriter = new PrintWriter(dataClientSocket.getOutputStream(), true);
            System.out.println("Data connection - Passive Mode - established");
        } catch (IOException e) {
            System.out.println("Could not create data connection.");
            e.printStackTrace();
        }

    }

    private void closeDataConnection() {
        try {
            dataOutWriter.close();
            dataClientSocket.close();
            dataServerSocket.close();
            System.out.println("Data connection was closed");
        } catch (IOException e) {
            System.out.println("Could not close data connection");
            e.printStackTrace();
        }
        dataOutWriter = null;
        dataClientSocket = null;
        dataServerSocket = null;
    }

    private void execList(String arg) {
        if (dataClientSocket == null || dataClientSocket.isClosed())
            sendControlToClient("425 No data connection was established");
        else {
            File[] dirList = Paths.get(currDirectory).toFile().listFiles();
            if (dirList == null) sendControlToClient("550 File does not exist.");
            else {
                sendControlToClient("150 Opening ASCII mode data connection for file list.");
                DateFormat dateFormat = new SimpleDateFormat(" MMM dd yyyy hh:mm ", new Locale("en"));
                String date;
                for (File f : dirList) {
                    date = dateFormat.format(f.lastModified());
                    if (f.isDirectory()) {
                        if (arg.equals("-n"))
                            sendDataToClient("drw-rw-rw- 1 ftp ftp " + f.getName());
                        else
                            sendDataToClient("drw-rw-rw- 1 ftp ftp " + f.length() + date + f.getName());
                    } else {
                        if (arg.equals("-n"))
                            sendDataToClient("-rw-rw-rw- 1 ftp ftp " + f.getName());
                        else
                            sendDataToClient("-rw-rw-rw- 1 ftp ftp " + f.length() + date + f.getName());
                    }
                }
                sendControlToClient("226 Transfer complete.");
                closeDataConnection();
            }
        }
    }

    private void execPwd() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        sendControlToClient("257 \"" + currDirectory + "\"");
    }

    private void execCwd(String args) {
        if (currDirectory.equals("")) currDirectory = args;
        String filename = currDirectory;

        if (args.equals("..")) {
            int index = filename.lastIndexOf(separator);
            if (index > 0) filename = filename.substring(0, index);
        } else if (!args.equals(".")) filename = filename + separator + args;

        File f = new File(filename);

        if (f.exists() && f.isDirectory()) {
            currDirectory = filename;
            sendControlToClient("250 The current directory has been changed to " + currDirectory);
        } else sendControlToClient("550 Requested action not taken. File unavailable.");
    }

    private void execMkd(String dir) {
        if (priority == 1) sendControlToClient("550 Not enough rights");
        else {
            if (dir != null && dir.matches("[a-zA-Z0-9]*")) {
                File file = new File(currDirectory + separator + dir);
                if (!file.mkdirs()) {
                    sendControlToClient("550 Failed to create new directory");
                    System.out.println("Failed to create new directory");
                } else sendControlToClient("250 Directory successfully created");
            } else sendControlToClient("550 Invalid name");
        }
    }

    private void execRmd(String dir) {
        if (priority == 1) sendControlToClient("550 Not enough rights");
        else {
            String filename = currDirectory;
            if (dir != null && dir.matches("[a-zA-Z0-9]*")) {
                filename = filename + separator + dir;
                File file = new File(filename);
                if (file.exists() && file.isDirectory()) {
                    try {
                        FileUtils.deleteDirectory(file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    sendControlToClient("250 Directory was successfully removed");
                } else sendControlToClient("550 Requested action not taken. File unavailable.");
            } else sendControlToClient("550 Invalid file name.");
        }
    }

    private void execRetr(String file) {
        File f = new File(currDirectory + separator + file);
        if (!f.exists()) sendControlToClient("550 File does not exist");
        else retrOrStor("-r", f);
        closeDataConnection();
    }

    private void execStor(String file) {
        if (priority == 1) sendControlToClient("550 Not enough rights");
        else {
            if (file == null) sendControlToClient("501 No filename given");
            else {
                File f = new File(currDirectory + separator + file);
                if (f.exists()) sendControlToClient("550 File already exists");
                else retrOrStor("-s", f);
            }
        }
        closeDataConnection();
    }

    private void retrOrStor(String arg, File f) {
        if (transferMode.equals("BINARY")) {
            sendControlToClient("150 Opening binary mode data connection for requested file " + f.getName());

            BufferedOutputStream fout = null;
            BufferedInputStream fin = null;
            try {
                if (arg.equals("-s")) {
                    fout = new BufferedOutputStream(new FileOutputStream(f));
                    fin = new BufferedInputStream(dataClientSocket.getInputStream());
                } else {
                    fout = new BufferedOutputStream(dataClientSocket.getOutputStream());
                    fin = new BufferedInputStream(new FileInputStream(f));
                }
            } catch (Exception e) {
                System.out.println("Could not create file streams");
            }

            System.out.println("Start receiving file " + f.getName());
            byte[] buf = new byte[1024];
            int l = 0;
            try {
                while ((l = fin.read(buf, 0, 1024)) != -1) fout.write(buf, 0, l);
            } catch (IOException e) {
                System.out.println("Could not read from or write to file streams");
                e.printStackTrace();
            }

            try {
                fin.close();
                fout.close();
            } catch (IOException e) {
                System.out.println("Could not close file streams");
                e.printStackTrace();
            }

            System.out.println("Completed receiving file " + f.getName());
            sendControlToClient("226 File transfer successful. Closing data connection.");
        } else {
            sendControlToClient("150 Opening ASCII mode data connection for requested file " + f.getName());

            BufferedReader rin = null;
            PrintWriter rout = null;
            try {
                if (arg.equals("-s")) {
                    rin = new BufferedReader(new InputStreamReader(dataClientSocket.getInputStream()));
                    rout = new PrintWriter(new FileOutputStream(f), true);
                } else {
                    rin = new BufferedReader(new FileReader(f));
                    rout = new PrintWriter(dataClientSocket.getOutputStream(), true);
                }

            } catch (IOException e) {
                System.out.println("Could not create file streams");
            }

            String s;
            try {
                while ((s = rin.readLine()) != null) rout.println(s);
            } catch (IOException e) {
                System.out.println("Could not read from or write to file streams");
                e.printStackTrace();
            }

            try {
                rout.close();
                rin.close();
            } catch (IOException e) {
                System.out.println("Could not close file streams");
                e.printStackTrace();
            }
            sendControlToClient("226 File transfer successful. Closing data connection.");
        }
    }

    private void execDele(String file) {
        if (priority == 1) sendControlToClient("550 Not enough rights");
        else {
            File f = new File(currDirectory + separator + file);
            if (!f.exists()) sendControlToClient("550 File does not exist");
            else {
                f.delete();
                sendControlToClient("250 File was successfully removed");
            }
        }
    }

    private void execType(String mode) {
        if (mode.toUpperCase().equals("A")) {
            transferMode = "ASCII";
            sendControlToClient("200 OK");
        } else if (mode.toUpperCase().equals("I")) {
            transferMode = "BINARY";
            sendControlToClient("200 OK");
        } else sendControlToClient("504 Not OK");
    }

    private void execQuit() {
        sendControlToClient("221 Closing connection");
        quit = true;
    }

    private void sendControlToClient(String msg) {
        controlOutWriter.println(msg);
    }

    private void sendDataToClient(String msg) {
        if (dataClientSocket == null || dataClientSocket.isClosed()) {
            sendControlToClient("425 No data connection was established");
            System.out.println("Cannot send message, because no data connection is established");
        } else {
            dataOutWriter.print(msg + '\r' + '\n');
        }
    }
}
