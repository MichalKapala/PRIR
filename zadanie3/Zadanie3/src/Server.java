import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Server {
    public static void main (String [] args)
    {
        System.setProperty("java.rmi.server.hostname", "127.0.1.1");
        try {
            Registry registry = LocateRegistry.getRegistry();
            Cinema reservationSystem = new ReservationSystem();
        } catch (Exception e) {
            System.out.println("Some error occured on server side" + e);
        }


    }
}
