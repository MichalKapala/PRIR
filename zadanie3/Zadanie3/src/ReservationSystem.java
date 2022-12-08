
import javax.naming.InitialContext;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.rmi.server.UnicastRemoteObject;

class ReservationSystem  implements Cinema {

    static class Pair
    {
        public static <T, U> Map.Entry<T, U> of(T first, U second) {
            return new AbstractMap.SimpleEntry<>(first, second);
        }
    }
    HashSet<Integer> availableSeats;

    Map<Map.Entry<String, Long>, Set<Integer>> reservations = new ConcurrentHashMap<>();

    Map<Integer, String> confirmedReservations = new ConcurrentHashMap<>();

    long maxTimeForConfirmation;

    public ReservationSystem()  {
        super();


//        try {
//            Cinema stub1 = (Cinema) UnicastRemoteObject.exportObject(this, 0);
//            LocateRegistry.getRegistry().bind(SERVICE_NAME, stub1);
//        } catch (Exception e) {
//            System.out.println("Some error occured " + e);
//        }

        try {
            Cinema stub = (Cinema) UnicastRemoteObject.exportObject(this, 0);
            var context = new InitialContext();
            context.bind("rmi:"+SERVICE_NAME, stub);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public void configuration(int seats, long timeForConfirmation) throws RemoteException{
        this.availableSeats = new HashSet<>(seats);
        this.maxTimeForConfirmation = timeForConfirmation;
        for(int i =0; i< seats; i++)
        {
            availableSeats.add(i);
        }
    }

    @Override
    public synchronized Set<Integer> notReservedSeats() throws RemoteException{
        ClearSeats();
        return availableSeats;
    }


    public boolean reservation(String user, Set<Integer> seats) throws RemoteException{
        ClearSeats();

        synchronized (this.reservations)
        {
            if(!availableSeats.containsAll(seats))
            {
                return false;
            }

            availableSeats.removeAll(seats);
            reservations.put(Pair.of(user, System.currentTimeMillis()), seats);
        }

        return true;
    }

    public boolean confirmation(String user) throws RemoteException{
        boolean retValue = false;

        synchronized (this.reservations)
        {
            for(var it = reservations.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                if(entry.getKey().getKey().equals(user)){
                    for(var seat : entry.getValue()) {
                        confirmedReservations.put(seat, user);
                    }
                    it.remove();
                    retValue = true;
                }
            }
        }

        return retValue;
    }

     public synchronized String whoHasReservation(int seat) throws RemoteException{
        return confirmedReservations.get(seat);
    }

    private synchronized void ClearSeats()
    {
        for(var it = reservations.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            long duration = System.currentTimeMillis() - entry.getKey().getValue();
            if (duration >= maxTimeForConfirmation) {
                availableSeats.addAll(entry.getValue());
                it.remove();
            }
        }
    }
}

