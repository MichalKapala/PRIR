import java.util.ArrayList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;


class ParallelCalculator implements DeltaParallelCalculator
{

	final  private Map<Integer, Data> receivedData = new ConcurrentHashMap<Integer, Data> ();

	final  private Map<Integer, Integer> comparedCounter = new ConcurrentHashMap<Integer, Integer> ();

	final private Map<Integer, List <Delta>> deltasMap = new ConcurrentHashMap<Integer, List <Delta>> ();

	private ExecutorService executor;

	private int maxPoolSize;

	final private PriorityBlockingQueue<Integer> processingQueue = new PriorityBlockingQueue<>();

	private DeltaReceiver receiver;

	  int currentSendDelta = 0;
	@Override
	public void setThreadsNumber(int threads)
	{
		executor = Executors.newFixedThreadPool(threads);
	}

	@Override
	public void setDeltaReceiver(DeltaReceiver receiver)
	{
		this.receiver = receiver;
	}

	@Override
	public void addData(Data data)
	{
		receivedData.put(data.getDataId(), data);
		processElement(data.getDataId());

		while(!processingQueue.isEmpty())
		{
			final int dataId = processingQueue.peek();
			processingQueue.remove(dataId);
			executor.submit(() -> {compareData(dataId); });
		}

	}

	private void processElement(int idx)
	{
		if(receivedData.containsKey(idx - 1))
		{
			processingQueue.add(idx - 1);
		}

		if(receivedData.containsKey(idx + 1))
		{
			processingQueue.add(idx);
		}
	}

	// Concurent
	 private void increaseComparedCtr(int idx1)
	{
			comparedCounter.merge(idx1, 1, Integer::sum);

			if(comparedCounter.get(idx1) >= 2)
			{
				receivedData.remove(idx1);
				comparedCounter.remove(idx1);
			}
	}

	// Concurent
	  private void compareData(int idx)
	{
//		System.out.println("comparing " + idx);
		var data1 = receivedData.get(idx);
		var data2 = receivedData.get(idx + 1);
		List <Delta> currentDeltaList = new ArrayList<>();
		for(int i = 0; i < data1.getSize(); i++)
		{
			if(data2.getValue(i) != data1.getValue(i))
			{
				int diff = data2.getValue(i) - data1.getValue(i);
				Delta delta = new Delta(idx, i, diff);
				currentDeltaList.add(delta);
			}
		}

		deltasMap.put(idx, currentDeltaList);
		sendDiff();
		increaseComparedCtr(idx);
		increaseComparedCtr(idx + 1);
	}

	// Concurent
	synchronized private void sendDiff()
	{
		while(deltasMap.get(currentSendDelta) != null)
		{
			receiver.accept(deltasMap.get(currentSendDelta));
			deltasMap.remove(currentSendDelta);
			currentSendDelta++;
		}

	}

}


