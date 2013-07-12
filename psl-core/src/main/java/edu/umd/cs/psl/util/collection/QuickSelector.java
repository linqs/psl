/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.umd.cs.psl.util.collection;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

/**
 * This object takes a list of Comparable objects as input and immediately partitions 
 * it into a median and the greater half and lesser half. Alternatively, it can select 
 * an arbitrary rank k instead of the k/n median rank. The computation (in the constructor)
 * costs O(n) expected time, and O(n^2) worst-case, using the quickselect algorithm
 * <p>
 * The input list must not contain any duplicate entries
 * 
 * @author Bert Huang
 */
public class QuickSelector<T extends Comparable<T>> {
	
	/**
	 * Constructor for median finding
	 * @param input Linked list of Comparable objects
	 */
	public QuickSelector(LinkedList<T> input) {
		this(input, input.size()/2);
	}
	
	/**
	 *  Constructor for k-selection
	 * @param input Linked list of Comparable objects
	 * @param index tarket index of selection
	 */
	public QuickSelector(LinkedList<T> input, int index) {
		greater = new LinkedList<T>();
		less = new LinkedList<T>();
		left = new LinkedList<T>();
		middle = new LinkedList<T>();
		right = new LinkedList<T>();
		list = new LinkedList<T>();
		list.addAll(input);
		k = index;
		value = null;
		
		this.quickSelect();
	}

	
	/**
	 * 
	 * @return list of elements from input that are greater than target entry 
	 * (median or selection)
	 */
	public LinkedList<T> getGreater() {
		return greater;
	}
	
	/**
	 * 
	 * @return list of elements from input that are less than target entry 
	 * (median or selection)
	 */
	public LinkedList<T> getLess() {
		return less;
	}
	
	/**
	 * 
	 * @return median or k-selected element
	 */
	public T getValue() {
		return value;
	}
	
	
	/**
	 * returns the median of (the first element of a list, the last element, and the 2nd element)
	 * @param list input list
	 * @return median of the three options
	 */
	private T medianOfThree(LinkedList<T> list) {
		if (list.size() < 3) 
			return list.peek();
			
		
		
		T a = list.getFirst();
		T b = list.getLast();
		// iterator order may not be first-to-last, so this is either the 2nd or 2nd-to-last 
		Iterator<T> iterator = list.iterator();
		iterator.next(); // skip one
		T c = iterator.next(); 
				
		// this can be further optimized
		if (a.compareTo(b) >= 0 && b.compareTo(c) >= 0 || a.compareTo(b) <= 0 && b.compareTo(c) <= 0)
			return b;
		if (b.compareTo(a) >= 0 && a.compareTo(c) >= 0 || b.compareTo(a) <= 0 && a.compareTo(c) <= 0)
			return a;
		if (a.compareTo(c) >= 0 && c.compareTo(b) >= 0 || a.compareTo(c) <= 0 && c.compareTo(b) <= 0)
			return c;
		
		// this should never happen
		System.out.println("Error");
		return null;
		
	}
	
	/**
	 * do the selection. Since we are maintaining the lists of the greater and lesser elements,
	 * this is easier to do non-recursively
	 */
	private void quickSelect() {
		boolean finished = false;
		
		while (!finished) {
			// select the pivot
			value = medianOfThree(list);

			// partition the list
			left.clear();
			middle.clear();
			right.clear();
			while (!list.isEmpty()) {
				T current = list.pop();

				if (current.compareTo(value) > 0) 
					right.add(current);
				else if (current.compareTo(value) < 0)
					left.add(current);
				else 
					middle.add(current);
			}

			// check which partition we want to use
			
			int numLess = less.size() + left.size();
			
			if (numLess <= k && numLess + middle.size() > k) {
				finished = true;
				less.addAll(left);
				greater.addAll(right);
			} else {
				if (numLess > k) { // use the left partition
					list = left;
					left = new LinkedList<T>();
					greater.addAll(right);
					greater.addAll(middle);
				} else {
					// use the right partition
					list = right;
					right = new LinkedList<T>();
					less.addAll(left);
					less.addAll(middle);
				}
			}
		}
	}
		
	private int k;
	private LinkedList<T> right;
	private LinkedList<T> left;
	private LinkedList<T> middle;
	private LinkedList<T> list;
	private LinkedList<T> greater;
	private LinkedList<T> less;
	private T value;
	
	public static void main(String [] args) {

		LinkedList<Double> testList = new LinkedList<Double>();
		
		Random rand = new Random();
		
		for (int i = 0; i < 200; i++) {
			testList.add(rand.nextDouble());
			//testList.add((double)(i % 100)); // for checking how the code handles duplicates
		}
		
		System.out.println("Input list");
		System.out.println(testList);
		System.out.println("Starting...");
		long start = System.currentTimeMillis();
		QuickSelector<Double> qs = new QuickSelector<Double>(testList);
		System.out.println("... Done in "+ (System.currentTimeMillis() - start) +" ms");
		
		System.out.println("median: " + qs.getValue());
		System.out.println("Less than: " + qs.getLess());
		System.out.println("Greater than: " + qs.getGreater());

		System.out.println("Number in lesser list: " + qs.getLess().size());
		System.out.println("Number in greater list: " + qs.getGreater().size());
		
		int count = 0;
		

		for (double x : qs.getLess()) 
			if (x < qs.getValue()) 
				count++;
		System.out.println(count + " entries of lesser list are less than " + qs.getValue());
		

		count = 0;
		for (double x : qs.getGreater()) 
			if (x > qs.getValue()) 
				count++;
		System.out.println(count + " entries of greater list are greater than " + qs.getValue());
		
	}
	
}
