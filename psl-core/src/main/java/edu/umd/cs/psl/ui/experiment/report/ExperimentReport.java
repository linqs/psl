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
package edu.umd.cs.psl.ui.experiment.report;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.common.base.Preconditions;

import de.mathnbits.io.DirectoryUtils;
import de.mathnbits.io.TextFiles;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.model.Model;

public class ExperimentReport {

	private final static String modelFilename = "model";
	//private final static String configurationFilename = "configuration";
	private final static String resultsFilename = "results";
	private final static String extension = ".txt";
	
	private final File directory;
	private final Writer results;
	
	private final boolean mirrorOutput;
	
	public ExperimentReport(File baseDir) {
		this(baseDir,"",false);
	}
	
	public ExperimentReport(String baseDir) {
		this(new File(baseDir));
	}
	
	public ExperimentReport(String baseDir, boolean mirror) {
		this(new File(baseDir),"",mirror);
	}
	
	public ExperimentReport(String baseDir,String experimentID) {
		this(new File(baseDir),experimentID,false);
	}
	
	public ExperimentReport(String baseDir,String experimentID, boolean mirror) {
		this(new File(baseDir),experimentID,mirror);
	}
	
	public ExperimentReport(File baseDir, String experimentID, boolean mirror) {
		Preconditions.checkArgument(baseDir.isDirectory());
		mirrorOutput = mirror;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
		if (experimentID==null) experimentID="";
		if (!experimentID.isEmpty()) experimentID= "-" + experimentID;
		String absResultsDir = baseDir.getAbsolutePath() + File.separator + sdf.format(new Date()) + experimentID +  File.separator;
		DirectoryUtils.mkDirException(absResultsDir);
		directory = new File(absResultsDir);
		assert directory.isDirectory();
	    try {
			results = new BufferedWriter(new FileWriter(absResultsDir + resultsFilename + extension));
		} catch (IOException e) {
			throw new AssertionError("Could not create results file: " + e);
		}
	}
	
	public void writeResult(String res) {
		try {
			results.write(res);
			if (mirrorOutput) System.out.print(res);
			results.flush();
		} catch (IOException e) {
			throw new AssertionError("Could not write to results file: " + e);
		}
	}
	
	public void writeResultLn(String res) {
		try {
			results.write(res+"\n");
			if (mirrorOutput) System.out.println(res);
			results.flush();
		} catch (IOException e) {
			throw new AssertionError("Could not write to results file: " + e);
		}
	}
	
	public void writeResultLn(Object res) {
		writeResultLn(res.toString());
	}
	
	public void writeResult(Object res) {
		writeResult(res.toString());
	}
	
	public void close() {
		try {
			results.close();
		} catch (IOException e) {
			throw new AssertionError("Could not close results file: " + e);
		}
	}
	
	public void writeModel(Model model) {
		writeModel(model,"");
	}
	
	public void writeModel(Model model, String id) {
	    TextFiles.writeObject2File(directory.getAbsolutePath() + File.separator + modelFilename + id + extension, model);
	}
	
	public void writeConfiguration(ConfigBundle config, String filename) {
		// TODO: write out config bundle as properties text file
		throw new UnsupportedOperationException();
	}
	
	public <O> void writeObject(String filename, O obj) {
		TextFiles.writeObject2File(directory.getAbsolutePath() + File.separator + filename + extension, obj);
	}
	
}
