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
package edu.umd.cs.psl.factorgraph;

import java.util.Collection;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Iterables;

//import edu.umd.cs.psl.factorgraph.util.FactorGraphHelper;
//import edu.umd.cs.psl.model.atom.Atom;
//import edu.umd.umiacs.dogma.cloud.common.IntEdgeType;
//import edu.umd.umiacs.dogma.diskgraph.core.Directionality;
//import edu.umd.umiacs.dogma.diskgraph.core.GraphDatabase;
//import edu.umd.umiacs.dogma.diskgraph.core.GraphTransaction;
//import edu.umd.umiacs.dogma.diskgraph.core.Relationship;
//import edu.umd.umiacs.dogma.diskgraph.core.RelationshipType;
//import edu.umd.umiacs.dogma.diskgraph.core.Node;
//import edu.umd.umiacs.dogma.diskgraph.decorators.RelationshipWeighter;
//import edu.umd.umiacs.dogma.diskgraph.diskstorage.DataConverter;
//import edu.umd.umiacs.dogma.diskgraph.diskstorage.DataInput;
//import edu.umd.umiacs.dogma.diskgraph.diskstorage.DataOutput;
//import edu.umd.umiacs.dogma.diskgraph.graphdb.InMemoryGraphDB;
//import edu.umd.umiacs.dogma.diskgraph.graphdb.NodeFactory;
//import edu.umd.umiacs.dogma.diskgraph.operations.GraphClustering;


public abstract class DogmaFactorGraph implements FactorGraph {

//	private static final Logger log = LoggerFactory.getLogger(DogmaFactorGraph.class);
//	
//	private final GraphDatabase graphdb;
//	private final GraphTransaction tx;
//	private final BiMap<Vertex,Node> representation;
//	
//	
//	private int totalNumEdges;
//	private double totalEdgeWeight;
//	private int singletonFactors;
//	
//	@SuppressWarnings("serial")
//	public DogmaFactorGraph() {
//		graphdb = new InMemoryGraphDB("inMemory",new IntEdgeType(0),true);
//		tx = graphdb.startTransaction(NodeFactory.Undirected);
//		totalNumEdges=0;
//		totalEdgeWeight=0.0;
//		singletonFactors=0;
//		representation = HashBiMap.create();
//		
//		graphdb.registerRelationType(FactorIncidence.Default, new DataConverter<Double>() {
//			@Override
//			public Double readData(DataInput in) { return null;	}
//			@Override
//			public void writeData(Double obj, DataOutput out) {	}
//		});
//	}
//	
//	private Node getNode(Vertex v) {
//		Node n = representation.get(v);
//		if (n==null) {
//			n = tx.createNode();
//			representation.put(v, n);
//		}
//		return n;
//	}
//	
//	@Override
//	public void add(Factor factor) {
//		add(factor,0.0);
//	}
//	
//	@Override
//	public void add(Factor factor, double strengthPerturbation) {
//		//log.debug(factor.toString());
//		Set<? extends RandomVariable> rvs = factor.getRandomVariables();
//		assert rvs.size()>0;
//		if (rvs.size()<2) {
//			//Its a singleton which we can ignore to speed up clustering
//			singletonFactors++;
//			getNode(Iterables.getOnlyElement(rvs));
//		} else {
//			Node factornode = getNode(factor);
//			Double strength = new Double(factor.getStrength());
//			for (RandomVariable rv : factor.getRandomVariables()) {
//				Node rvnode = getNode(rv);
//				if (strengthPerturbation<=0.0) {
//					tx.createRelationship(factornode, rvnode, FactorIncidence.Default, strength);
//					totalEdgeWeight+= strength.doubleValue();
//				} else {
//					Double perturbedstrength = Double.valueOf(strength+getPerturbation(strengthPerturbation));
//					if (perturbedstrength<=0.0) continue; //Do not add edges with negative weight
//					tx.createRelationship(factornode, rvnode, FactorIncidence.Default, perturbedstrength);
//					totalEdgeWeight+= perturbedstrength.doubleValue();
//				}
//				totalNumEdges++;
//			}
//		}
//	}
//	
//	private static final double getPerturbation(double strengthPerturbation) {
//		return (Math.random()*2*strengthPerturbation)-strengthPerturbation;
//	}
//	
//	@Override
//	public Collection<Set<Atom>> clusterAtoms(int minVertices, int maxVertices) {
//		log.debug("Starting to cluster on {} nodes and {} edges with edge weight "+totalEdgeWeight,representation.size(),totalNumEdges);
//		GraphClustering clusterer = new GraphClustering(totalEdgeWeight);
//		double singletonAdjustFactor = 1.0/(1.0 + singletonFactors*1.0/representation.size());
//		clusterer.setExtremumClusterVertexWeight(minVertices*singletonAdjustFactor, maxVertices*singletonAdjustFactor);
//		clusterer.setRelationshipWeighter(FactorIncidence.Weighter);
//		Collection<Set<Node>> clusters = clusterer.cluster(representation.values());
//		return FactorGraphHelper.filterClusters(clusters, Atom.class, representation.inverse());
//	}
//	
//	public double getAverageEdgeWeight() {
//		return totalEdgeWeight/totalNumEdges;
//	}
	
}

//class FactorIncidence implements RelationshipType {
//
//	public static final FactorIncidence Default = new FactorIncidence();
//	public static final RelationshipWeighter Weighter = new RelationshipWeighter() {
//
//		@Override
//		public double getWeight(Relationship v) {
//			assert v.getRelationshipType() instanceof FactorIncidence;
//			return v.getLabel(Double.class);
//		}
//		
//	};
//	
//	private static final long serialVersionUID = 1L;
//
//	@Override
//	public Directionality getDirectionality() {
//		return Directionality.Undirected;
//	}
//
//	@Override
//	public boolean isLabeled() {
//		return true;
//	}
//
//	@Override
//	public String getName() {
//		return "Relationship between Factors and RVs";
//	}
//	
//}