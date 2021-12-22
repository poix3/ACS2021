/**
 * 
 */
package com.acertainbookstore.client.workloads;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.BitmapEncoder.BitmapFormat;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.style.Styler.ChartTheme;

import com.acertainbookstore.business.CertainBookStore;
import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;

/**
 * 
 * CertainWorkload class runs the workloads by different workers concurrently.
 * It configures the environment for the workers using WorkloadConfiguration
 * objects and reports the metrics
 * 
 */
public class CertainWorkload {
	private static int numConcurrentWorkloadThreads = 10;
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		int numConcurrentWorkloadThreads = 10;
		String serverAddress = "http://localhost:8081";

		CertainBookStore store = new CertainBookStore();
		List<List<WorkerRunResult>> localResults = runWorkers(store, store, numConcurrentWorkloadThreads);

		// Bookstore HTTP Server needs to run otherwise won't be able to run Certain Workload
		StockManagerHTTPProxy stockManager = new StockManagerHTTPProxy(serverAddress + "/stock");
		BookStoreHTTPProxy bookStore = new BookStoreHTTPProxy(serverAddress);
		List<List<WorkerRunResult>> rpcResults = runWorkers(bookStore, stockManager, numConcurrentWorkloadThreads);

		// Finished initialization, stop the clients if not localTest
		bookStore.stop();
		stockManager.stop();

		reportMetric(localResults, rpcResults);
	}

	private static List<List<WorkerRunResult>> runWorkers(BookStore bookStore, StockManager stockManager,
														  int numConcurrentWorkloadThreads)
			throws Exception {
		// all workers' running results
		List<List<WorkerRunResult>> allWorkersRunResults = new ArrayList<>();
		// Generate data in the bookstore before running the workload
		initializeBookStoreData(bookStore, stockManager);

		ExecutorService exec = Executors
				.newFixedThreadPool(numConcurrentWorkloadThreads);

		for (int i = 0; i < numConcurrentWorkloadThreads; i++) {
			List<Future<WorkerRunResult>> runResults = new ArrayList<>();
			List<WorkerRunResult> workerRunResults = new ArrayList<>();

			// run experiment with i workers and save result
			for (int j = 0; j <= i; j++) {
				WorkloadConfiguration config = new WorkloadConfiguration(bookStore,
						stockManager);
				Worker workerTask = new Worker(config);
				// Keep the futures to wait for the result from the thread
				runResults.add(exec.submit(workerTask));
			}
			// Get the results from the threads using the futures returned
			for (Future<WorkerRunResult> futureRunResult : runResults) {
				WorkerRunResult runResult = futureRunResult.get(); // blocking call
				workerRunResults.add(runResult);
			}
			//Add the experiment data to the results
			allWorkersRunResults.add(workerRunResults);
			stockManager.removeAllBooks();
		}

		exec.shutdownNow(); // shutdown the executor
		return allWorkersRunResults;
	}

	/**
	 * Computes the metrics and prints them
	 */
	public static void reportMetric(List<List<WorkerRunResult>> totalWorkersRunResults,
									List<List<WorkerRunResult>> rpcResults) throws IOException {


		List<Double> localLatency = new ArrayList<>();
		List<Double> localThroughput = new ArrayList<>();
		getMetricResults(totalWorkersRunResults, localLatency, localThroughput);
		List<Double> remoteLatency = new ArrayList<>();
		List<Double> remoteThroughput = new ArrayList<>();
		getMetricResults(rpcResults, remoteLatency, remoteThroughput);
		XYChart chart1 = createChart("Latency", localLatency, remoteLatency);
		chart1.setYAxisTitle("nanoseconds");
		chart1.setXAxisTitle("Number of threads");
		BitmapEncoder.saveBitmap(chart1, "latency", BitmapFormat.PNG);
		XYChart chart2 = createChart("Throughput", localThroughput, remoteThroughput);
		chart2.setYAxisTitle("Successful interactions per ns");
		chart2.setXAxisTitle("Number of threads");
		BitmapEncoder.saveBitmap(chart2, "throughput", BitmapFormat.PNG);
	}

	private static XYChart createChart(String title, List<Double> localData,
									   List<Double> remoteData) {

		double[] xLabels = IntStream.rangeClosed(1, numConcurrentWorkloadThreads).asDoubleStream().toArray();
		XYChart chart = new XYChartBuilder().width(600).height(400).theme(ChartTheme.GGPlot2).build();
		chart.setTitle(title);
		chart.addSeries("local", xLabels, localData.stream().mapToDouble(Double::doubleValue).toArray());
		chart.addSeries("remote", xLabels, remoteData.stream().mapToDouble(Double::doubleValue).toArray());
		chart.getStyler().setYAxisLogarithmic(true);
		return chart;
	}

	private static void getMetricResults(List<List<WorkerRunResult>> workerRunResults,
										 List<Double> latencyList, List<Double> throuputList) {
		long totalRunTime = 0;
		double aggregatedThroughPut = 0;
		double successfulInteractions;
		double elapsedTimeInNanoSecs;
		for (List<WorkerRunResult> workerRunResultList : workerRunResults) {
			for (WorkerRunResult workerRunResult : workerRunResultList) {
				successfulInteractions = workerRunResult.getSuccessfulInteractions();
				elapsedTimeInNanoSecs = workerRunResult.getElapsedTimeInNanoSecs();
				aggregatedThroughPut += successfulInteractions / elapsedTimeInNanoSecs;
				totalRunTime += workerRunResult.getElapsedTimeInNanoSecs();
			}
			double averageLatency = totalRunTime/ (double) workerRunResults.size();

			latencyList.add(averageLatency);
			throuputList.add(aggregatedThroughPut);
		}
	}

	/**
	 * Generate the data in bookstore before the workload interactions are run
	 * 
	 * Ignores the serverAddress if its a localTest
	 * 
	 */
	public static void initializeBookStoreData(BookStore bookStore,
			StockManager stockManager) throws BookStoreException {
		BookSetGenerator bookSetGenerator = new BookSetGenerator(false);
		// generate 1000 random books
		Set<StockBook> stockBooks = bookSetGenerator.nextSetOfStockBooks(1000);
		// ensure the books in the stock are only from the generated 1000 books
		stockManager.removeAllBooks();
		// add books
		stockManager.addBooks(stockBooks);

	}
}
