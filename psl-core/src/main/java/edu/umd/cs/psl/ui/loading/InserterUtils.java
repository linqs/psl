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
package edu.umd.cs.psl.ui.loading;

import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cern.colt.Arrays;
import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.ui.data.file.util.DelimitedObjectConstructor;
import edu.umd.cs.psl.ui.data.file.util.LoadDelimitedData;

/**
 * Utility methods for common data-loading tasks.
 */
public class InserterUtils {
	
	private static final Logger log = LoggerFactory.getLogger(InserterUtils.class);
	

	public static void loadDelimitedData(final Inserter insert, String file, String delimiter) {
		LoadDelimitedData.loadTabData(file, new DelimitedObjectConstructor<String>(){

			@Override
			public String create(String[] data) {
				//assert data.length==length;
				insert.insert((Object[])data);
				return null;
			}

			@Override
			public int length() {
				return 0;
			}
			
		}, delimiter);
	}
	
	public static void loadDelimitedData(final Inserter insert, String file) {
		loadDelimitedData(insert,file,LoadDelimitedData.defaultDelimiter);
	}
	
	public static void loadDelimitedDataTruth(final Inserter insert, String file, String delimiter) {
		LoadDelimitedData.loadTabData(file, new DelimitedObjectConstructor<String>(){

			@Override
			public String create(String[] data) {
				double truth;
				try {
					truth = Double.parseDouble(data[data.length-1]);
				} catch (NumberFormatException e) {
					throw new AssertionError("Could not read truth value for data: " + Arrays.toString(data));
				}
				if (truth<0.0 || truth>1.0)
					throw new AssertionError("Illegal truth value encountered: " + truth);
				Object[] newdata = new Object[data.length-1];
				System.arraycopy(data, 0, newdata, 0, newdata.length);
				insert.insertValue(truth,newdata);
				return null;
			}

			@Override
			public int length() {
				return 0;
			}
			
		}, delimiter);
	}
	
	public static void loadDelimitedDataTruth(final Inserter insert, String file) {
		loadDelimitedDataTruth(insert,file,LoadDelimitedData.defaultDelimiter);
	}
	
	public static void loadDelimitedMultiData(final InserterLookup inserters, final int position, 
												String file, String delimiter) {
		LoadDelimitedData.loadTabData(file, new DelimitedObjectConstructor<String>(){

			@Override
			public String create(String[] data) {
				if (data.length<2 || data.length<=position) {
					log.error("The following data is illegal and therefore skipped: {}",data);
					return null;
				}
				Inserter insert = inserters.get(data[position]);
				if (insert==null) {
					log.error("Could not find inserter for [{}] and therefore skipped: {}",data[position],data);
					return null;
				}
				Object[] ins = new Object[data.length-1];
				int j=0;
				for (int i=0;i<data.length;i++) {
					if (i==position) continue;
					ins[j]=data[i];
					j++;
				}
				insert.insert(ins);
				return null;
			}

			@Override
			public int length() {
				return 0;
			}
			
		}, delimiter);
	}
	
	public static void loadDelimitedMultiData(final InserterLookup inserters, final int position, 
			String file) {
		loadDelimitedMultiData(inserters,position,file,LoadDelimitedData.defaultDelimiter);
	}
	
	/**
	 * Calls {{@link #loadFactTable(PredicateFactory, DataStore, String, Partition, String)} with
	 * default delimiter.
	 * 
	 * @param data      DataStore from which to create inserters
	 * @param file	    path to table file
	 * @param partition partition into which data will be inserted
	 */
	public static void loadFactTable(final DataStore data, String file, Partition partition) {
		loadFactTable(data, file, partition, LoadDelimitedData.defaultDelimiter);
	}
	
	/**
	 * Loads a table of facts. Each column in the table should be a column of entities.
	 * The first row should be predicate names. Cell (1,1) should be empty.
	 * 
	 * @param data      DataStore from which to create inserters
	 * @param file	    path to table file
	 * @param partition partition into which data will be inserted
	 * @param delimiter delimiter between columns in a row
	 */
	public static void loadFactTable(final DataStore data, String file, Partition partition,
			String delimiter) {
		Predicate p;
		PredicateFactory pf = PredicateFactory.getFactory();
		List<String[]> table = LoadDelimitedData.loadTabData(file, new DelimitedObjectConstructor<String[]>(){
			
			@Override
			public String[] create(String[] data) {
				return data;
			}

			@Override
			public int length() {
				return 0;
			}
			
		}, delimiter);
		
		String[] row = table.get(0);
		Inserter[] inserters = new Inserter[row.length-1];
		for (int i = 1; i < row.length; i++) {
			p = pf.getPredicate(row[i]);
			if (p != null) {
				if (p instanceof StandardPredicate) {
					inserters[i-1] = data.getInserter((StandardPredicate) p, partition);
				}
				else
					throw new IllegalStateException("Predicate '" + row[i] + "' is not a StandardPredicate.");
			}
			else
				throw new IllegalStateException("No predicate with name '" + row[i] + "' has been created.");
		}
		
		Iterator<String[]> itr = table.iterator();
		String[] newData = new String[2];
		itr.next();
		while (itr.hasNext()) {
			row = itr.next();
			newData[0] = row[0];
			for (int i = 1; i < row.length; i++) {
				newData[1] = row[i];
				inserters[i-1].insert((Object[]) newData);
			}
		}
	}
	
	/**
	 * Calls {{@link #loadFactIntersectionTable(Inserter, String, String)} with
	 * default delimiter.
	 * 
	 * @param insert	inserter for the predicate
	 * @param file		path to table file
	 */
	public static void loadFactIntersectionTable(final Inserter insert, String file) {
		loadFactIntersectionTable(insert, file, LoadDelimitedData.defaultDelimiter);
	}
	
	/**
	 * Loads facts from a table represented as delimited values in a file.
	 * Given an inserter for predicate P, inserts P(row, column) for each cell
	 * in the table, where row and column are headers of that cell's row and column,
	 * respectively. The predicate is added as a fact with the value in that cell.
	 * 
	 * @param insert		inserter for the predicate
	 * @param file			path to table file
	 * @param delimiter		delimiter separating columns in the file
	 */
	public static void loadFactIntersectionTable(final Inserter insert, String file, String delimiter) {
		LoadDelimitedData.loadTabData(file, new DelimitedObjectConstructor<String>(){
			
			boolean first = true;
			String[] headings;

			@Override
			public String create(String[] data) {
				if (first) {
					headings = data;
					first = false;
				}
				else {
					double fact;
					String[] newData = new String[2];
					newData[0] = data[0];
					for (int i = 1; i < data.length; i++) {
						if (!data[i].trim().equals("")) {
							try {
								fact = Double.parseDouble(data[i]);
							}
							catch (NumberFormatException e) {
								throw new AssertionError("Could not read fact value for data: "
									+ data[0] + ", " + headings[i] + " = " + data[i]);
							}
							newData[1] = headings[i];
							insert.insertValue(fact,(Object[]) newData);
						}
					}
				}
				return null;
			}

			@Override
			public int length() {
				return 0;
			}
			
		}, delimiter);
	}
	
	/**
	 * Calls {{@link #loadFactEntityIntersectionTable(Inserter, String, String)} with
	 * default delimiter.
	 * 
	 * @param insert	inserter for the predicate
	 * @param file		path to table file
	 */
	public static void loadFactEntityIntersectionTable(final Inserter insert, String file) {
		loadFactEntityIntersectionTable(insert, file, LoadDelimitedData.defaultDelimiter);
	}
	
	/**
	 * Loads facts from a table represented as delimited values in a file.
	 * Given an inserter for predicate P, inserts P(row, column, value) for each cell
	 * in the table, where row and column are headers of that cell's row and column,
	 * respectively, and value is the value in the cell.
	 * 
	 * @param insert		inserter for the predicate
	 * @param file			path to table file
	 * @param delimiter		delimiter separating columns in the file
	 */
	public static void loadFactEntityIntersectionTable(final Inserter insert, String file, String delimiter) {
		LoadDelimitedData.loadTabData(file, new DelimitedObjectConstructor<String>(){
			
			boolean first = true;
			String[] headings;

			@Override
			public String create(String[] data) {
				if (first) {
					headings = data;
					first = false;
				}
				else {
					String[] newData = new String[3];
					newData[0] = data[0];
					for (int i = 1; i < data.length; i++) {
						if (!data[i].trim().equals("")) {
							newData[1] = headings[i];
							newData[2] = data[i];
							insert.insert((Object[]) newData);
						}
					}
				}
				return null;
			}

			@Override
			public int length() {
				return 0;
			}
			
		}, delimiter);
	}
}
