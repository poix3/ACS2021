/**
 * 
 */
package com.acertainbookstore.client.workloads;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.acertainbookstore.business.Book;
import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreException;

/**
 * 
 * Worker represents the workload runner which runs the workloads with
 * parameters using WorkloadConfiguration and then reports the results
 * 
 */
public class Worker implements Callable<WorkerRunResult> {
    private WorkloadConfiguration configuration = null;
    private int numSuccessfulFrequentBookStoreInteraction = 0;
    private int numTotalFrequentBookStoreInteraction = 0;

    public Worker(WorkloadConfiguration config) {
	configuration = config;
    }

    /**
     * Run the appropriate interaction while trying to maintain the configured
     * distributions
     * 
     * Updates the counts of total runs and successful runs for customer
     * interaction
     * 
     * @param chooseInteraction
     * @return
     */
    private boolean runInteraction(float chooseInteraction) {
	try {
	    float percentRareStockManagerInteraction = configuration.getPercentRareStockManagerInteraction();
	    float percentFrequentStockManagerInteraction = configuration.getPercentFrequentStockManagerInteraction();

	    if (chooseInteraction < percentRareStockManagerInteraction) {
		runRareStockManagerInteraction();
	    } else if (chooseInteraction < percentRareStockManagerInteraction
		    + percentFrequentStockManagerInteraction) {
		runFrequentStockManagerInteraction();
	    } else {
		numTotalFrequentBookStoreInteraction++;
		runFrequentBookStoreInteraction();
		numSuccessfulFrequentBookStoreInteraction++;
	    }
	} catch (BookStoreException ex) {
	    return false;
	}
	return true;
    }

    /**
     * Run the workloads trying to respect the distributions of the interactions
     * and return result in the end
     */
    public WorkerRunResult call() throws Exception {
	int count = 1;
	long startTimeInNanoSecs = 0;
	long endTimeInNanoSecs = 0;
	int successfulInteractions = 0;
	long timeForRunsInNanoSecs = 0;

	Random rand = new Random();
	float chooseInteraction;

	// Perform the warmup runs
	while (count++ <= configuration.getWarmUpRuns()) {
	    chooseInteraction = rand.nextFloat() * 100f;
	    runInteraction(chooseInteraction);
	}

	count = 1;
	numTotalFrequentBookStoreInteraction = 0;
	numSuccessfulFrequentBookStoreInteraction = 0;

	// Perform the actual runs
	startTimeInNanoSecs = System.nanoTime();
	while (count++ <= configuration.getNumActualRuns()) {
	    chooseInteraction = rand.nextFloat() * 100f;
	    if (runInteraction(chooseInteraction)) {
		successfulInteractions++;
	    }
	}
	endTimeInNanoSecs = System.nanoTime();
	timeForRunsInNanoSecs += (endTimeInNanoSecs - startTimeInNanoSecs);
	return new WorkerRunResult(successfulInteractions, timeForRunsInNanoSecs, configuration.getNumActualRuns(),
		numSuccessfulFrequentBookStoreInteraction, numTotalFrequentBookStoreInteraction);
    }

	/**
	 * Runs the new stock acquisition interaction
	 *
	 * @throws BookStoreException
	 */
	private void runRareStockManagerInteraction() throws BookStoreException {
		StockManager stockManager = configuration.getStockManager();
		BookSetGenerator bookSetGenerator = configuration.getBookSetGenerator();

		List<StockBook> stockBookList = stockManager.getBooks();
		// get stockBooks' isbns
		List<Integer> booksListIsbns = stockBookList.stream().map(Book::getISBN).collect(Collectors.toList());

		// get a random set of books
		Set<StockBook> randomBooks = bookSetGenerator.nextSetOfStockBooks(configuration.getNumBooksToAdd());
		List<StockBook> booksToAddList = new ArrayList<StockBook> (randomBooks);

		// check if the set of ISBNs is in the list of books fetched
		Set<StockBook> missedBooks = booksToAddList.stream().filter(book -> !booksListIsbns.contains(book.getISBN()))
				.collect(Collectors.toSet());

		stockManager.addBooks(missedBooks);
	}

	/**
	 * Runs the stock replenishment interaction
	 *
	 * @throws BookStoreException
	 */
	private void runFrequentStockManagerInteraction() throws BookStoreException {
		StockManager stockManager = configuration.getStockManager();
		// the smallest quantities in stock
		int numberOfLeastCopies = configuration.getNumBooksWithLeastCopies();
		int numberOfCopiesToAdd = configuration.getNumAddCopies();
		// set of bookCopies need to be added
		Set<BookCopy> bookCopiesToAdd = new HashSet<>();

		List<StockBook> stockBookList = stockManager.getBooks();

		// get the bookCopiesToAdd
		stockBookList.stream().sorted(Comparator.comparing(StockBook::getNumCopies).reversed())
				.limit(numberOfLeastCopies).forEach(stockBook -> bookCopiesToAdd
						.add(new BookCopy(stockBook.getISBN(), numberOfCopiesToAdd)));

		stockManager.addCopies(bookCopiesToAdd);
	}

	/**
	 * Runs the customer interaction
	 *
	 * @throws BookStoreException
	 */
	private void runFrequentBookStoreInteraction() throws BookStoreException {
		BookStore bookStore = configuration.getBookStore();
		BookSetGenerator bookSetGenerator = configuration.getBookSetGenerator();
		int numberOfEditorPicks = configuration.getNumEditorPicksToGet();

		// get some editorPicks books
		List<Book> editorPicksBooks = bookStore.getEditorPicks(numberOfEditorPicks);
		Set<Integer> booksISBN = editorPicksBooks.stream().map(Book::getISBN).collect(Collectors.toSet());

		Set<BookCopy> booksToBuy = new HashSet<>();
		Set<Integer> subSetOfPicksBooks = bookSetGenerator.sampleFromSetOfISBNs(booksISBN, configuration.getNumBooksToBuy());
		// get books to buy
		subSetOfPicksBooks.forEach(isbn -> booksToBuy.add(new BookCopy(isbn, configuration.getNumBookCopiesToBuy())));

		bookStore.buyBooks(booksToBuy);
	}

}
