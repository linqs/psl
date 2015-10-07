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
package edu.umd.cs.psl.application.topicmodel;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.application.topicmodel.kernel.LDAgroundLogLoss;
import edu.umd.cs.psl.application.topicmodel.reasoner.admm.LatentTopicNetworkADMMReasoner;
import edu.umd.cs.psl.application.util.GroundKernels;
import edu.umd.cs.psl.application.util.Grounding;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabasePopulator;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.evaluation.result.FullInferenceResult;
import edu.umd.cs.psl.evaluation.result.memory.MemoryFullInferenceResult;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.UniqueID;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.PersistedAtomManager;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.parameters.PositiveWeight;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.reasoner.Reasoner;
import edu.umd.cs.psl.util.database.Queries;

/**
 * Latent Topic Networks, a framework which jointly reasons over a PSL model
 * with a topic model, as published in:
 * 
 * J.R. Foulds, S.H. Kumar, L. Getoor (2015). Latent Topic Networks:
 * A Versatile Probabilistic Programming Framework for Topic Models.
 * Proceedings of The 32nd International Conference on Machine Learning, pages 777-786.
 * 
 * @author Jimmy Foulds <jfoulds@ucsc.edu>
 */
public class LatentTopicNetwork implements ModelApplication {
	

	private static final Logger log = LoggerFactory.getLogger(LatentTopicNetwork.class);
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "latentTopicNetworks";
	
	/**
	 * Key for Boolean property indicating whether to use a hinge-loss MRF to model theta.
	 */
	public static final String HINGE_LOSS_THETA_KEY = CONFIG_PREFIX + ".hingeLossTheta";
	/** Default value for HINGE_LOSS_THETA_KEY */
	public static final boolean HINGE_LOSS_THETA_DEFAULT = true;
	
	/**
	 * Key for Boolean property indicating whether to use a hinge-loss MRF to model phi.
	 */
	public static final String HINGE_LOSS_PHI_KEY = CONFIG_PREFIX + ".hingeLossPhi";
	/** Default value for HINGE_LOSS_PHI_KEY */
	public static final boolean HINGE_LOSS_PHI_DEFAULT = false;
	
	/**
	 * Key for positive integer property indicating the number of EM iterations to perform.
	 */
	public static final String NUM_ITERATIONS_KEY = CONFIG_PREFIX + ".numIterations";
	/** Default value for NUM_ITERATIONS_KEY */
	public static final int NUM_ITERATIONS_DEFAULT = 200;
	
	/**
	 * Key for positive integer property indicating the number of vanilla LDA EM iterations to perform before using hinge losses in the M step.
	 */
	public static final String NUM_BURNIN_KEY = CONFIG_PREFIX + ".numBurnIn";
	/** Default value for NUM_BURNIN_KEY */
	public static final int NUM_BURNIN_DEFAULT = 0;
	
	
	/**
	 * Key for positive integer property indicating the number of EM iterations to perform.
	 */
	public static final String NUM_TOPICS_KEY = CONFIG_PREFIX + ".numTopics";
	/** Default value for NUM_TOPICS_KEY */
	public static final int NUM_TOPICS_DEFAULT = 20;
	
	/**
	 * Key for positive double property, the Dirichlet prior hyperparameter alpha.
	 */
	public static final String ALPHA_KEY = CONFIG_PREFIX + ".alpha";
	/** Default value for ALPHA_KEY */
	public static final double ALPHA_DEFAULT = 1.01;
	
	/**
	 * Key for positive double property, the Dirichlet prior hyperparameter beta.
	 */
	public static final String BETA_KEY = CONFIG_PREFIX + ".beta";
	/** Default value for BETA_KEY */
	public static final double BETA_DEFAULT = 1.01;
	
	/**
	 * Key for Boolean property indicating whether to perform pseudo-likelihood weight learning in the EM loop.
	 */
	public static final String WEIGHT_LEARNING_KEY = CONFIG_PREFIX + ".weightLearning";
	/** Default value for WEIGHT_LEARNING_KEY */
	public static final boolean WEIGHT_LEARNING_DEFAULT = false;
	
	/**
	 * Key for positive integer property indicating the number of EM iterations to perform before performing weight learning.
	 */
	public static final String FIRST_W_LEARNING_ITER_KEY = CONFIG_PREFIX + ".firstWLearningIter";
	/** Default value for FIRST_W_LEARNING_ITER_KEY */
	public static final int FIRST_W_LEARNING_ITER_DEFAULT = 50;
	
	/**
	 * Key for positive integer property indicating the number of EM iterations to between weight learning steps.
	 */
	public static final String W_LEARNING_GAP_KEY = CONFIG_PREFIX + ".WLearningGap";
	/** Default value for W_LEARNING_GAP_KEY */
	public static final int W_LEARNING_GAP_DEFAULT = 10;
	
	/**
	 * Key for Boolean property indicating whether to initialize the ADMM variables to LDA, for theta.
	 * The alternative is to initialize at the previous iteration.  LDA initialization may be best in high dimensions,
	 * while previous iteration initialization may be best with strong weights.
	 */
	public static final String INIT_MSTEP_TO_LDA_THETA_KEY = CONFIG_PREFIX + ".initMStepToLDAtheta";
	/** Default value for INIT_MSTEP_TO_LDA_THETA_KEY */
	public static final boolean INIT_MSTEP_TO_LDA_THETA_DEFAULT = false;
	
	/**
	 * Key for Boolean property indicating whether to initialize the ADMM variables to LDA, for phi.
	 * The alternative is to initialize at the previous iteration.  LDA initialization may be best in high dimensions,
	 * while previous iteration initialization may be best with strong weights.
	 */
	public static final String INIT_MSTEP_TO_LDA_PHI_KEY = CONFIG_PREFIX + ".initMStepToLDAphi";
	/** Default value for INIT_MSTEP_TO_LDA_PHI_KEY */
	public static final boolean INIT_MSTEP_TO_LDA_PHI_DEFAULT = true;
	
	/**
	 * Key for string property indicating the directory to save intermediate topic models (if empty, do not save them).
	 */
	public static final String SAVE_DIR_KEY = CONFIG_PREFIX + ".saveDir";
	/** Default value for SAVE_DIR_KEY */
	public static final String SAVE_DIR_DEFAULT = "";
	
	private Model modelTheta;
	private Model modelPhi;
	private Database dbTheta;
	private Database dbPhi;
	LatentTopicNetworkADMMReasoner reasonerTheta;
	LatentTopicNetworkADMMReasoner reasonerPhi;
	PersistedAtomManager atomManagerTheta;
	PersistedAtomManager atomManagerPhi;
	private ConfigBundle config;
	int numDocuments;
	int numTopics;
	int numWords;
	int numIterations;
	int burnIn;
	double[][] expectedCountsTheta; //summary statistics from E-step. [document][topic]
	double[][] expectedCountsPhi; //[topic][word]
	double[][] theta; //parameters
	double[][] phi;
	
	final boolean hingeLossTheta; //false means do only LDA inference, ignore hinge loss
	final boolean hingeLossPhi;
	
	final boolean initMStepToLDAtheta; //if true, initialize ADMM to LDA's solution, otherwise initialize to the values at the previous iteration.
	final boolean initMStepToLDAphi;
	
	//weight learning variables
	boolean doWeightLearning;
	StandardPredicate[] X;
	StandardPredicate[] Y;
	StandardPredicate[] Z;
	int firstWLearningIter = Integer.MAX_VALUE;
    int wLearningGap = Integer.MAX_VALUE;
    DataStore dataStore;
    Database rvDBweightLearningTheta;		
	Database observedDBweightLearningTheta;
	Database rvDBweightLearningPhi;		
	Database observedDBweightLearningPhi;
	Partition rvPartitionTheta;;
	Partition labelPartitionTheta;
	Partition rvPartitionPhi;
	Partition labelPartitionPhi;
	
	double alpha; //hyper-parameters
	double beta;
	
	//store the data
	int[][] docWords; //[document][dictionary index]
	int[][] docCounts;//[document][count of corresponding word in docWords]
	
	final String saveDir;
	
	public LatentTopicNetwork(Model modelTheta, Database dbTheta, Model modelPhi, Database dbPhi, int[][] docWords, int[][] docCounts, int numWords, String[] X, String[] Y, String[] Z, DataStore dataStore, ConfigBundle config) {
		this(modelTheta, dbTheta, modelPhi, dbPhi, docWords, docCounts, numWords, config);
		doWeightLearning = config.getBoolean(WEIGHT_LEARNING_KEY, WEIGHT_LEARNING_DEFAULT);
		if (doWeightLearning) {
			firstWLearningIter = config.getInt(FIRST_W_LEARNING_ITER_KEY, FIRST_W_LEARNING_ITER_DEFAULT);
			wLearningGap = config.getInt(W_LEARNING_GAP_KEY, W_LEARNING_GAP_DEFAULT);
			this.X = new StandardPredicate[X.length];
			for (int i = 0; i < X.length; i++)
				this.X[i] = (StandardPredicate)PredicateFactory.getFactory().getPredicate(X[i]);
			this.Y = new StandardPredicate[Y.length];
			for (int i = 0; i < Y.length; i++)
				this.Y[i] = (StandardPredicate)PredicateFactory.getFactory().getPredicate(Y[i]);
			this.Z = new StandardPredicate[Z.length];
			for (int i = 0; i < Z.length; i++)
				this.Z[i] = (StandardPredicate)PredicateFactory.getFactory().getPredicate(Z[i]);
			this.dataStore = dataStore;
			//Note, getNextPartition() finds the next empty Partition.
			//This can therefore potentially select an empty partition that one was planning on using.
			//I'm not sure how to prevent this. -JF
			rvPartitionTheta = dataStore.getNextPartition();
			labelPartitionTheta = new Partition(rvPartitionTheta.getID() + 1);
			rvPartitionPhi  = new Partition(rvPartitionTheta.getID() + 2);
			labelPartitionPhi  = new Partition(rvPartitionTheta.getID() + 3);
		}
	}
	
	/** A convenience constructor with fewer required fields which can be used when not performing weight learning. */
	public LatentTopicNetwork(Model modelTheta, Database dbTheta, Model modelPhi, Database dbPhi, int[][] docWords, int[][] docCounts, int numWords, ConfigBundle config) {
		numIterations = config.getInt(NUM_ITERATIONS_KEY, NUM_ITERATIONS_DEFAULT);
		if (numIterations <= 0)
			throw new IllegalArgumentException("Number of iterations must be positive.");
		burnIn = config.getInt(NUM_BURNIN_KEY, NUM_BURNIN_DEFAULT);
		if (burnIn < 0)
			throw new IllegalArgumentException("Number of burn-in iterations must be non-negative.");
		hingeLossTheta = config.getBoolean(HINGE_LOSS_THETA_KEY, HINGE_LOSS_THETA_DEFAULT);
		hingeLossPhi = config.getBoolean(HINGE_LOSS_PHI_KEY, HINGE_LOSS_PHI_DEFAULT);
		numTopics = config.getInt(NUM_TOPICS_KEY, NUM_TOPICS_DEFAULT);
		if (numTopics <= 0)
			throw new IllegalArgumentException("Number of topics iterations must be positive");
		alpha = config.getDouble(ALPHA_KEY, ALPHA_DEFAULT);
		if (alpha <= 0)
			throw new IllegalArgumentException("alpha must be positive.");
		beta = config.getDouble(BETA_KEY, BETA_DEFAULT);
		if (beta <= 0)
			throw new IllegalArgumentException("beta must be positive.");
		doWeightLearning = false; //Fields required for weight learning have not been provided.
		
		initMStepToLDAtheta = config.getBoolean(INIT_MSTEP_TO_LDA_THETA_KEY, INIT_MSTEP_TO_LDA_THETA_DEFAULT);
		initMStepToLDAphi = config.getBoolean(INIT_MSTEP_TO_LDA_PHI_KEY, INIT_MSTEP_TO_LDA_PHI_DEFAULT);
		
		saveDir = config.getString(SAVE_DIR_KEY, SAVE_DIR_DEFAULT);
		
		this.modelTheta = modelTheta;
		this.dbTheta = dbTheta;
		this.modelPhi = modelPhi;
		this.dbPhi = dbPhi;
		this.config = config;
		this.numWords = numWords;
		this.docWords = docWords;
		this.docCounts = docCounts;
		numDocuments = docWords.length;
		
		expectedCountsTheta = new double[numDocuments][numTopics];
		theta = new double[numDocuments][numTopics];
		expectedCountsPhi = new double[numTopics][numWords];
		phi = new double[numTopics][numWords];
	}
	
	
	public double[][] getTheta() {
		return theta;
	}
	public double[][] getPhi() {
		return phi;
	}
	
	public void trainModel() throws ClassNotFoundException, IllegalAccessException, InstantiationException, IOException {
		initialize();
		boolean firstMStep = true;
		for (int i = 0; i < numIterations; i++) {
			log.debug("Iteration " + i);
			eStep();
			try {
				if (i >= burnIn) {
					mStep(firstMStep);
					firstMStep = false;
				}
				else {
					log.debug("Burn in, LDA M-step");
					LdaMStep();
				}
			} catch (Exception e) {
				System.err.println("Unexpected error!");
				e.printStackTrace();
				System.exit(-1);
			}
			log.debug("Log-likelihood after Iteration " + i + ": " + logLikelihood());
			
			if (doWeightLearning && i >= firstWLearningIter && ((i - firstWLearningIter) % wLearningGap == 0)) {
				weightLearning();
			}
			
			if (saveDir.length() > 0) {
				ObjectOutputStream outStream = new ObjectOutputStream(new FileOutputStream(saveDir + "theta_iteration_" + i + ".ser"));
				outStream.writeObject(theta);
				outStream.flush();
				outStream.close();
				
				outStream = new ObjectOutputStream(new FileOutputStream(saveDir + "phi_iteration_" + i + ".ser"));
				outStream.writeObject(phi);
				outStream.flush();
				outStream.close();
			}
		}
	}
	
	protected void initialize() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		//reset counts
		double total;
		for (int j = 0; j < numDocuments; j++) {
			total = 0;
			for (int k = 0; k < numTopics; k++) {
				theta[j][k] = Math.random();
				total += theta[j][k];
			}
			for (int k = 0; k < numTopics; k++) {
				theta[j][k] /=	total;
			}
		}
		for (int k = 0; k < numTopics; k++)  {
			total = 0;
			for  (int w = 0; w < numWords; w++) {
				phi[k][w] = Math.random();
				total += phi[k][w];
			}
			for (int w = 0; w < numWords; w++) {
				phi[k][w] /= total;
			}
		}
		
		eStep();

		reasonerTheta = new LatentTopicNetworkADMMReasoner(config);
		reasonerPhi = new LatentTopicNetworkADMMReasoner(config);
		atomManagerTheta = new PersistedAtomManager(dbTheta);
		atomManagerPhi = new PersistedAtomManager(dbPhi);
		
		Predicate p = PredicateFactory.getFactory().getPredicate("Theta");
		if (hingeLossTheta)
			initializeForReasoner(reasonerTheta, atomManagerTheta, modelTheta, dbTheta, expectedCountsTheta, theta, p);
		p = PredicateFactory.getFactory().getPredicate("Phi");
		if (hingeLossPhi)
			initializeForReasoner(reasonerPhi, atomManagerPhi, modelPhi, dbPhi, expectedCountsPhi, phi, p);
	}
	
	protected void initializeForReasoner(Reasoner reasoner, PersistedAtomManager atomManager, Model model, Database db, double[][] expectedCounts, double[][] initialization, Predicate p) {
		log.info("Grounding out model.");
		Grounding.groundAll(model, atomManager, reasoner);
		
		//Add log loss terms
		log.info("Adding log loss ground kernels");
		UniqueID row; //doc, for theta, or topic, for phi
		UniqueID col; //topic, for theta, or word, for phi
		
		
		int numRows = expectedCounts.length; //num documents, or num topics
		int numColumns = expectedCounts[0].length; //num topics, or num words
		
		List<GroundAtom> literals;
		List<Double> coefficients;
		
		for (int j = 0; j < numRows; j++) {
			row = db.getUniqueID(new Integer(j));
			 literals = new ArrayList<GroundAtom>(numColumns);
			 coefficients = new ArrayList<Double>(numColumns);
			for (int k = 0; k < numColumns; k++) {
				col = db.getUniqueID(new Integer(k));
				GroundAtom ga = atomManager.getAtom(p, row, col);
				ga.getVariable().setValue(initialization[j][k]);
				literals.add(ga);
				coefficients.add(expectedCounts[j][k]);
			}
			LDAgroundLogLoss GLL = new LDAgroundLogLoss(null, literals, coefficients, expectedCounts[j]);
			GLL.setWeight(new PositiveWeight(1));
			reasoner.addGroundKernel(GLL);
		}
	}
	
	protected void eStep() {
		log.info("E-step");
		//reset counts
		for (int j = 0; j < numDocuments; j++) {
			for (int k = 0; k < numTopics; k++) {
				expectedCountsTheta[j][k] = alpha - 1; //we are going to have to add this later, might as well start with it instead of zero
			}
		}
		for (int w = 0; w < numWords; w++) {
			for (int k = 0; k < numTopics; k++) {
				expectedCountsPhi[k][w] = beta - 1;
			}
		}
		
		int w;
		double total;
		int count;
		double[] gamma = new double[numTopics];
		double gamma_k; //for locality of reference
		for (int j = 0; j < numDocuments; j++)
			for (int wInd = 0; wInd < docWords[j].length; wInd++)
			{
				w = docWords[j][wInd];
				count = docCounts[j][wInd];
				total = 0;
				
				//compute e-step responsibilities for current word
				for (int k = 0; k < numTopics; k++) {
					gamma[k] = theta[j][k] * phi[k][w];
					total = total + gamma[k];
				}
				
				//normalize and add it to the expected count matrices				
				for (int k = 0; k < numTopics; k++) {
					gamma_k = gamma[k] / total;
					if (Double.isNaN(gamma_k)) {
						log.debug("IsNan! " + j + " " + wInd + " " + k + " " + gamma_k + " " + total);
						System.exit(-1);
					}
					expectedCountsTheta[j][k] += count * gamma_k;
					expectedCountsPhi[k][w] += count * gamma_k;
				}
			}
	}
	
	protected void LdaMStep() {
		double total;
		for (int j = 0; j < numDocuments; j++) {
			total = 0;
			for (int k = 0; k < numTopics; k++) {
				total += expectedCountsTheta[j][k];
			}
			for (int k = 0; k < numTopics; k++) {
				theta[j][k] = expectedCountsTheta[j][k] / total;
			}
		}
		for (int k = 0; k < numTopics; k++) {
			total = 0;
			for (int j = 0; j < numWords; j++) {
				total += expectedCountsPhi[k][j];
			}
			for (int j = 0; j < numWords; j++) {
				phi[k][j] = expectedCountsPhi[k][j] / total;
			}
		}
	}
	
	protected void mStep(boolean firstTime) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		log.info("LDA M-step");
		LdaMStep();
		Predicate p;
		if (hingeLossTheta) {
			log.info("M-Step. Inference for theta");
			if (!firstTime && initMStepToLDAtheta) {
				reasonerTheta.initDirichletTerms();
			}
			p = PredicateFactory.getFactory().getPredicate("Theta");
			mpeInference(reasonerTheta, atomManagerTheta, modelTheta, dbTheta, expectedCountsTheta, theta, p);
			log.info("finished inference for theta");
		
			for (GroundAtom atom : Queries.getAllAtoms(dbTheta, p)) {	
				//println atom.toString() + "\t" + atom.getValue();
				GroundTerm[] terms  = atom.getArguments();
				theta[Integer.valueOf(terms[0].toString())][Integer.valueOf(terms[1].toString())] = atom.getValue();
			}
			
			//DEBUG
			if (log.isDebugEnabled()) {
				log.debug("theta totals: ");
				for (int j = 0; j < 10;j++) {
					double total = 0;
					for (int k = 0; k < numTopics; k++) {
						total += theta[j][k];
					}
					log.debug("" + total);
				}
				log.debug("");
			}
		}
		
		if (hingeLossPhi) {
			log.info("inference for phi");
			if (!firstTime && initMStepToLDAphi) {
				reasonerPhi.initDirichletTerms();
			}
			p = PredicateFactory.getFactory().getPredicate("Phi");
			mpeInference(reasonerPhi, atomManagerPhi, modelPhi, dbPhi, expectedCountsPhi, phi, p);
			log.info("finished for phi");
			
			for (GroundAtom atom : Queries.getAllAtoms(dbPhi, p)) {	
				GroundTerm[] terms  = atom.getArguments();
				phi[Integer.valueOf(terms[0].toString())][Integer.valueOf(terms[1].toString())] = atom.getValue();
			}
			
			if (log.isDebugEnabled()) {
				log.debug("phi totals ");
				for (int k = 0; k < 10; k++) {
					double total = 0;
					for (int j = 0; j < numWords; j++) {
						total += phi[k][j];
					}
					log.debug("" + total);
				}
				log.debug("");
			}
			
			//normalizing phi
			for (int k = 0; k < numTopics; k++)  {
				double total = 0;
				for  (int w = 0; w < numWords; w++) {
					total += phi[k][w];
				}
				for (int w = 0; w < numWords; w++) {
					phi[k][w] /= total;
				}
			}
						
			if (log.isDebugEnabled()) {
				log.debug("entropy after normalizing");
				for (int k = 0; k < 10; k++) {
					double entropy = 0;
					for (int j = 0; j < numWords; j++) {
						entropy -= phi[k][j] * Math.log(phi[k][j]);
					}
					log.debug(entropy + " ");
				}
				log.debug("");
			}
			
		}
				
	}
	
	protected void weightLearning() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		
		//TODO do I need separate sets for theta and phi variables?
		Set<StandardPredicate> setX = new HashSet<StandardPredicate>(Arrays.asList(this.X));
		Set<StandardPredicate> setZ = new HashSet<StandardPredicate>(Arrays.asList(this.Z));
		
		if (hingeLossTheta) {
			log.info("Populating databases for Theta");
			StandardPredicate pTheta = (StandardPredicate)PredicateFactory.getFactory().getPredicate("Theta");
			
			for (int i = 0; i < X.length; i++) {
				updateVariablesForWeightLearning(rvPartitionTheta, X[i], dbTheta);
			}
			for (int i = 0; i < Y.length; i++) {
				updateVariablesForWeightLearning(rvPartitionTheta, Y[i], dbTheta);
				updateVariablesForWeightLearning(labelPartitionTheta, Y[i], dbTheta);
			}
			for (int i = 0; i < Z.length; i++) {
				updateVariablesForWeightLearning(rvPartitionTheta, Z[i], dbTheta);
				updateVariablesForWeightLearning(labelPartitionTheta, Z[i], dbTheta); //the hidden variables are observed for the purposes of the weight learning M-step.
			}
			
			updateVariablesForWeightLearning(rvPartitionTheta, pTheta, dbTheta);
			updateVariablesForWeightLearning(labelPartitionTheta, pTheta, dbTheta); //the parameters are observed for the purposes of the weight learning M-step.
						
			Set<StandardPredicate> toCloseLabelPartitionTheta = new HashSet<StandardPredicate>(Arrays.asList(this.Y));
			toCloseLabelPartitionTheta.addAll(setZ);
			
			toCloseLabelPartitionTheta.add(pTheta);
			rvDBweightLearningTheta = dataStore.getDatabase(rvPartitionTheta, setX);		
			observedDBweightLearningTheta = dataStore.getDatabase(labelPartitionTheta, toCloseLabelPartitionTheta); //the hidden variables are observed for the purposes of the weight learning M-step.
			
			log.info("Running weight learning for Theta");
			LatentTopicNetworkMaxPseudoLikelihood MPL = new LatentTopicNetworkMaxPseudoLikelihood(modelTheta, rvDBweightLearningTheta, observedDBweightLearningTheta, config, alpha, pTheta);
			MPL.learn();
			
			//clean up
			rvDBweightLearningTheta.close();
			observedDBweightLearningTheta.close();
			dataStore.deletePartition(rvPartitionTheta);
			dataStore.deletePartition(labelPartitionTheta);
			
			log.info("Finished running weight learning for Theta");
			log.info(modelTheta.toString());
		}
		if (hingeLossPhi) {
			log.info("Populating databases for Phi");
			StandardPredicate pPhi = (StandardPredicate)PredicateFactory.getFactory().getPredicate("Phi");
			
			for (int i = 0; i < X.length; i++) {
				updateVariablesForWeightLearning(rvPartitionPhi, X[i], dbPhi);
			}
			for (int i = 0; i < Y.length; i++) {
				updateVariablesForWeightLearning(rvPartitionPhi, Y[i], dbPhi);
				updateVariablesForWeightLearning(labelPartitionPhi, Y[i], dbPhi);
			}
			for (int i = 0; i < Z.length; i++) {
				updateVariablesForWeightLearning(rvPartitionPhi, Z[i], dbPhi);
				updateVariablesForWeightLearning(labelPartitionPhi, Z[i], dbPhi); //the hidden variables are observed for the purposes of the weight learning M-step.
			}
			
			updateVariablesForWeightLearning(rvPartitionPhi, pPhi, dbPhi);
			updateVariablesForWeightLearning(labelPartitionPhi, pPhi, dbPhi); //the parameters are observed for the purposes of the weight learning M-step.
						
			Set<StandardPredicate> toCloseLabelPartitionPhi = new HashSet<StandardPredicate>(Arrays.asList(this.Y));
			toCloseLabelPartitionPhi.addAll(setZ);
			
			toCloseLabelPartitionPhi.add(pPhi);
			rvDBweightLearningPhi = dataStore.getDatabase(rvPartitionPhi, setX);		
			observedDBweightLearningPhi = dataStore.getDatabase(labelPartitionPhi, toCloseLabelPartitionPhi); //the hidden variables are observed for the purposes of the weight learning M-step.
			
			log.info("Running weight learning for Phi");
			LatentTopicNetworkMaxPseudoLikelihood MPL = new LatentTopicNetworkMaxPseudoLikelihood(modelPhi, rvDBweightLearningPhi, observedDBweightLearningPhi, config, beta, pPhi);
			MPL.learn();
			
			//clean up
			rvDBweightLearningPhi.close();
			observedDBweightLearningPhi.close();
			dataStore.deletePartition(rvPartitionPhi);
			dataStore.deletePartition(labelPartitionPhi);
			
			log.info("Finished running weight learning for Phi");
			log.info(modelPhi.toString());
			
		}
	}
	
	protected void updateVariablesForWeightLearning(Partition partition, StandardPredicate p, Database sourceDB) {
		Inserter u = dataStore.getInserter(p, partition);
		Set<GroundAtom> groundings = Queries.getAllAtoms(sourceDB, p);
		for (GroundAtom ga : groundings) {
			GroundTerm[] arguments = ga.getArguments();
			GroundAtom rv = sourceDB.getAtom(p, arguments);
			u.insertValue(rv.getValue(), (Object[]) arguments);
		}
	}
	
	/**
	 * Minimizes the total weighted incompatibility of the {@link GroundAtom GroundAtoms}
	 * in the Database according to the Model and commits the updated truth
	 * values back to the Database.
	 * <p>
	 * The {@link RandomVariableAtom RandomVariableAtoms} to be inferred are those
	 * persisted in the Database when this method is called. All RandomVariableAtoms
	 * which the Model might access must be persisted in the Database.
	 * 
	 * @return inference results
	 * @see DatabasePopulator
	 */
	protected FullInferenceResult mpeInference(Reasoner reasoner, PersistedAtomManager atomManager, Model model, Database db, double[][] expectedCounts, double[][] initialization, Predicate p)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
				
		log.info("Beginning inference.");
		reasoner.optimize();
		log.info("Inference complete. Writing results to Database.");
		
		/* Commits the RandomVariableAtoms back to the Database */
		int count = 0;
		for (RandomVariableAtom atom : atomManager.getPersistedRVAtoms()) {
			atom.commitToDB();
			count++;
		}
		
		double incompatibility = GroundKernels.getTotalWeightedIncompatibility(reasoner.getCompatibilityKernels());
		double infeasibility = GroundKernels.getInfeasibilityNorm(reasoner.getConstraintKernels());
		int size = reasoner.size();
		return new MemoryFullInferenceResult(incompatibility, infeasibility, count, size);
	}

	@Override
	public void close() {
		modelTheta=null;
		dbTheta = null;
		modelPhi=null;
		dbPhi = null;
		config = null;
		reasonerTheta.close();
		reasonerPhi.close();
	}
	
	/** Compute LDA training log-likelihood.
	 */
	public double logLikelihood() {
		double ll = 0;
		int word;
		int count;
		double prob;
		for (int j = 0; j < numDocuments; j++) {
			for (int w = 0; w < docWords[j].length; w++) {
				word = docWords[j][w];
				count = docCounts[j][w];
				prob = 0;
				for (int k = 0; k < numTopics; k++) {
					prob = prob + theta[j][k] * phi[k][word];
				}
				ll  += count * Math.log(prob);
			}
		}
		return ll;
	}

}
