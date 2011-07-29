/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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
package edu.umd.cs.psl.application.inference;

import edu.umd.cs.psl.application.FullInference;
import edu.umd.cs.psl.application.ModelApplication;

public abstract class PartitionedModelApplication implements ModelApplication, FullInference {
	
//	private static final Logger log = LoggerFactory.getLogger(MaintainedMemoryFullInference.class);
//
//	private final int[] clusterSizeExtremums;
//	private final double numActivatedTerminationFactor;
//	private final double numActivatedPerturbationFactor;
//	private final int maxConvergenceRounds;
//	
//	private static final double numActivatedDiscountFactor = 0.2;
//	private static final double atomChangeFactorMultiplier = 2;
//	private static final double maxConvergenceRoundsFactor = 0.05;
//	private static final double numActivatedPerturbationFactorMultiplier = 4;
//	
////	private static final double edgeWeightPerturbationBaseline = 0.1;
////	private static final double perceptronUpdateFactor = 0.3;
//
//	
//	private final DatabaseQuery dbProxy;
//	private final AtomEventFramework activationFrame;
//	private final AtomStore store;
//	private final PSLCoreConfiguration config;
//	private final Model model;
//	private final double atomChangeDelta;
//	
//	
//	public PartitionedModelApplication(Model m, Database db, PSLCoreConfiguration configuration) {
//		config = configuration;
//		model = m;
//		store = new AtomStore(db);
//		dbProxy = new DatabaseQuery(db,store,m.getPredicateFactory());
//		activationFrame = new AtomEventFramework(m,this,store,config);
//		model.registerModelObserver(this);
//		atomChangeDelta=config.getActivationThreshold();
//		maxConvergenceRounds = (int)Math.round(config.getMaxNoInferenceSteps()*maxConvergenceRoundsFactor);
//		clusterSizeExtremums=config.getNumericProgramSizeBounds();
//		numActivatedTerminationFactor=config.getActivationTerminationFactor();
//		numActivatedPerturbationFactor = numActivatedTerminationFactor*numActivatedPerturbationFactorMultiplier;
//	}
//	
//	public PartitionedModelApplication(Model m, Database db) {
//		this(m,db,new PSLCoreConfiguration());
//	}
//	
//	//####### Evidence Handling #####
//	
//	@Override
//	public void addGroundKernel(GroundKernel e) {
//		if (!evidences.put(e.getKernel(),e)) throw new IllegalArgumentException("Evidence has already been added: "+e);
//		//Register with atoms
//		for (Atom atom : e.getAtoms()) if (!atom.registerGroundKernel(e)) throw new AssertionError("Evidence has already been registered with atom!");
//	}
//	
//	@Override
//	public void changedGroundKernel(GroundKernel e) {
//		//Register with atoms. Note that some atoms might already have the evidence registered, since it is only updated
//		if (!evidences.contains(e.getKernel(), e)) throw new IllegalArgumentException("Evidence has never been added: "+e);
//		for (Atom atom : e.getAtoms()) atom.registerGroundKernel(e);
//	}
//	
//	@Override
//	public void removeGroundKernel(GroundKernel e) {
//		//Deregister with atoms and remove from reasoner
//		for (Atom atom : e.getAtoms()) if (!atom.deregisterGroundKernel(e)) throw new AssertionError("Evidence has never been registered with atom!");
//		evidences.remove(e.getKernel(), e);
//	}
//	
//	@Override
//	public boolean containsGroundKernel(GroundKernel e) {
//		return evidences.contains(e.getKernel(),e);
//	}
//	
//	@Override
//	public GroundKernel getGroundKernel(GroundKernel e) {
//		return evidences.get(e.getKernel(),e);
//	}
//	
//	@Override
//	public Iterable<GroundCompatibilityKernel> getCompatibilityKernels() {
//		return Iterables.filter(evidences.filterIterable(Filters.ProbabilisticEvidence), GroundCompatibilityKernel.class);
//	}
//	
//	@Override
//	public void changedEvidenceTypeParameters(Kernel me) {
//		for (GroundKernel e : evidences.keyIterable(me)) {
//			e.updateEvidenceParameters();
//		}
//	}
//	
//	//####### Inference #####
//	
//	@Override
//	public void initialGrounding() {
//		model.groundAll(this);
//		activationFrame.workOffJobQueue();
//	}
//	
//	@Override
//	public FullInferenceResult totalMap() {
//		initialGrounding();
//		return mapInference();
//	}
//	
//	class PartitionInference implements Runnable {
//
//		private final Set<Atom> part;
//		private final BlockingQueue<Map<Atom,Double>> results;
//		
//		public PartitionInference(Set<Atom> part, BlockingQueue<Map<Atom,Double>> results) {
//			this.part = part;
//			this.results = results;
//		}
//		
//		@Override
//		public void run() {
//			SetFunctionVariableMask varmask = new SetFunctionVariableMask(part);
//			Reasoner reasoner = NumericToolboxReasoner.getDefaultReasoner(config, varmask);
//			//Add evidence
//			for (Atom atom : part) {
//				for (GroundKernel e : atom.getAllRegisteredGroundKernels()) {
//					if (!reasoner.containsGroundKernel(e)) reasoner.addGroundKernel(e);
//					assert constraintConsistency(e) : e.toString();
////					if (atom.getArguments()[0].toString().equals("8255")) {
////						log.debug("Incident on {}: {}",atom,e);
////						for (Atom a : e.getAtoms()) {
////							log.debug("Neighbor {} is fixed {} with truth value "+a.getTruthValue(),a,varmask.isFixed(a));
////						}
////					}
//
//				}
//			}
//			results.add(reasoner.inferenceStep());
//			reasoner.close();
//			//if (varmask.getNumQueriesForFixed()>0) 
//				log.debug("Number of queries for fixed atoms: {}",varmask.getNumQueriesForFixed());
//		}
//		
//		private boolean constraintConsistency(GroundKernel evidence) {
//			if (evidence instanceof GroundConstraintKernel) {
//				for (Atom a : evidence.getAtoms()) {
//					if (a.isInferenceAtom() && !part.contains(a)) return false;
//				}				
//			} 
//			return true;
//		}
//		
//	}
//	
//	
//	@Override
//	public FullInferenceResult mapInference() {
//		ExecutorService pool = Executors.newFixedThreadPool(config.getNoThreads());
//		
//		Collection<Set<Atom>> clusters = null;
//		double numActivatedRunningAvg=0.0;
//		boolean terminate = false;
//		double edgeWeightPerturbationFactor = 0.0;
//		double updateFactor = 1.0;
//		int convergenceRounds=0;
//		int rounds=0;
//		do {
//			int numEvidences = evidences.size();
//			log.debug("Clustering {} pieces of evidence with {} perturbation",numEvidences,edgeWeightPerturbationFactor);
//			DogmaFactorGraph fg=FactorGraphFactory.constructFactorGraph(evidences,edgeWeightPerturbationFactor);
//			clusters = fg.clusterAtoms(clusterSizeExtremums[0],clusterSizeExtremums[1]);
//			log.debug("Computed {} clusters, updating with updateFactor {}",clusters.size(),updateFactor);
//			
//			//double avgEdgeWeight = fg.getAverageEdgeWeight();
//			fg=null;
//			int numAtoms = 0; for (Set<Atom> cluster : clusters) numAtoms+=cluster.size();
//
////			do {
//				int numChanged = 0;
//				double totalAtomChange = 0.0;
//				int numActivated = 0;
//				rounds++;
//				
//				BlockingQueue<Map<Atom,Double>> results = new LinkedBlockingQueue<Map<Atom,Double>>();
//				for (Set<Atom> cluster : clusters) {
//					pool.submit(new PartitionInference(cluster,results));
//				}
//				int numClusters2Process = clusters.size();
//		
//				while (numClusters2Process>0) {
//					Map<Atom, Double> result;
//					try {
//						result = results.take();
//					} catch (InterruptedException e) {
//						throw new AssertionError(e);
//					}
//					numClusters2Process--;
//					for (Map.Entry<Atom, Double> entry : result.entrySet()) {
//						Atom atom = entry.getKey();
//						double truthval = entry.getValue();
//						double oldtruthval = atom.getSoftValue();
//						if (truthval<config.getActivationThreshold()) truthval = 0.0;
//
//						double delta = Math.abs(truthval-oldtruthval);
//						if (delta>atomChangeDelta) numChanged++;
////						if (atom.getArguments()[0].toString().equals("8255")) log.debug("{} changed from {} to "+truthval,atom,oldtruthval);
//						totalAtomChange+=delta;
//						
//						if (activationFrame.activateAtom(atom,truthval)) numActivated++;
//						atom.setTruthValue(oldtruthval + updateFactor*(truthval-oldtruthval));
//					}
//				}
//				assert results.isEmpty();
//				
//				numActivatedRunningAvg = numActivatedRunningAvg*numActivatedDiscountFactor + numActivated;
//				double atomChangeThreshold =numAtoms*numActivatedTerminationFactor*atomChangeFactorMultiplier; 
//				double runningActivationThreshold = numAtoms*numActivatedTerminationFactor;
//				
//				log.debug("Atom change {} vs threshold {} where total number changed is "+numChanged,totalAtomChange,atomChangeThreshold); 
//				log.debug("Activated running avg {} vs threshold {}",numActivatedRunningAvg,runningActivationThreshold); 
//
//				terminate = (numActivatedRunningAvg<=runningActivationThreshold || numActivated==0)
//										&& (totalAtomChange<=atomChangeThreshold || convergenceRounds>=maxConvergenceRounds);
//				
//				//Should we randomly perturbate the edge weights to facilitate convergence? Only do so toward the end!
//				if (numActivatedRunningAvg<=numAtoms*numActivatedPerturbationFactor) {
////					edgeWeightPerturbationFactor=Math.min(runningActivationThreshold/numActivatedRunningAvg, 1.0)
////													*avgEdgeWeight*edgeWeightPerturbationBaseline;
//				} else {
//					edgeWeightPerturbationFactor=0.0;
//				}
//				
//				//Modify behavior when we get to the end
//				if (numActivatedRunningAvg<=runningActivationThreshold) {
//					//updateFactor = perceptronUpdateFactor;
//					updateFactor = 1.0;
//					convergenceRounds++;
//				}
//				
//				if (!terminate) {
//					log.debug("Activate {} atoms",numActivated);
//					if (numActivated>0) activationFrame.workOffJobQueue();
//				}
////			} while (numActivated<=0 && totalChange>atomChangeThreshold && rounds<config.getMaxNoInferenceSteps());
//		
//		} while (!terminate && rounds<config.getMaxNoInferenceSteps());
//		
//		//Shutdown thread pool to release resources
//		pool.shutdownNow();
//		try {
//			if (!pool.awaitTermination(2, TimeUnit.SECONDS)) log.warn("Pool did not shut down!");
//		} catch (InterruptedException e) {
//			log.warn("Got interrupted waiting for thread pool to shut down! {}",e.getMessage());
//		}
//		
//		//Update truth values
//		int numAtoms = 0;
//		for (Set<Atom> cluster : clusters) { for (Atom atom : cluster) {
//			numAtoms++;
//			if (atom.getSoftValue()>0.0) {
//				if (!atom.isActive() && !atom.isFactAtom()) {
//					log.debug("Found atom which was not activated: {}",atom.toString());
//					//throw new AssertionError("Encountered a non-active atom with non-zero truth value! " + atom);
//				}
//				store.persistTruthValue(atom);
//			}
//		}}
//		
//		return new FullInferenceResult(getLogProbability(),numAtoms,evidences.size());
//	}
//	
//	@Override
//	public FullConfidenceAnalysisResult marginalInference() {
//		throw new UnsupportedOperationException("Not yet supported");
//	}
//	
//	//####### Interface to other components #####
//	
//	@Override
//	public DatabaseQuery getDatabase() {
//		return dbProxy;
//	}
//	
//	@Override
//	public AtomEventFramework getAtomManager() {
//		return activationFrame;
//	}	
//		
//	@Override
//	public void fixedAtom(Atom atom) {
//		
//	}
//	
//	@Override
//	public void close() {
//		
//	}

	
}
