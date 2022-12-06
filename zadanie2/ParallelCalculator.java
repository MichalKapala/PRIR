import java.util.*;
import java.util.concurrent.*;




class ParallelCalculator implements DeltaParallelCalculator {
	private ExecutorService executor;

	final private PriorityBlockingQueue<Integer> processingQueue = new PriorityBlockingQueue<>();

	final private Map<Integer, Data> receivedData = new ConcurrentHashMap<Integer, Data>();

	final private Map<Integer, Integer> comparedCounter = new ConcurrentHashMap<Integer, Integer>();

	final private Map<Integer, List<Delta>> deltasMap = new ConcurrentHashMap<Integer, List<Delta>>();

	private DeltaReceiver receiver;

	 volatile private int currentSendDelta = 0;

	@Override
	public void setThreadsNumber(int threads) {
		executor = new ThreadPoolExecutor(threads, threads, 0L,
				TimeUnit.MILLISECONDS, new PriorityBlockingQueue<Runnable>(1, new PriorityComparator())) {
			@Override
			protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
				RunnableFuture<T> newTaskFor = super.newTaskFor(callable);
				return new PriorityFuture<T>(newTaskFor, ((DataComparator) callable).getId());
			}
		};
	}

	@Override
	public void setDeltaReceiver(DeltaReceiver receiver) {
		this.receiver = receiver;
	}

	@Override
	public void addData(Data data) {
		receivedData.put(data.getDataId(), data);
		processElement(data.getDataId());

		while (!processingQueue.isEmpty()) {
			final int dataId = processingQueue.peek();
			processingQueue.remove(dataId);
			DataComparator comp = new DataComparator(receivedData.get(dataId), receivedData.get(dataId + 1));
			executor.submit(comp);
		}

	}

	private void processElement(int idx) {
		if (receivedData.containsKey(idx - 1)) {
			processingQueue.add(idx - 1);
		}

		if (receivedData.containsKey(idx + 1)) {
			processingQueue.add(idx);
		}
	}


	class DataComparator implements Callable<Long> {

		final private Data data1;
		final private Data data2;

		public DataComparator(Data data1, Data data2) {
			this.data1 = data1;
			this.data2 = data2;
		}


		@Override
		public Long call() {
				List<Delta> currentDeltaList = new ArrayList<>();
				for (int i = 0; i < data1.getSize(); i++) {
					if (data2.getValue(i) != data1.getValue(i)) {
						Delta delta = new Delta(data1.getDataId(), i, data2.getValue(i) - data1.getValue(i));
						currentDeltaList.add(delta);
					}
				}

				deltasMap.put(data1.getDataId(), currentDeltaList);
				sendDiff();
				increaseComparedCtr(data1.getDataId());
				increaseComparedCtr(data1.getDataId() + 1);

				return null;
		}

		// Concurent
		private void increaseComparedCtr(int idx1) {
			comparedCounter.merge(idx1, 1, Integer::sum);

			if (comparedCounter.get(idx1) >= 2) {
				receivedData.remove(idx1);
				comparedCounter.remove(idx1);
			}
		}

		// Concurent
		 private void sendDiff() {
			synchronized (deltasMap)
			{
				while (deltasMap.get(currentSendDelta) != null) {
					receiver.accept(deltasMap.get(currentSendDelta));
					deltasMap.remove(currentSendDelta);
					currentSendDelta++;
				}
			}
		}

		public int getId() {
			return data1.getDataId();
		}
	}

	static class PriorityFuture<T> implements RunnableFuture<T> {

		private final RunnableFuture<T> baseFuture;
		private final int id;

		public PriorityFuture(RunnableFuture<T> future, int id) {
			this.baseFuture = future;
			this.id = id;
		}

		public int getId() {
			return id;
		}

		@Override
		public boolean cancel(boolean interruptIfRun) {
			return baseFuture.cancel(interruptIfRun);
		}
		@Override
		public boolean isCancelled() {
			return baseFuture.isCancelled();
		}
		@Override
		public boolean isDone() {
			return baseFuture.isDone();
		}
		@Override
		public T get() throws InterruptedException, ExecutionException {
			return baseFuture.get();
		}
		@Override
		public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
			return baseFuture.get();
		}
		@Override
		public void run() {
			baseFuture.run();
		}
	}

	static class PriorityComparator implements Comparator<Runnable> {
		public int compare(Runnable comp1, Runnable comp2) {
			if (comp1 == null && comp2 == null)
				return 0;
			else if (comp1 == null)
				return -1;
			else if (comp2 == null)
				return 1;
			else {
				return Integer.compare(((PriorityFuture<?>) comp1).getId(), ((PriorityFuture<?>) comp2).getId());
			}
		}
	}
}



