import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

public class Server {

    private static Selector selector;
    private static int current = 0;
    private static int numAcks = 0;
    private static int numInitAcks = 0;
    private static int myID;
    private static SelectionKey clientKey = null;
    private static ChannelOperator co = new ChannelOperator();
    private static Set<SelectionKey> connections = new HashSet<>();
    private static PriorityQueue<Message> q = new PriorityQueue<>((m1, m2) -> m1.ts - m2.ts);
    private static Set<Integer> seatSet = new HashSet<>();
    private static Map<String, Integer> assignedSeats = new HashMap<>();
    private static Map<Integer, ServerInfo> servers = new HashMap<>();
    public static void main (String[] args) throws IOException, ClassNotFoundException, InterruptedException {

        // Read Config
        Scanner scanner = new Scanner(System.in);
        String line = scanner.nextLine();
        String parts[] = line.trim().split(" ");
        myID =  Integer.parseInt(parts[0]) - 1;
        int numServer = Integer.parseInt(parts[1]);
        int numSeat = Integer.parseInt(parts[2]);

        for (int i = 0; i < numSeat;i ++)
            seatSet.add(i);
        if (myID == 0)
            seatSet.add(20);


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
                if (!key.isValid()) {
                    continue;
                }

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
                System.out.println(assignedSeats);
                System.out.println(seatSet);
                System.out.println(q);
            }
        }
    }

    private static void handleRead(int myID, SelectionKey key) throws IOException, ClassNotFoundException {
        // Read the data
        SocketChannel socketChannel = (SocketChannel) key.channel();
        System.out.println(socketChannel);

        co.recv(socketChannel);
        Serializable ois = co.recv(socketChannel);

        Message s = (Message)ois;
        System.out.println("recv:" + s);
        if (s == null) {
            connections.remove(key);
            key.channel().close();
            key.cancel();
            System.out.println("close" + myID);
        }
        else {
            syncCurrent(s);
            if (s.type.equals("Init")) {
                connections.add(key);
                Message msg = new Message(current, "ActInit", myID);
                co.send(socketChannel, msg);
                System.out.println("send msg:" + msg);
            }
            else if (s.type.equals("ActInit")) {
                numInitAcks++;
                System.out.println("initack:" + numInitAcks);
                if (numInitAcks == connections.size()) {
                    Message m = new Message(current, "Sync", myID);
                    numAcks = 0;
                    sendToAll(m);
                    addToQ(m);
                }
            }
            else if (s.type.equals("Ack")) {
                numAcks++;
                if (numAcks == connections.size() && q.peek().id == myID) {
                    if("Sync".equals(q.peek().type)) {
                        seatSet.clear();
                        seatSet.addAll(s.availableSeats);
                        assignedSeats = new HashMap<>(s.assignedSeats);
                        sendToAll(new Message(current, "Delete", myID));
                        q.poll();
                    }
                    else if ("reserve".equals(q.peek().type)) {
                        Message m = q.poll();
                        reserve(m);
                        Message msg = new Message(current, "Delete", myID);
                        msg.assignedSeats = assignedSeats;
                        msg.availableSeats = seatSet;
                        sendToAll(msg);

                    }
                    else if ("bookSeat".equals(q.peek().type)) {
                        Message m = q.poll();

                        bookSeat(m);

                        Message msg = new Message(current, "Delete", myID);
                        msg.assignedSeats = assignedSeats;
                        msg.availableSeats = seatSet;
                        sendToAll(msg);
                    }
                    else if ("delete".equals(q.peek().type)) {
                        Message m = q.poll();

                        delete(m);

                        Message msg = new Message(current, "Delete", myID);
                        msg.assignedSeats = assignedSeats;
                        msg.availableSeats = seatSet;
                        sendToAll(msg);
                    }
                    else if ("search".equals(q.peek().type)) {
                        Message m = q.poll();

                        search(m);

                        Message msg = new Message(current, "Delete", myID);
                        msg.assignedSeats = assignedSeats;
                        msg.availableSeats = seatSet;
                        sendToAll(msg);
                    }
                }
            }
            else if (s.type.equals("Delete")) {
                Message msg = q.poll();
                if ("reserve".equals(msg.type) || "bookSeat".equals(msg.type) || "delete".equals(msg.type)) {
                    seatSet = new HashSet<>();
                    seatSet.addAll(s.availableSeats);
                    assignedSeats = new HashMap<>(s.assignedSeats);
                }
            }
            else if (s.type.equals("client")) {
                key.cancel();
                clientKey = key;
                String tokens[] = s.cmd.trim().split(" ");
                if (tokens[0].equals("reserve")) {
                    System.out.println("reserve" + s );
                    Message m = new Message(current, "reserve", myID);
                    m.cmd = s.cmd;
                    numAcks = 0;
                    if (connections.isEmpty())
                        reserve(m);
                    else {
                        sendToAll(m);
                        addToQ(m);
                    }
                }
                else if (tokens[0].equals("bookSeat")) {
                    System.out.println("book seat" + s );
                    Message m = new Message(current, "bookSeat", myID);
                    m.cmd = s.cmd;
                    numAcks = 0;
                    if (connections.isEmpty())
                        bookSeat(m);
                    else {
                        sendToAll(m);
                        addToQ(m);
                    }
                }
                else if (tokens[0].equals("delete")) {
                    System.out.println("release seat" + s );
                    Message m = new Message(current, "delete", myID);
                    m.cmd = s.cmd;
                    numAcks = 0;
                    if (connections.isEmpty())
                        bookSeat(m);
                    else {
                        sendToAll(m);
                        addToQ(m);
                    }
                }
                else if (tokens[0].equals("search")) {
                    System.out.println("search " + s );
                    Message m = new Message(current, "search", myID);
                    m.cmd = s.cmd;
                    numAcks = 0;
                    if (connections.isEmpty())
                        search(m);
                    else {
                        sendToAll(m);
                        addToQ(m);
                    }
                }
            }
            else {
                q.add(s);
                sendAck(socketChannel, s);
            }
        }
        System.out.println("String is: '" + s + "'" );
    }

    private static void bookSeat(Message m) throws IOException {
        String []token = m.cmd.trim().split(" ");

        String name = token[1];
        int seatNum = Integer.parseInt(token[2]);

        if (seatSet.isEmpty()) {
            ByteBuffer wrap = ByteBuffer.wrap(("Sold out - No seat available#").getBytes());
            ((SocketChannel)clientKey.channel()).write(wrap);
            return ;
        }

        if (!seatSet.contains(seatNum)) {
            ByteBuffer wrap = ByteBuffer.wrap((seatNum + " is not available#").getBytes());
            ((SocketChannel)clientKey.channel()).write(wrap);
            return ;
        }

        if (assignedSeats.keySet().contains(name)) {
            ByteBuffer wrap = ByteBuffer.wrap(("Seat already booked against the name provided#").getBytes());
            ((SocketChannel)clientKey.channel()).write(wrap);
            return ;
        }

        seatSet.remove(seatNum);

        assignedSeats.put(name, seatNum);
        ByteBuffer wrap = ByteBuffer.wrap(("Seat assigned to you is " + seatNum + "#").getBytes());
        ((SocketChannel)clientKey.channel()).write(wrap);
    }

    private static void reserve(Message m) throws IOException {
        String []token = m.cmd.trim().split(" ");

        String name = token[1];

        if (seatSet.isEmpty()) {
            ByteBuffer wrap = ByteBuffer.wrap(("Sold out - No seat available#").getBytes());
            ((SocketChannel)clientKey.channel()).write(wrap);
            return ;
        }

        if (assignedSeats.keySet().contains(name)) {
            ByteBuffer wrap = ByteBuffer.wrap(("Seat already booked against the name provided#").getBytes());
            ((SocketChannel)clientKey.channel()).write(wrap);
            return ;
        }

        int oneSeat=-1;
        for (Integer seat : seatSet) {
            oneSeat = seat;
            break;
        }
        seatSet.remove(oneSeat);
        assignedSeats.put(token[1], oneSeat);
        ByteBuffer wrap = ByteBuffer.wrap(("Seat assigned to you is " + oneSeat + "#").getBytes());
        ((SocketChannel)clientKey.channel()).write(wrap);
    }

    private static void search(Message m) throws IOException {
        String []token = m.cmd.trim().split(" ");

        String name = token[1];

        if (!assignedSeats.keySet().contains(name)) {
            ByteBuffer wrap = ByteBuffer.wrap(("‘No reservation found for " + name + "#").getBytes());
            ((SocketChannel)clientKey.channel()).write(wrap);
            return ;
        }

        int seatNum = assignedSeats.get(name);
        ByteBuffer wrap = ByteBuffer.wrap(( seatNum + "#").getBytes());
        ((SocketChannel)clientKey.channel()).write(wrap);
    }

    private static void delete(Message m) throws IOException {
        String []token = m.cmd.trim().split(" ");

        if (!assignedSeats.containsKey(token[1])) {
            ByteBuffer wrap = ByteBuffer.wrap(("No reservation found for " + token[1] + "#").getBytes());
            ((SocketChannel)clientKey.channel()).write(wrap);
            return ;
        }
        int seatNum = assignedSeats.get(token[1]);

        seatSet.add(seatNum);
        assignedSeats.remove(token[1]);
        ByteBuffer wrap = ByteBuffer.wrap((seatNum + "#").getBytes());
        ((SocketChannel)clientKey.channel()).write(wrap);
    }

    private static void sendAck(SocketChannel socketChannel, Message s) throws IOException {
        Message msg = new Message(current, "Ack", myID);
        msg.availableSeats = seatSet;
        msg.assignedSeats= assignedSeats;
        msg.ori = s.type;
        co.send(socketChannel, msg);
        System.out.println("send msg:" + msg);
    }

    private static void addToQ(Message m) {
        q.add(m);
    }

    private static void sendToAll(Message msg) throws IOException {
        for (SelectionKey key: connections) {
            SocketChannel socketChannel = (SocketChannel) key.channel();
            co.send(socketChannel, msg);
            System.out.println("send msg:" + msg);
        }
    }

    private static boolean handleConnection(int myID, SelectionKey key, Set<SelectionKey> connections) throws IOException {
        connect(key);
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
        ss.setReuseAddress(true);

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
        InetSocketAddress remote = (InetSocketAddress) sc.getRemoteAddress();
        ServerInfo si = new ServerInfo();
        si.serverIp = remote.getAddress().getHostName().replace("localhost", "127.0.0.1");
        si.port = remote.getPort();

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
            int num = socket.read(lengthByteBuffer);
            if (num == -1) return null;
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

    @Override
    public String toString() {
        return "" + serverIp + ":" + port;
    }
}
