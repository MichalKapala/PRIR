import java.util.*;
import java.util.concurrent.*;




class ParallelCalculator implements DeltaParallelCalculator {

	ForkJoinPool pool;

	final private PriorityBlockingQueue<Integer> processingQueue = new PriorityBlockingQueue<>();

	final private Map<Integer, Data> receivedData = new ConcurrentHashMap<>();

	final private Map<Integer, Integer> comparedCounter = new ConcurrentHashMap<>();

	final private Map<Integer, List<Delta>> deltasMap = new ConcurrentHashMap<>();

	private DeltaReceiver receiver;

	 volatile private int currentSendDelta = 0;

	 int threadsNo;

	@Override
	public void setThreadsNumber(int threads) {
		threadsNo = threads;
		pool = new ForkJoinPool(threadsNo);
		Thread waitThread = new Thread(() -> {

				while (true) {
						try {
							final int dataId;
							dataId = processingQueue.take();
							synchronized (receivedData) {
								DataComparator comp = new DataComparator(receivedData.get(dataId), receivedData.get(dataId + 1));
								comp.call();
							}
						} catch (InterruptedException e) {
							throw new RuntimeException(e);
						}
				}
			}

		);
		waitThread.start();

	}

	@Override
	public void setDeltaReceiver(DeltaReceiver receiver) {
		this.receiver = receiver;
	}

	@Override
	public void addData(Data data) {

			receivedData.put(data.getDataId(), data);
			processElement(data.getDataId());



	}

	private void processElement(int idx) {

			if (receivedData.containsKey(idx - 1)) {
				processingQueue.add(idx - 1);
			}

			if (receivedData.containsKey(idx + 1)) {
				processingQueue.put(idx);
			}
	}


	class DataComparator   {
		public class DifferenceCalculator extends RecursiveTask<List<Delta> > {
			final private int start;
			final private int end;

			public DifferenceCalculator(int start, int end) {
				this.start = start;
				this.end = end;
			}

			@Override
			protected List<Delta>  compute() {
				if (end - start <= 1) {

					List<Delta> currenLocalDeltaList = new ArrayList<>();
					for (int i = start; i < end; i++) {

							var v1 = data1.getValue(i);
							var v2 = data2.getValue(i);
							var id = data1.getDataId();
//							System.out.println("COMPARING " + id +" IDX " + i);

							if (v2 != v1) {
								Delta delta = new Delta(id, i, v2 - v1);
								currenLocalDeltaList.add(delta);
							}



					}
					return currenLocalDeltaList;
				} else {
					// Otherwise, split the set in half and calculate the differences in each half
					int middle = start + (end - start) / 2;
					DifferenceCalculator left = new DifferenceCalculator(start, middle);
					DifferenceCalculator right = new DifferenceCalculator(middle, end);

					// Start the calculations in the left and right halves in parallel
					left.fork();
					right.fork();

					List<Delta> retLeft = left.join();
					List<Delta> retRight = right.join();

					retLeft.addAll(retRight);
					return retLeft;
				}

			}
		}
		final private Data data1;
		final private Data data2;

		public DataComparator(Data data1, Data data2) {
			this.data1 = data1;
			this.data2 = data2;
		}



		public Long call() {

				List<Delta> result = pool.invoke(new DifferenceCalculator(0, data1.getSize()));

				deltasMap.put(data1.getDataId(), result);
				sendDiff();
				increaseComparedCtr(data1.getDataId());
				increaseComparedCtr(data1.getDataId() + 1);

				return null;
		}

		private void increaseComparedCtr(int idx1) {
			comparedCounter.merge(idx1, 1, Integer::sum);

			if (comparedCounter.get(idx1) >= 2) {
				receivedData.remove(idx1);
				comparedCounter.remove(idx1);
			}
		}

		 private void sendDiff() {
				while (deltasMap.get(currentSendDelta) != null) {
					receiver.accept(deltasMap.get(currentSendDelta));
					deltasMap.remove(currentSendDelta);
					currentSendDelta++;
				}
		}
	}

}



