package com.acertainbookstore.client.tests;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.acertainbookstore.business.*;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;


/**
 * {@link BookStoreTest} tests the {@link BookStore} interface.
 * 
 * @see BookStore
 */
public class BookStoreTest {

	/** The Constant TEST_ISBN. */
	private static final int TEST_ISBN = 3044560;

	/** The Constant NUM_COPIES. */
	private static final int NUM_COPIES = 5;

	/** The local test. */
	private static boolean localTest = true;

	/** Single lock test */
	private static boolean singleLock = true;
	
	/** The store manager. */
	private static StockManager storeManager;

	/** The client. */
	private static BookStore client;

	/**
	 * Sets the up before class.
	 */
	@BeforeClass
	public static void setUpBeforeClass() {
		try {
			String localTestProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_LOCAL_TEST);
			localTest = (localTestProperty != null) ? Boolean.parseBoolean(localTestProperty) : localTest;
			
			String singleLockProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_SINGLE_LOCK);
			singleLock = (singleLockProperty != null) ? Boolean.parseBoolean(singleLockProperty) : singleLock;

			if (localTest) {
				if (singleLock) {
					SingleLockConcurrentCertainBookStore store = new SingleLockConcurrentCertainBookStore();
					storeManager = store;
					client = store;
				} else {
					TwoLevelLockingConcurrentCertainBookStore store = new TwoLevelLockingConcurrentCertainBookStore();
					storeManager = store;
					client = store;
				}
			} else {
				storeManager = new StockManagerHTTPProxy("http://localhost:8081/stock");
				client = new BookStoreHTTPProxy("http://localhost:8081");
			}

			storeManager.removeAllBooks();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Helper method to add some books.
	 *
	 * @param isbn
	 *            the isbn
	 * @param copies
	 *            the copies
	 * @throws BookStoreException
	 *             the book store exception
	 */
	public void addBooks(int isbn, int copies) throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		StockBook book = new ImmutableStockBook(isbn, "Test of Thrones", "George RR Testin'", (float) 10, copies, 0, 0,
				0, false);
		booksToAdd.add(book);
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Helper method to get the default book used by initializeBooks.
	 *
	 * @return the default book
	 */
	public StockBook getDefaultBook() {
		return new ImmutableStockBook(TEST_ISBN, "Harry Potter and JUnit", "JK Unit", (float) 10, NUM_COPIES, 0, 0, 0,
				false);
	}

	/**
	 * Method to add a book, executed before every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Before
	public void initializeBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(getDefaultBook());
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Method to clean up the book store, execute after every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@After
	public void cleanupBooks() throws BookStoreException {
		storeManager.removeAllBooks();
	}

	/**
	 * Tests basic buyBook() functionality.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyAllCopiesDefaultBook() throws BookStoreException {
		// Set of books to buy
		Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES));

		// Try to buy books
		client.buyBooks(booksToBuy);

		List<StockBook> listBooks = storeManager.getBooks();
		assertTrue(listBooks.size() == 1);
		StockBook bookInList = listBooks.get(0);
		StockBook addedBook = getDefaultBook();

		assertTrue(bookInList.getISBN() == addedBook.getISBN() && bookInList.getTitle().equals(addedBook.getTitle())
				&& bookInList.getAuthor().equals(addedBook.getAuthor()) && bookInList.getPrice() == addedBook.getPrice()
				&& bookInList.getNumSaleMisses() == addedBook.getNumSaleMisses()
				&& bookInList.getAverageRating() == addedBook.getAverageRating()
				&& bookInList.getNumTimesRated() == addedBook.getNumTimesRated()
				&& bookInList.getTotalRating() == addedBook.getTotalRating()
				&& bookInList.isEditorPick() == addedBook.isEditorPick());
	}

	/**
	 * Tests that books with invalid ISBNs cannot be bought.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyInvalidISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with invalid ISBN.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(-1, 1)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that books can only be bought if they are in the book store.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNonExistingISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with ISBN which does not exist.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(100000, 10)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy more books than there are copies.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyTooManyBooks() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy more copies than there are in store.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES + 1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy a negative number of books.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNegativeNumberOfBookCopies() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a negative number of copies.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that all books can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetBooks() throws BookStoreException {
		Set<StockBook> booksAdded = new HashSet<StockBook>();
		booksAdded.add(getDefaultBook());

		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		booksAdded.addAll(booksToAdd);

		storeManager.addBooks(booksToAdd);

		// Get books in store.
		List<StockBook> listBooks = storeManager.getBooks();

		// Make sure the lists equal each other.
		assertTrue(listBooks.containsAll(booksAdded) && listBooks.size() == booksAdded.size());
	}

	/**
	 * Tests that a list of books with a certain feature can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetCertainBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		storeManager.addBooks(booksToAdd);

		// Get a list of ISBNs to retrieved.
		Set<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN + 1);
		isbnList.add(TEST_ISBN + 2);

		// Get books with that ISBN.
		List<Book> books = client.getBooks(isbnList);

		// Make sure the lists equal each other
		assertTrue(books.containsAll(booksToAdd) && books.size() == booksToAdd.size());
	}

	/**
	 * Tests that books cannot be retrieved if ISBN is invalid.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetInvalidIsbn() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Make an invalid ISBN.
		HashSet<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN); // valid
		isbnList.add(-1); // invalid

		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.getBooks(isbnList);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * the following two methods are helper functions of test case 1
	 */

	/**
	 * buyBooks runnable
	 */
	public class BuyBooksRunnable implements Runnable {
		private Set<BookCopy> books;
		private int count;   //the number of the operations
		BuyBooksRunnable(int count, Set<BookCopy> bookCopies){
			this.count = count;
			this.books = bookCopies;
		}
		@Override
		public void run(){
			try {
				for (int i = 0; i < count; i++) {
					client.buyBooks(this.books);
				}
			} catch (BookStoreException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * addCopies runnable
	 */
	public class AddCopiesRunnable implements Runnable {
		private Set<BookCopy> books;
		private int count;
		AddCopiesRunnable(int count, Set<BookCopy> bookCopies) {
			this.count = count;
			this.books = bookCopies;
		}
		@Override
		public void run() {
			try {
				for (int i = 0; i < count; i++) {
					storeManager.addCopies(this.books);
				}
			} catch (BookStoreException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Test 1
	 *
	 * @throws BookStoreException
	 * 				the book store exception
	 */
	@Test
	public void testCase1() throws BookStoreException {
		// to add a sufficient number of copies in stock
		addBooks(TEST_ISBN + 1, NUM_COPIES*20);
		addBooks(TEST_ISBN + 2,NUM_COPIES*20);

		// books to buy and to add
		Set<BookCopy> books = new HashSet<>();
		books.add(new BookCopy(TEST_ISBN + 1,NUM_COPIES));
		books.add(new BookCopy(TEST_ISBN + 2, NUM_COPIES));

		Thread c1 = new Thread(new BuyBooksRunnable(10,books));
		Thread c2 = new Thread(new AddCopiesRunnable(10,books));

		c1.start();
		c2.start();

		try {
			c1.join();
			c2.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		Set<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN + 1);
		isbnList.add(TEST_ISBN + 2);
		// get books' information
		List<StockBook> booksInStore = storeManager.getBooksByISBN(isbnList);

		// to check the number of copies in stock
		assertEquals(100, booksInStore.get(0).getNumCopies());
		assertEquals(100, booksInStore.get(1).getNumCopies());
	}

	/**
	 * the following two methods are helper functions of test case 2
	 */

	/**
	 * buyBooks and addCopies runnable
	 */
	public class BuyAndAddCopiesRunnable implements Runnable {
		private Set<BookCopy> books;
		private int count;
		BuyAndAddCopiesRunnable(int count, Set<BookCopy> bookCopies) {
			this.count = count;
			this.books = bookCopies;
		}
		@Override
		public void run() {
			try {
				for (int i = 0; i < this.count; i++) {
					client.buyBooks(this.books);
					storeManager.addCopies(this.books);
				}
			} catch (BookStoreException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * check snapshotError runnable
	 */
	public class CheckSnapShotRunnable implements Runnable {
		private int count;
		/** Error for checking if snapshot is wrong */
		private static boolean snapshotError = false;

		CheckSnapShotRunnable(int count) {
			this.count = count;
		}

		public static boolean getError(){
			return snapshotError;
		}
		@Override
		public void run() {
			for (int i = 0; i < this.count; i++) {
				List<StockBook> books = new ArrayList<>();
				try {
					books = storeManager.getBooks();
				} catch (BookStoreException e) {
					snapshotError = true;
					e.printStackTrace();
				}
				for (StockBook book : books) {
					if (!(book.getNumCopies() == NUM_COPIES || book.getNumCopies() == 0))  snapshotError = true;
				}
			}
		}
	}

	/**
	 * Test 2
	 *
	 * @throws BookStoreException
	 * 				the book store exception
	 */
	@Test
	public void testCase2() throws BookStoreException {
		addBooks(TEST_ISBN+1,NUM_COPIES);
		addBooks(TEST_ISBN+2,NUM_COPIES);

		Set<BookCopy> books = new HashSet<>();
		books.add(new BookCopy(TEST_ISBN+1, NUM_COPIES));
		books.add(new BookCopy(TEST_ISBN+2, NUM_COPIES));

		Thread c1 = new Thread(new BuyAndAddCopiesRunnable(10,books));
		Thread c2 = new Thread(new CheckSnapShotRunnable(10));

		c1.start();
		c2.start();

		try {
			c1.join();
			c2.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		assertFalse(CheckSnapShotRunnable.getError());
	}

	/**
	 * the following two methods are helper functions of test case 3
	 */

	private class UpdateEditorPicksRunnable implements Runnable{
		private final int count;
		Set<BookEditorPick> editorPicks1;
		Set<BookEditorPick> editorPicks2;

		UpdateEditorPicksRunnable(int count,Set<BookEditorPick> editorPicks1,Set<BookEditorPick> editorPicks2){
			this.count = count;
			this.editorPicks1 = editorPicks1;
			this.editorPicks2 = editorPicks2;
		}
		@Override
		public void run(){
			try{
				for(int i = 0;i < this.count; i++){
					storeManager.updateEditorPicks(editorPicks1);
					storeManager.updateEditorPicks(editorPicks2);
				}
			}catch (BookStoreException e){
				e.printStackTrace();
			}
		}
	}

	private static class CheckSnapShotRunnable2 implements Runnable{
		private final int count;
		private static boolean snapshotError = false;

		CheckSnapShotRunnable2 (int count){
			this.count = count;
		}

		public static boolean getError(){
			return snapshotError;
		}

		@Override
		public void run(){
			for(int i = 0;i < this.count; i++){
				List<Book> books = new ArrayList<>();

				try{
					books = client.getEditorPicks(1);
				}catch (BookStoreException e){
					snapshotError = true;
					e.printStackTrace();
				}
				if (!(books.get(0).getISBN() == TEST_ISBN || books.get(0).getISBN() == TEST_ISBN+1)) {
					snapshotError = true;
				}
			}
		}
	}

	/**
	 * Test 3: c1 invokes updateEditorPicks to pick book1 and do not pick book2,
	 * and then invokes updateEditorPicks to pick book2 and do not pick book1
	 *  c2 continuously calls getEditorPicks(1) and ensures that the snapshot returned either has the book1 was picked or
	 *  the book2 was picked
	 *
	 * @throws BookStoreException
	 * 				the book store exception
	 */
	@Test
	public void testCase3() throws BookStoreException{

		addBooks(TEST_ISBN+1,NUM_COPIES);

		Set<BookEditorPick> editorPicks1=new HashSet<>();
		Set<BookEditorPick> editorPicks2=new HashSet<>();

		editorPicks1.add(new BookEditorPick(TEST_ISBN,true));
		editorPicks1.add(new BookEditorPick(TEST_ISBN+1,false));

		storeManager.updateEditorPicks(editorPicks1);

		editorPicks2.add(new BookEditorPick(TEST_ISBN,false));
		editorPicks2.add(new BookEditorPick(TEST_ISBN+1,true));

		Thread t1= new Thread(new UpdateEditorPicksRunnable(10,editorPicks1,editorPicks2));
		Thread t2=new Thread(new CheckSnapShotRunnable2(10));

		t1.start();
		t2.start();

		try {
			t1.join();
			t2.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		assertFalse(CheckSnapShotRunnable2.getError());
	}

	/**
	 * Test 4 c1 buy and add the same number of copies, c2 add the same number of copies
	 *
	 * @throws BookStoreException
	 * 				the book store exception
	 */
	@Test
	public void testCase4() throws BookStoreException {

		addBooks(TEST_ISBN+1,NUM_COPIES);
		addBooks(TEST_ISBN+2,NUM_COPIES);

		Set<BookCopy> books = new HashSet<>();
		books.add(new BookCopy(TEST_ISBN+1, NUM_COPIES));
		books.add(new BookCopy(TEST_ISBN+2, NUM_COPIES));

		Thread c1 = new Thread(new BuyAndAddCopiesRunnable(10,books));
		Thread c2 = new Thread(new AddCopiesRunnable(10,books));

		c1.start();
		c2.start();

		try {
			c1.join();
			c2.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		Set<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN + 1);
		isbnList.add(TEST_ISBN + 2);
		// get the books' information
		List<StockBook> booksInStore = storeManager.getBooksByISBN(isbnList);

		int numberOfCopies = 10*5+5;
		assertEquals(numberOfCopies, booksInStore.get(0).getNumCopies());
		assertEquals(numberOfCopies, booksInStore.get(1).getNumCopies());
	}

	/**
	 * Tear down after class.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws BookStoreException {
		storeManager.removeAllBooks();

		if (!localTest) {
			((BookStoreHTTPProxy) client).stop();
			((StockManagerHTTPProxy) storeManager).stop();
		}
	}
}
