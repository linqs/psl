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
package edu.umd.cs.psl.reasoner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;

import de.mathnbits.util.KeyedRetrievalSet;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;

/**
 * An abstract superclass for reasoners implemented as command-line executables.
 * 
 * Ground models are provided to the executable and results are read via
 * temporary text files.
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
abstract public class ExecutableReasoner implements Reasoner {
	
	private static final Logger log = LoggerFactory.getLogger(ExecutableReasoner.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "executablereasoner";
	
	/**
	 * Key for String property which is path to reasoner executable.
	 * 
	 * This is the rare PSL property that is mandatory to specify.
	 */
	public static final String EXECUTABLE_KEY = CONFIG_PREFIX + ".executable";
	
	/** Ground kernels defining the objective function */
	protected KeyedRetrievalSet<Kernel, GroundKernel> groundKernels;
	
	protected final String executable;
	
	public ExecutableReasoner(ConfigBundle config) {
		executable = config.getString(EXECUTABLE_KEY, "");
		if (executable.equals(""))
			throw new IllegalArgumentException("Must specify executable.");
		
		groundKernels = new KeyedRetrievalSet<Kernel, GroundKernel>();
	}

	@Override
	public void optimize() {
		log.debug("Writing model file.");
		File modelFile = new File(getModelFileName());
		try {
			BufferedWriter modelWriter = new BufferedWriter(new FileWriter(modelFile));
			writeModel(modelWriter);
			modelWriter.close();
		}
		catch (IOException e) {
			throw new Error("IOException when writing model file.", e);
		}
		
		log.debug("Finished writing model file. Calling reasoner.");
		try {
			callReasoner();
		}
		catch (IOException e) {
			throw new Error("IOException when calling reasoner.", e);
		}
		
		log.debug("Reasoner finished. Reading results file.");
		File resultsFile = new File(getResultsFileName());
		try {
			BufferedReader resultsReader = new BufferedReader(new FileReader(resultsFile));
			readResults(resultsReader);
			resultsReader.close();
		}
		catch (IOException e) {
			throw new Error("IOException when reading results file.", e);
		}
		
		log.debug("Finished reading results file.");
		modelFile.delete();
		resultsFile.delete();
	}
	
	protected void callReasoner() throws IOException {
		List<String> command = getArgs();
		command.add(0, executable);
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(true);
		Process proc = pb.start();
		BufferedReader stdout = new BufferedReader(new InputStreamReader(proc.getInputStream()));
		String line;
		while ((line = stdout.readLine()) != null)
			log.trace(line);
		stdout.close();
		int exitValue = -1;
		try {
			exitValue = proc.waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		//int exitValue = proc.exitValue();
		if (exitValue != 0)
			log.warn("Executable exited with unexpected value: {}", exitValue);
	}
	
	abstract protected List<String> getArgs();
	
	abstract protected void writeModel(BufferedWriter modelWriter) throws IOException;
	
	abstract protected void readResults(BufferedReader resultsReader) throws IOException;
	
	protected String getModelFileName() {
		return "model";
	}
	
	protected String getResultsFileName() {
		return "results";
	}

	@Override
	public void addGroundKernel(GroundKernel gk) {
		groundKernels.put(gk.getKernel(), gk);
	}

	@Override
	public void removeGroundKernel(GroundKernel gk) {
		groundKernels.remove(gk.getKernel(), gk);

	}

	@Override
	public boolean containsGroundKernel(GroundKernel gk) {
		return groundKernels.contains(gk.getKernel(), gk);
	}

	@Override
	public GroundKernel getGroundKernel(GroundKernel gk) {
		return groundKernels.get(gk.getKernel(), gk);
	}

	@Override
	public Iterable<GroundKernel> getGroundKernels() {
		return groundKernels;
	}

	@Override
	public Iterable<GroundCompatibilityKernel> getCompatibilityKernels() {
		return Iterables.filter(groundKernels, GroundCompatibilityKernel.class);
	}
	
	public Iterable<GroundConstraintKernel> getConstraintKernels() {
		return Iterables.filter(groundKernels, GroundConstraintKernel.class);
	}

	@Override
	public Iterable<GroundKernel> getGroundKernels(Kernel k) {
		return groundKernels.keyIterable(k);
	}

	@Override
	public int size() {
		return groundKernels.size();
	}

	@Override
	public void changedGroundKernel(GroundKernel gk) {
		/* Intentionally empty */
	}

	@Override
	public void changedGroundKernelWeight(GroundCompatibilityKernel gk) {
		/* Intentionally empty */
	}

	@Override
	public void changedGroundKernelWeights() {
		/* Intentionally empty */
	}

	@Override
	public void close() {
		groundKernels = null;
	}

}
