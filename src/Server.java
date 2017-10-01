import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

public class Server {

    private static Selector selector;
    private static int current = 0;
    private static int connectionNum = 0;
    private static ChannelOperator co = new ChannelOperator();
    public static void main (String[] args) throws IOException, ClassNotFoundException, InterruptedException {

        // Read Config
        Scanner scanner = new Scanner(System.in);
        String line = scanner.nextLine();
        String parts[] = line.trim().split(" ");
        int myID = Integer.parseInt(parts[0]) - 1;
        int numServer = Integer.parseInt(parts[1]);
        int numSeat = Integer.parseInt(parts[2]);

        Map<Integer, ServerInfo> servers = new HashMap<>();
        Set<SelectionKey> connections = new HashSet<>();
        Set<Integer> seatSet = new HashSet<>();
        for (int i = 0; i < numSeat;i ++)
            seatSet.add(i);

        PriorityQueue<Message> q = new PriorityQueue<>((m1, m2) -> m1.ts - m2.ts);

        for (int i = 0; i < numServer; i++) {
            String serverInput = scanner.nextLine();
            ServerInfo si = new ServerInfo();
            String[] tokens = serverInput.trim().split(":");
            si.serverIp = tokens[0];
            si.port = Integer.parseInt(tokens[1]);
            servers.put(i, si);
        }

        // Initiate server connectionNum
        selector = Selector.open();
        for (int i = 0; i < numServer; i++) {
            if (i == myID)
                continue;
            initiateConnection(servers, i);
        }
        initiateListenPort(myID, servers);

        while (true) {
            int num = selector.select(1000);
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> it = selectedKeys.iterator();

            while (it.hasNext()) {
                SelectionKey key = it.next();

                if( key.isConnectable()) {
                    if (handleConnection(myID, key, connections)) continue;
                    it.remove();
                }
                else if ((key.readyOps() & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT) {
                    handleAcception(myID, key, connections);
                    it.remove();
                } else if ((key.readyOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ) {
                    handleRead(myID, key);
                    it.remove();
                }
                System.out.println(connections);
                System.out.println(connectionNum);
            }
        }
    }

    private static void handleRead(int myID, SelectionKey key) throws IOException, ClassNotFoundException {
        // Read the data
        SocketChannel socketChannel = (SocketChannel) key.channel();

        co.recv(socketChannel);
        Serializable ois = co.recv(socketChannel);
        Message s = (Message)ois;
        syncCurrent(s);

        if (s == null) {
            key.channel().close();
            System.out.println("close" + myID);
        } else {
            if (s.type.equals("Init")) {
                Message msg = new Message(current, "ActInit", myID);
                co.send(socketChannel, msg);
                System.out.println("send msg:" + msg);
            }
        }
        System.out.println("String is: '" + s + "'" );
    }

    private static boolean handleConnection(int myID, SelectionKey key, Set<SelectionKey> connections) throws IOException {
        connect(key);
        connectionNum++;
        connections.add(key);
        SocketChannel socketChannel = (SocketChannel) key.channel();

        if (!socketChannel.isConnected()) {
            System.out.println("not connect ");
            connections.remove(key);
            return true;
        }
        Message msg = new Message(current, "Init", myID);
        co.send(socketChannel, msg);
        System.out.println("Connect init");
        System.out.println("send msg:" + msg);
        return false;
    }

    private static void initiateListenPort(int myID, Map<Integer, ServerInfo> servers) throws IOException {
        // Open a listener on each port, and register each one
        // with the selector
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.configureBlocking( false );
        ServerSocket ss = ssc.socket();
        InetSocketAddress address = new InetSocketAddress(servers.get(myID).port);
        ss.bind(address);

        SelectionKey listenKey = ssc.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println( "Going to listen on "+ servers.get(myID).port );
    }

    private static void initiateConnection(Map<Integer, ServerInfo> servers, int i) {
        ServerInfo si = servers.get(i);
        SocketChannel socketChannel = null;
        try {
            System.out.println("init connect init" + i);
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);

            socketChannel.connect(new InetSocketAddress(si.serverIp, si.port));
            socketChannel.register(selector, SelectionKey.OP_CONNECT);
        } catch (IOException e) {
            e.printStackTrace();
            try {
                socketChannel.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private static void handleAcception(int myID, SelectionKey key, Set<SelectionKey> connections) throws IOException {
        ServerSocketChannel ssc;// Accept the new connection
        ssc = (ServerSocketChannel) key.channel();
        SocketChannel sc = ssc.accept();
        sc.configureBlocking(false);

        // Add the new connection to the selector
        SelectionKey newKey = sc.register(selector, SelectionKey.OP_READ);
        connections.add(newKey);

        connectionNum++;
        System.out.println("Got connection from " + sc);
    }


    private static void syncCurrent(Message s) {
        current = Math.max(current, s.ts) + 1;
    }

    public static boolean connect(SelectionKey key){
        try{
            SocketChannel socketChannel = (SocketChannel) key.channel();
            if(socketChannel.finishConnect() ){
                key.interestOps(SelectionKey.OP_READ);
            }
        }catch(IOException e){
            key.cancel();
        }
        return true;
    }
}

class ChannelOperator{
    public static void send(SocketChannel socket,  Serializable serializable) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for(int i=0;i<4;i++) baos.write(0);
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(serializable);
        oos.close();
        final ByteBuffer wrap = ByteBuffer.wrap(baos.toByteArray());
        wrap.putInt(0, baos.size()-4);
        socket.write(wrap);
    }
    private final ByteBuffer lengthByteBuffer = ByteBuffer.wrap(new byte[4]);
    private ByteBuffer dataByteBuffer = null;
    private boolean readLength = true;

    public Serializable recv(SocketChannel socket) throws IOException, ClassNotFoundException {
        if (readLength) {
            socket.read(lengthByteBuffer);
            if (lengthByteBuffer.remaining() == 0) {
                readLength = false;
                dataByteBuffer = ByteBuffer.allocate(lengthByteBuffer.getInt(0));
                lengthByteBuffer.clear();
            }
        } else {
            socket.read(dataByteBuffer);
            if (dataByteBuffer.remaining() == 0) {
                ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(dataByteBuffer.array()));
                final Serializable ret = (Serializable) ois.readObject();
                // clean up
                dataByteBuffer = null;
                readLength = true;
                return ret;
            }
        }
        return null;
    }

}

class ServerInfo {
    public String serverIp;
    public int port;
}
