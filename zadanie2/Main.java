import java.time.Instant;
import java.util.ArrayList;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Collections;

class Main 
{
	private static Data GenerateData(int id, int size)
	{

			ArrayList<Integer> v = new ArrayList<>();
			for(int j=0;j <size; j++)
			{
				int randomNum = ThreadLocalRandom.current().nextInt(0, 100 + 1);
				v.add(randomNum);

			}

		return new ConcreteData(id, size, v);
	}

	public static void main(String args[]) throws InterruptedException {
		long totalElapsedTime = 0;
		Instant start;
		Instant end;
		int dataSize =  30000; // Size of one Data object list
		int numberOfData = 5000; //Number of Data objects
		int numberOfPackets = 300; //Number of packets- one packet contains numberOfData Data objects

		DeltaReceiver receiver = new ConcreteDeltaReceiver();
		ParallelCalculator calculator = new ParallelCalculator();
		calculator.setDeltaReceiver(receiver);
		calculator.setThreadsNumber(5);

		for (int j =0; j < numberOfPackets; j++)
		{
			ArrayList<Data> dataList = new ArrayList<>();

			for (int i = j*numberOfData; i < (j+1)*numberOfData ; i++) {
				dataList.add(GenerateData(i, dataSize));
			}

			Collections.shuffle(dataList);

			start = Instant.now();
			for (int i = 0; i < numberOfData ; i++) {
				calculator.addData(dataList.get(i));
			}
			end = Instant.now();
			totalElapsedTime += Duration.between(start, end).toNanos();
		}

		Thread.sleep(3000);
		System.out.println("Elapsed time " + (totalElapsedTime / 1000.0 ) + "us");
		System.out.println(receiver.GetDeltaCtr());

		System.exit(0);
	}
}
