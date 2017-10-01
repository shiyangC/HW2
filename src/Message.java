import java.io.Serializable;
import java.util.Set;

public class Message implements Serializable{
    public int ts;
    public String type;
    public int id;
    public Set<Integer> availableSeats;
    public Message(int ts, String type, int id) {
        this.ts = ts;
        this.type = type;
        this.id = id;
    }

    @Override
    public String toString() {
        return "" + ts + ":" + type + ":" + id + ":" + availableSeats;
    }
}
