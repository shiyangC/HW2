import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.*;

public class Client {
    public static int TIMEOUT = 100;
    public static void main (String[] args) throws IOException {

        Scanner scanner = new Scanner(System.in);
        String line = scanner.nextLine();
        int numServer = Integer.parseInt(line.trim());
        Map<Integer, ServerInfo> servers = new HashMap<>();

        for (int i = 0; i < numServer; i++) {
            line = scanner.nextLine();
            String parts[] = line.trim().split(":");
            ServerInfo si = new ServerInfo();
            si.serverIp = parts[0];
            si.port = Integer.parseInt(parts[1]);
            servers.put(i, si);
        }

        while (scanner.hasNextLine()) {
            line = scanner.nextLine();
            for (int i = 0; ; i = (i+1) % numServer) {
                try {
                    handleCommand(line, servers.get(i));
                }
                catch(SocketTimeoutException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void handleCommand(String cmd, ServerInfo si) throws IOException {

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(si.serverIp, si.port), TIMEOUT);

        Message msg = new Message(0, "client", -1);
        msg.cmd = cmd;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for(int i=0;i<4;i++) baos.write(0);
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(msg);
        oos.close();
        final ByteBuffer wrap = ByteBuffer.wrap(baos.toByteArray());
        wrap.putInt(0, baos.size()-4);

        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(wrap.array());

        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        //System.out.println("echo: " + in.readLine());
        String result = in.readLine();
        if (result != null) {
            for (String line : result.split("\t")) {
                System.out.println(line);
            }
        }
    }


}
