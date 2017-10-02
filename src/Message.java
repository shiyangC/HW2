import java.io.Serializable;
import java.util.Map;
import java.util.Set;

public class Message implements Serializable{
    public int ts;
    public String type;
    public int id;
    public Set<Integer> availableSeats;
    public Map<String, Integer> assignedSeats;
    public String cmd;
    public String ori;
    public Message(int ts, String type, int id) {
        this.ts = ts;
        this.type = type;
        this.id = id;
    }

    @Override
    public String toString() {
        return "" + ts + ":" + type + ":" + id + ":" + availableSeats + ":" + assignedSeats+":"+ori;
    }
}
