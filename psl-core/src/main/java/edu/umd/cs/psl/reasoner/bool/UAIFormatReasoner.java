/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package edu.umd.cs.psl.reasoner.bool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.reasoner.ExecutableReasoner;
import edu.umd.cs.psl.util.model.ConstraintBlocker;

/**
 * Reasoner that performs inferences as a Boolean MRF using a command-line
 * executable that supports the UAI format
 * (http://www.cs.huji.ac.il/project/PASCAL/fileFormat.php).
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class UAIFormatReasoner extends ExecutableReasoner {
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "uaiformatreasoner";

	/** Options for supported UAI format inference tasks */
	public enum Task {
		/** Infer MPE state */
		MPE,
		/** Infer marginal probability that each Boolean variable is 1 */
		MAR
	}
	
	/**
	 * Key for Task enum property which is reasoner task to perform.
	 */
	public static final String TASK_KEY = CONFIG_PREFIX + ".task";
	/** Default value for TASK_KEY property (MPE) */
	public static final Task TASK_DEFAULT = Task.MPE;
	
	/**
	 * Key for integer property which is random seed for reasoner
	 */
	public static final String SEED_KEY = CONFIG_PREFIX + ".seed";
	/** Default value for SEED_KEY property */
	public static final int SEED_DEFAULT = 0;
	
	private final Task task;
	private final int seed;
	private ConstraintBlocker conBlocker;
	
	public UAIFormatReasoner(ConfigBundle config) {
		super(config);
		task = (Task) config.getEnum(TASK_KEY, TASK_DEFAULT);
		seed = config.getInt(SEED_KEY, SEED_DEFAULT);
		conBlocker = new ConstraintBlocker(this);
	}

	@Override
	protected List<String> getArgs() {
		List<String> args = new ArrayList<String>();
		
		args.add(getModelFileName());
		args.add("no.evid");
		args.add(Integer.toString(seed));
		args.add(task.toString());
		
		return args;
	}
	
	@Override
	protected void callReasoner() throws IOException {
		/* Creates an empty evidence file */
		File evidenceFile = new File("no.evid");
		FileWriter evidenceWriter = new FileWriter(evidenceFile);
		evidenceWriter.write("0");
		evidenceWriter.close();
		super.callReasoner();
		evidenceFile.delete();
	}
	
	@Override
	protected void writeModel(BufferedWriter modelWriter) throws IOException {
		conBlocker.prepareBlocks(true);
		RandomVariableAtom[][] rvBlocks = conBlocker.getRVBlocks();
		Map<RandomVariableAtom, Integer> rvMap = conBlocker.getRVMap();
		boolean[] exactlyOne = conBlocker.getExactlyOne();
		
		modelWriter.write("MARKOV\n");
		
		/* Writes out number of variables and each cardinality */
		modelWriter.write(conBlocker.getRVBlocks().length + "\n");
		for (int i = 0; i < rvBlocks.length; i++) {
			modelWriter.write(Integer.toString(exactlyOne[i] ? rvBlocks[i].length : rvBlocks[i].length + 1));
			if (i < rvBlocks.length - 1)
				modelWriter.write(" ");
		}
		modelWriter.write("\n");
		
		/* Collects list of potentials */
		List<GroundCompatibilityKernel> gcks = new ArrayList<GroundCompatibilityKernel>();
		for (GroundCompatibilityKernel gck : getCompatibilityKernels())
			gcks.add(gck);
		
		/* Writes out number of potentials and indices of participating variables */
		modelWriter.write(gcks.size() + "\n");
		List<Integer> vars = new ArrayList<Integer>();
		for (GroundCompatibilityKernel gck : gcks) {
			for (GroundAtom atom : gck.getAtoms()) {
				if (atom instanceof RandomVariableAtom)
					vars.add(rvMap.get(atom));
			}
			Collections.sort(vars);
			if (vars.size() > 0) {
				modelWriter.write(Integer.toString(vars.size()));
				for (Integer var : vars)
					modelWriter.write(" " + var);
			}
			modelWriter.write("\n");
			vars.clear();
		}
		
		/* Writes out potential tables */
		for (GroundCompatibilityKernel gck : gcks) {
			modelWriter.write("\n");
			for (GroundAtom atom : gck.getAtoms()) {
				if (atom instanceof RandomVariableAtom)
					vars.add(rvMap.get(atom));
			}
			Collections.sort(vars);
			
			/* Computes and writes number of table entries */
			int entries = 1;
			for (int i = 0; i < vars.size(); i++) {
				entries *= exactlyOne[vars.get(i)] ? rvBlocks[vars.get(i)].length : rvBlocks[vars.get(i)].length + 1;
			}
			modelWriter.write(Integer.toString(entries) + "\n");
			
			/* Computes and writes each table entry */
			int[] currentEntry = new int[vars.size()];
			for (int i = 0; i < entries; i++) {
				/* Assigns variables to current entry */
				for (int j = 0; j < vars.size(); j++) {
					/* Just zeroes everything out first */
					for (int k = 0; k < rvBlocks[vars.get(j)].length; k++) {
						rvBlocks[vars.get(j)][k].setValue(0.0);
					}
					
					/* 
					 * If it is the first state and the sum does not have to be one,
					 * leave as all zeros. Otherwise, flips the correct bit.
					 */
					if (exactlyOne[vars.get(j)] || currentEntry[j] != 0) {
						rvBlocks[vars.get(j)][(exactlyOne[vars.get(j)]) ? currentEntry[j] : currentEntry[j] - 1].setValue(1.0);
					}
				}
				
				/* Computes and writes (unnormalized) probability */
				double p = gck.getWeight().getWeight() * gck.getIncompatibility();
				p = Math.exp(-1 * p);
				modelWriter.write(" " + p);
				
				/* Updates current entry */
				for (int j = 0; j < currentEntry.length; j++) {
					currentEntry[j]++;
					if (currentEntry[j] == (exactlyOne[vars.get(j)] ? rvBlocks[vars.get(j)].length : rvBlocks[vars.get(j)].length + 1)) {
						currentEntry[j] = 0;
					}
					else {
						break;
					}
				}
			}
			modelWriter.write("\n");
			vars.clear();
		}
	}
	
	@Override
	protected void readResults(BufferedReader resultsReader) throws IOException {
		RandomVariableAtom[][] rvBlocks = conBlocker.getRVBlocks();
		boolean[] exactlyOne = conBlocker.getExactlyOne();
		
		String line = resultsReader.readLine();
		if (!line.equals(task.toString()))
			throw new IllegalStateException("Results file is not for specified task.");
		
		/* Some UAI solvers print multiple solutions. Gets just the last one. */
		boolean readNextSolution = true;
		String solution = "";
		while (readNextSolution) {
			line = resultsReader.readLine();
			if (line.equals("1")) {
				solution = resultsReader.readLine();
				if (resultsReader.readLine() == null)
					readNextSolution = false;
			}
			else {
				throw new IllegalStateException("Results file contains multiple assignments in a single solution.");
			}
		}
		
		/* Parses the solution string */
		String[] assignments = solution.split(" ");
		for (int i = 0; i < assignments.length - 1; i++) {
			int assignment = Integer.parseInt(assignments[i+1]);
			
			/* First zeros out everything */
			for (int k = 0; k < rvBlocks[i].length; k++) {
				rvBlocks[i][k].setValue(0.0);
			}
			
			/* 
			 * If it is the first state and the sum does not have to be one,
			 * leave as all zeros. Otherwise, flips the correct bit.
			 */
			if (exactlyOne[i] || assignment != 0) {
				rvBlocks[i][(exactlyOne[i]) ? assignment : assignment - 1].setValue(1.0);
			}
		}
	}
	
	@Override
	protected String getModelFileName() {
		return "model.uai";
	}
	
	@Override
	protected String getResultsFileName() {
		return "model.uai." + task.toString();
	}
	
	@Override
	public void close() {
		super.close();
		conBlocker = null;
	}

}
