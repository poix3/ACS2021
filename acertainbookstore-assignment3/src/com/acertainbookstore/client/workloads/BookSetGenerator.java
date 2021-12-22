package com.acertainbookstore.client.workloads;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.acertainbookstore.business.ImmutableStockBook;
import com.acertainbookstore.business.StockBook;

/**
 * Helper class to generate stockbooks and isbns modelled similar to Random
 * class
 */
public class BookSetGenerator {
	private static Integer ISBN = 1;
	private Random random;
	private boolean randomISBN;
	private NameGenerator nameGenerator;

	public BookSetGenerator(boolean randomISBN) {
		this.random = new Random();
		this.randomISBN = randomISBN;
		this.nameGenerator = new NameGenerator();
	}

	public Set<Integer> sampleFromSetOfISBNs(Set<Integer> isbns, int n) {
		List<Integer> l = new ArrayList<>(isbns);
		Collections.shuffle(l, new Random());
		return l.stream().limit(n).collect(Collectors.toSet());
	}

	public Set<StockBook> nextSetOfStockBooks(int num) {
		Set<StockBook> result = new HashSet<>();
		IntStream.rangeClosed(1, num).forEach(i -> result.add(createRandomBook()));
		return result;
	}

	public Integer getISBN() {
		return ISBN++;
	}

	private Integer getISBNRandomly() {
		return random.ints(1, 1, 5000).findAny().getAsInt();
	}

	private ImmutableStockBook createRandomBook() {
		int isbn = randomISBN ? getISBNRandomly() : getISBN();
		String title = nameGenerator.createName();
		String author = nameGenerator.createName();
		float price = random.nextFloat() * 100f;
		int numCopies = random.ints(1, 1, 1000).findAny().getAsInt();
		long numSaleMisses = random.longs(1, 0L, 100L).findAny().getAsLong();
		long numTimesRated = random.longs(1, 0L, 1000L).findAny().getAsLong();
		long totalRating = random.longs(1, 0L, 5L).findAny().getAsLong();
		boolean editorPick = random.nextBoolean();
		return new ImmutableStockBook(isbn, title, author, price, numCopies, numSaleMisses,
				numTimesRated, totalRating, editorPick);
	}
}
