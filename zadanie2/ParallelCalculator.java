import java.util.*;
import java.util.concurrent.*;


class PriorityFuture<T> implements RunnableFuture<T> {

	private final RunnableFuture<T> baseFuture;
	private final int id;

	public PriorityFuture(RunnableFuture<T> future, int id) {
		this.baseFuture = future;
		this.id = id;
	}

	public int getId() {
		return id;
	}

	public boolean cancel(boolean mayInterruptIfRunning) {
		return baseFuture.cancel(mayInterruptIfRunning);
	}

	public boolean isCancelled() {
		return baseFuture.isCancelled();
	}

	public boolean isDone() {
		return baseFuture.isDone();
	}

	public T get() throws InterruptedException, ExecutionException {
		return baseFuture.get();
	}

	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		return baseFuture.get();
	}

	public void run() {
		baseFuture.run();
	}
}

class PriorityFutureComparator implements Comparator<Runnable> {
	public int compare(Runnable comp1, Runnable comp2) {
		if (comp1 == null && comp2 == null)
			return 0;
		else if (comp1 == null)
			return -1;
		else if (comp2 == null)
			return 1;
		else {
			int p1 = ((PriorityFuture<?>) comp1).getId();
			int p2 = ((PriorityFuture<?>) comp2).getId();

			return Integer.compare(p1, p2);
		}
	}
}

class EnvironmentInfo {
	final  private Map<Integer, Data> receivedData = new ConcurrentHashMap<Integer, Data> ();

	final  private Map<Integer, Integer> comparedCounter = new ConcurrentHashMap<Integer, Integer> ();

	final private Map<Integer, List <Delta>> deltasMap = new ConcurrentHashMap<Integer, List <Delta>> ();

	private final DeltaReceiver receiver;

	volatile private Integer currentSendDelta = 0;

	EnvironmentInfo(DeltaReceiver receiver)
	{
		this.receiver = receiver;
	}

	void AddReceivedData(int idx, Data data)
	{
		receivedData.put(idx, data);
	}

	void AddComparedCtr(int idx)
	{
		comparedCounter.merge(idx, 1, Integer::sum);
	}


	void AddDeltaMap(int idx, List <Delta> currentDeltaList)
	{
		deltasMap.put(idx, currentDeltaList);
	}

	void IncreaseCurrentSentDelta() {

		currentSendDelta++;
	}

	void ReceiverAccept(List <Delta> deltas) {
		receiver.accept(deltas);
	}

	Map<Integer, Data> GetReceivedData()
	{
		return receivedData;
	}

	Map<Integer, Integer> GetComparedCtr()
	{
		return comparedCounter;
	}


	Map<Integer, List<Delta> > GetDeltaMap()
	{
		return deltasMap;
	}

	Integer GetCurrentSendDelta() {

		return currentSendDelta;
	}

}

class ParallelCalculator implements DeltaParallelCalculator
{
	private ExecutorService executor;

	final private PriorityBlockingQueue<Integer> processingQueue = new PriorityBlockingQueue<>();

	EnvironmentInfo envInfo;

	volatile int orderOfCome = 0;

	@Override
	public void setThreadsNumber(int threads)
	{
		executor = new ThreadPoolExecutor(threads, threads, 0L,
				TimeUnit.MILLISECONDS, new PriorityBlockingQueue<Runnable>(1, new PriorityFutureComparator()))
		{
			@Override
			protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
				RunnableFuture<T> newTaskFor = super.newTaskFor(callable);
				return new PriorityFuture<T>(newTaskFor, ((DataComparator) callable).getId());
			}
		};
	}

	@Override
	public void setDeltaReceiver(DeltaReceiver receiver)
	{
		envInfo = new EnvironmentInfo(receiver);
	}

	@Override
	public void addData(Data data)
	{
		envInfo.AddReceivedData(data.getDataId(), data);
		processElement(data.getDataId());

		while(!processingQueue.isEmpty())
		{
			final int dataId = processingQueue.peek();
			processingQueue.remove(dataId);
			DataComparator comp = new DataComparator(envInfo, dataId);
			executor.submit(comp);
		}

	}

	private void processElement(int idx)
	{
		if(envInfo.GetReceivedData().containsKey(idx - 1))
		{
			processingQueue.add(idx - 1);
		}

		if(envInfo.GetReceivedData().containsKey(idx + 1))
		{
			processingQueue.add(idx);
		}
	}

}
class DataComparator implements Callable<Long> {

	private EnvironmentInfo envInfo;
	private final int idx;


	public DataComparator(EnvironmentInfo envInfo, int idx){
		this.envInfo = envInfo;
		this.idx = idx;
	}

	public Long call() throws Exception {
		System.out.println("comparing " + idx);
		var data1 = envInfo.GetReceivedData().get(this.idx);
		var data2 = envInfo.GetReceivedData().get(this.idx + 1);
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

		envInfo.AddDeltaMap(idx, currentDeltaList);
		sendDiff();
		increaseComparedCtr(idx);
		increaseComparedCtr(idx + 1);

		return null;
	}

	// Concurent
	private void increaseComparedCtr(int idx1)
	{
		envInfo.AddComparedCtr(idx1);

		if(envInfo.GetComparedCtr().get(idx1) >= 2)
		{
			envInfo.GetReceivedData().remove(idx1);
			envInfo.GetComparedCtr().remove(idx1);
		}
	}

	// Concurent
	synchronized private void sendDiff()
	{
		while(envInfo.GetDeltaMap().get(envInfo.GetCurrentSendDelta()) != null)
		{
			envInfo.ReceiverAccept(envInfo.GetDeltaMap().get(envInfo.GetCurrentSendDelta()));
			envInfo.GetDeltaMap().remove(envInfo.GetCurrentSendDelta());
			envInfo.IncreaseCurrentSentDelta();
		}

	}
	public int getId() {
		return idx;
	}
}



