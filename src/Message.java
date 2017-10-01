import java.io.Serializable;

public class Message implements Serializable{
    public int ts;
    public String type;
    public int id;
    public Message(int ts, String type, int id) {
        this.ts = ts;
        this.type = type;
        this.id = id;
    }

    @Override
    public String toString() {
        return "" + ts + ":" + type + ":" + id;
    }
}
