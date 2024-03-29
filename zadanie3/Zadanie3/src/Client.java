import jdk.jfr.Threshold;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashSet;
import java.util.Set;
import  java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Client {



    static class Tester
    {
        private ExecutorService executor;
        Cinema cinema;
        public Tester() throws NotBoundException, RemoteException {
            Registry registry = LocateRegistry.getRegistry();
             cinema = (Cinema) registry.lookup("CINEMA");
             this.executor = Executors.newFixedThreadPool(10);
        }

        public void TestSequentialReservation() throws RemoteException {
            cinema.configuration(100, 1000);
            for(int i =0; i< 100; i++) {
                Set<Integer> seats = new HashSet<>();
                seats.add(i);
                String user = Integer.toString(i);
                assert cinema.reservation(user, seats);
                assert cinema.confirmation(user);
                assert cinema.whoHasReservation(i).equals(user);

            }
            assert cinema.notReservedSeats().isEmpty();
        }

        public void TestTakingReservedSeats() throws RemoteException {

            Set<Integer> seats = new HashSet<>();

            cinema.configuration(100, 1000);

            for(int i =0; i< 50; i++) {
                seats.add(i);
            }

            assert cinema.reservation("KOWALSKI", seats);
            assert cinema.confirmation("KOWALSKI");
            assert !cinema.notReservedSeats().isEmpty();
            assert cinema.whoHasReservation(25).equals("KOWALSKI");

            seats.clear();

            for(int i =0; i< 50; i++) {
                seats.add(i);
            }

            assert !cinema.reservation("NOWAK", seats);
            assert !cinema.confirmation("NOWAK");
            assert !cinema.notReservedSeats().isEmpty();
            assert cinema.whoHasReservation(25).equals("KOWALSKI");

        }

        public void TestNoConfirmationInTime() throws RemoteException {

            cinema.configuration(100, 1000);

            this.executor.submit( () ->
                    {
                        Set<Integer> seats = new HashSet<>();
                        for (int i = 0; i < 50; i++) {
                            seats.add(i);
                        }
                    try {
                        if (cinema.reservation("KOWALSKI", seats))
                        {
                            System.out.println("RESERVED SEATS KOWALSKI OK");
                        }
                        if( cinema.whoHasReservation(25) == null)
                        {
                            System.out.println("NOBODY CONFIRMED SEATS KOWALSKI OK");
                        }
                        if(cinema.notReservedSeats().size() == 50)
                        {
                            System.out.println("SEATS NUBMER EQUALS 50 KOWALSKI OK");
                        }

                    }catch (Exception e)
                    {
                        System.out.println("TEST FAILED KOWALSKI");
                    }
                        try {
                            Thread.currentThread().sleep(1500);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }

                        try {
                            assert cinema.confirmation("KOWALSKI") == false;
                        }catch (Exception e)
                        {
                            System.out.println("TEST FAILED KOWALSKI CONFIRMATION");
                            System.out.println("KOWALSKI WAS ABLE TO CONFIRM");
                        }

                    });

            this.executor.submit( () ->
            {
                Set<Integer> seats = new HashSet<>();

                for (int i = 0; i < 50; i++) {
                    seats.add(i);
                }

                try {
                    Thread.currentThread().sleep(1050);
                } catch (InterruptedException e) {
                    System.out.println("RUNTIME");
                    throw new RuntimeException(e);

                }

                try {
                    if (cinema.notReservedSeats().size() == 100)
                    {
                        System.out.println("NOT RESERVED SEATS NOWAK EQUALS 100 OK");
                    }else {
                        System.out.println("NOT RESERVED SEATS NOWAK EQUALS 100 FAILED");
                    }

                    if( cinema.reservation("NOWAK", seats))
                    {
                        System.out.println("RESERVED SEATS NOWAK OK");
                    }else {
                        System.out.println("RESERVED SEATS NOWAK FAILED");
                    }

                    if( cinema.confirmation("NOWAK"))
                    {
                        System.out.println("CONFIRMED SEATS NOWAK OK");
                    }else {
                        System.out.println("CONFIRMED SEATS NOWAK FAILED");
                    }

                    if( cinema.whoHasReservation(25).equals("NOWAK"))
                    {
                        System.out.println("HAS RESERVATION SEATS NOWAK OK");
                    }else {
                        System.out.println("HAS RESERVATION SEATS NOWAK FAILED");
                    }

                    if( cinema.notReservedSeats().size() == 50)
                    {
                        System.out.println("NOT RESERVED SEATS NOWAK OK");
                    }else {
                        System.out.println("NOT RESERVED SEATS NOWAK FAILED");
                    }


                }catch (Exception e)
                {
                    System.out.println("TEST FAILED NOWAK + " + e);
                    throw new RuntimeException(e);
                }
            });

            try {
                Thread.sleep(5500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            System.out.println("FINISH");
        }

        public void TestConfirmationAfterTimeDelay() throws RemoteException {

            Set<Integer> seats = new HashSet<>();

            cinema.configuration(100, 1000);

            for(int i =0; i< 50; i++) {
                seats.add(i);
            }

            assert cinema.reservation("KOWALSKI", seats);
            assert cinema.notReservedSeats().size() == 50;
            assert cinema.whoHasReservation(25) == null;

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            assert cinema.confirmation("KOWALSKI");
            assert cinema.notReservedSeats().size() == 50;
            assert cinema.whoHasReservation(25).equals("KOWALSKI");

        }

        public void TestConfirmationAfterTimeDelay2() throws RemoteException {

            Set<Integer> seats = new HashSet<>();
            Set<Integer> seats2 = new HashSet<>();

            cinema.configuration(100, 1000);

            for(int i =0; i< 50; i++) {
                seats.add(i);
            }
            for(int i =0; i< 25; i++) {
                seats2.add(i);
            }

            assert cinema.reservation("KOWALSKI", seats);
            assert cinema.notReservedSeats().size() == 50;
            assert cinema.whoHasReservation(25) == null;

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assert cinema.reservation("NOWAK", seats2);

            assert !cinema.confirmation("KOWALSKI");
            assert cinema.notReservedSeats().size() == 75;
            assert cinema.confirmation("NOWAK");
            assert cinema.whoHasReservation(24).equals("NOWAK");

        }
    }



    public static void main (String [] args)
    {
        try {
            Tester tester = new Tester();

//              For sure run only one testaces each time
//            System.out.println("START TEST 1");
//            tester.TestSequentialReservation();
//            System.out.println("START TEST 2");
//            tester.TestTakingReservedSeats();
            System.out.println("START TEST 3");
            tester.TestNoConfirmationInTime();
//            System.out.println("START TEST 4");
//            tester.TestConfirmationAfterTimeDelay();
//            System.out.println("START TEST 5");
//            tester.TestConfirmationAfterTimeDelay2();

        } catch (Exception e) {
            System.out.println("Some error occured on client side " + e);
        }


    }
}
