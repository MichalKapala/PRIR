import java.util.List;

public class ConcreteDeltaReceiver implements DeltaReceiver {

    private int deltaCounter = 0;
    public void accept(List<Delta> deltas) {
        if(!deltas.isEmpty())
        {
            deltaCounter++;
//            var firstDelta = deltas.get(0);
//            System.out.println("Received " + firstDelta.getDataID());
        }

    }

    public int GetDeltaCtr()
    {
        return deltaCounter;
    }

}
