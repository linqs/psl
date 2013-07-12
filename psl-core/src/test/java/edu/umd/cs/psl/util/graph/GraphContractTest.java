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
package edu.umd.cs.psl.util.graph;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Before;
import org.junit.Test;

/**
 * Contract tests for classes that implement {@link Graph}.
 */
abstract public class GraphContractTest {
	
	private Graph graph;
	
	private static enum testEnum {A, B};
	
	private static String P1 = "p1";
	private static Class<?> C1 = String.class;
	private static String P2 = "p2";
	private static Class<?> C2 = Boolean.class; 
	private static String P3 = "p3";
	private static Class<?> C3 = testEnum.class;
	private static String R1 = "r1";
	private static String R2 = "r2";
	
	@Before
	public final void setUp() throws Exception {
		graph = getGraphImplementation();
	}
	
	abstract protected Graph getGraphImplementation();
	
	private void createPropertyTypes() {
		graph.createPropertyType(P1, C1);
		graph.createPropertyType(P2, C2);
		graph.createPropertyType(P3, C3);
	}
	
	private void createRelationshipTypes() {
		graph.createRelationshipType(R1);
		graph.createRelationshipType(R2);
	}
	
	/** Tests creating a node. */
	@Test
	public void testCreateNode() {
		Node node = graph.createNode();
		
		assertTrue(node.getNoEdges() == 0);
		assertTrue(node.getNoProperties() == 0);
		assertTrue(node.getNoRelationships() == 0);
		
		assertFalse(node.getEdgeIterator().hasNext());
		assertFalse(node.getPropertyIterator().hasNext());
		assertFalse(node.getRelationshipIterator().hasNext());
		
		assertFalse(node.getEdges().iterator().hasNext());
		assertFalse(node.getProperties().iterator().hasNext());
		assertFalse(node.getRelationships().iterator().hasNext());
	}
	
	/** Tests deleting a node. */ 
	@Test
	public void testDeleteNode() {
		Node node1, node2;
		node1 = graph.createNode();
		node2 = graph.createNode();
		
		createPropertyTypes();
		createRelationshipTypes();
		
		node1.createRelationship(R1, node2);
		node1.createProperty(P2, true);
		node1.createProperty(P3, testEnum.B);
		
		node1.delete();
		
		assertTrue(graph.getNodeSnapshotByAttribute(P2, true).size() == 0);
		assertTrue(graph.getNodeSnapshotByAttribute(P2, false).size() == 0);
		assertTrue(graph.getNodeSnapshotByAttribute(P3, testEnum.A).size() == 0);
		assertTrue(graph.getNodeSnapshotByAttribute(P3, testEnum.B).size() == 0);
		
		assertTrue(node2.getNoEdges() == 0);
		assertTrue(node2.getNoProperties() == 0);
		assertTrue(node2.getNoRelationships() == 0);
		
		assertFalse(node2.getEdgeIterator().hasNext());
		assertFalse(node2.getPropertyIterator().hasNext());
		assertFalse(node2.getRelationshipIterator().hasNext());
		
		assertFalse(node2.getEdges().iterator().hasNext());
		assertFalse(node2.getProperties().iterator().hasNext());
		assertFalse(node2.getRelationships().iterator().hasNext());
	}
	
	/** Tests creating a property. */
	@Test
	public void testCreateProperty() {
		Node node;
		node = graph.createNode();
		String p = "test property value";
		
		createPropertyTypes();
		node.createProperty(P1, p);
		
		assertTrue(node.getNoEdges() == 1);
		assertTrue(node.getNoProperties() == 1);
		assertTrue(node.getNoRelationships() == 0);
		
		assertTrue(node.getPropertyIterator().next().getAttribute().equals(p));
		assertTrue(node.getProperties().iterator().next().getAttribute().equals(p));
		assertTrue(node.getPropertyIterator(P1).next().getAttribute().equals(p));
		assertTrue(node.getProperties(P1).iterator().next().getAttribute().equals(p));
		
		/* Tests that no property is return for type P2 */
		assertFalse(node.getPropertyIterator(P2).hasNext());
		assertFalse(node.getProperties(P2).iterator().hasNext());
	}
	
	/** Tests deleting a property. */
	@Test
	public void testDeleteProperty() {
		Node node;
		node = graph.createNode();
		String p = "test property value";
		
		createPropertyTypes();
		node.createProperty(P1, p).delete();
		
		assertTrue(node.getNoEdges() == 0);
		assertTrue(node.getNoProperties() == 0);
		assertTrue(node.getNoRelationships() == 0);
		
		assertFalse(node.getEdgeIterator().hasNext());
		assertFalse(node.getEdges().iterator().hasNext());
		assertFalse(node.getPropertyIterator(P1).hasNext());
		assertFalse(node.getProperties(P1).iterator().hasNext());
		assertFalse(node.getPropertyIterator(P2).hasNext());
		assertFalse(node.getProperties(P2).iterator().hasNext());
	}
	
	/** Tests deleting a property while leaving another untouched. */
	@Test
	public void testDeletePropertySelectively() {
		Node node;
		node = graph.createNode();
		String p = "test property value";
		
		createPropertyTypes();
		node.createProperty(P1, p);
		node.createProperty(P1, p).delete();
		
		assertTrue(node.getNoEdges() == 1);
		assertTrue(node.getNoProperties() == 1);
		assertTrue(node.getNoRelationships() == 0);
		
		assertTrue(node.getPropertyIterator().next().getAttribute().equals(p));
		assertTrue(node.getProperties().iterator().next().getAttribute().equals(p));
		assertTrue(node.getPropertyIterator(P1).next().getAttribute().equals(p));
		assertTrue(node.getProperties(P1).iterator().next().getAttribute().equals(p));
		
		/* Tests that no property is return for type P2 */
		assertFalse(node.getPropertyIterator(P2).hasNext());
		assertFalse(node.getProperties(P2).iterator().hasNext());
	}
	
	/** Tests that a boolean property is indexed correctly after creation. */
	@Test
	public void testCreateBooleanProperty() {
		Node node;
		node = graph.createNode();
		
		createPropertyTypes();
		node.createProperty(P2, true);
		
		assertTrue(graph.getNodeSnapshotByAttribute(P2, true).size() == 1);
		assertTrue(graph.getNodeSnapshotByAttribute(P2, true).iterator().next().equals(node));
		
		assertTrue(graph.getNodeSnapshotByAttribute(P2, false).size() == 0);
	}
	
	/** Tests that a boolean property is indexed correctly after deletion. */
	@Test
	public void testDeleteBooleanProperty() {
		Node node;
		node = graph.createNode();
		
		createPropertyTypes();
		node.createProperty(P2, true).delete();
		
		assertTrue(graph.getNodeSnapshotByAttribute(P2, true).size() == 0);
		assertTrue(graph.getNodeSnapshotByAttribute(P2, false).size() == 0);
	}
	
	/**
	 * Tests that boolean properties are indexed correctly after deleting
	 * one and leaving others untouched.
	 */
	@Test
	public void testDeleteBooleanPropertySelectively() {
		Node node = graph.createNode();
		
		createPropertyTypes();
		node.createProperty(P2, true);
		node.createProperty(P2, true).delete();
		node.createProperty(P2, false).delete();
		
		assertTrue(graph.getNodeSnapshotByAttribute(P2, true).size() == 1);
		assertTrue(graph.getNodeSnapshotByAttribute(P2, false).size() == 0);
	}
	
	/**
	 * Tests getting an attribute from a node with multiple properties of the same type.
	 * 
	 * {@link Node#getAttribute(String)} should throw {@link IllegalArgumentException}.  
	 */
	@Test(expected=IllegalArgumentException.class)
	public void testGetAttribute() {
		Node node = graph.createNode();
		
		createPropertyTypes();
		node.createProperty(P2, true);
		node.createProperty(P2, true);
		
		node.getAttribute(P2);
	}
	
	/** Tests that an enum property is indexed correctly after creation. */
	@Test
	public void testCreateEnumProperty() {
		Node node;
		node = graph.createNode();
		
		createPropertyTypes();
		node.createProperty(P3, testEnum.A);
		
		assertTrue(graph.getNodeSnapshotByAttribute(P3, testEnum.A).size() == 1);
		assertTrue(graph.getNodeSnapshotByAttribute(P3, testEnum.A).iterator().next().equals(node));
		
		assertTrue(graph.getNodeSnapshotByAttribute(P3, testEnum.B).size() == 0);
	}
	
	/** Tests that an enum property is indexed correctly after deletion. */
	@Test
	public void testDeleteEnumProperty() {
		Node node;
		node = graph.createNode();
		
		createPropertyTypes();
		node.createProperty(P3, testEnum.A).delete();
		
		assertTrue(graph.getNodeSnapshotByAttribute(P3, testEnum.A).size() == 0);
		assertTrue(graph.getNodeSnapshotByAttribute(P3, testEnum.B).size() == 0);
	}
	
	/** Tests creating a relationship. */
	@Test
	public void testCreateRelationship() {
		Node node1, node2;
		node1 = graph.createNode();
		node2 = graph.createNode();
		
		createRelationshipTypes();
		node1.createRelationship(R1, node2);
		
		assertTrue(node1.getNoEdges() == 1);
		assertTrue(node1.getNoProperties() == 0);
		assertTrue(node1.getNoRelationships() == 1);
		
		assertTrue(node2.getNoEdges() == 1);
		assertTrue(node2.getNoProperties() == 0);
		assertTrue(node2.getNoRelationships() == 1);
		
		assertTrue(node1.getEdgeIterator().next().getStart().equals(node1));
		assertTrue(node1.getEdges().iterator().next().getStart().equals(node1));
		
		assertTrue(node2.getEdgeIterator().next().getStart().equals(node1));
		assertTrue(node2.getEdges().iterator().next().getStart().equals(node1));
		
		assertTrue(node1.getRelationshipIterator().next().getStart().equals(node1));
		assertTrue(node1.getRelationships().iterator().next().getStart().equals(node1));
		assertTrue(node1.getRelationshipIterator().next().getEnd().equals(node2));
		assertTrue(node1.getRelationships().iterator().next().getEnd().equals(node2));
		
		assertTrue(node2.getRelationshipIterator().next().getStart().equals(node1));
		assertTrue(node2.getRelationships().iterator().next().getStart().equals(node1));
		assertTrue(node2.getRelationshipIterator().next().getEnd().equals(node2));
		assertTrue(node2.getRelationships().iterator().next().getEnd().equals(node2));
	}
	
	/** Tests creating a duplicate relationship. */
	@Test
	public void testCreateDuplicateRelationship() {
		Node node1, node2;
		node1 = graph.createNode();
		node2 = graph.createNode();
		
		createRelationshipTypes();
		node1.createRelationship(R1, node2);
		node1.createRelationship(R1, node2);
		
		assertTrue(node1.getNoEdges() == 2);
		assertTrue(node1.getNoProperties() == 0);
		assertTrue(node1.getNoRelationships() == 2);
		
		assertTrue(node2.getNoEdges() == 2);
		assertTrue(node2.getNoProperties() == 0);
		assertTrue(node2.getNoRelationships() == 2);
	}
	
	/** Tests deleting a relationship. */
	@Test
	public void testDeleteRelationship() {
		Node node1, node2;
		node1 = graph.createNode();
		node2 = graph.createNode();
		
		createRelationshipTypes();
		node1.createRelationship(R1, node2).delete();
		
		assertTrue(node1.getNoEdges() == 0);
		assertTrue(node1.getNoProperties() == 0);
		assertTrue(node1.getNoRelationships() == 0);
		
		assertTrue(node2.getNoEdges() == 0);
		assertTrue(node2.getNoProperties() == 0);
		assertTrue(node2.getNoRelationships() == 0);
		
		assertFalse(node1.getEdgeIterator().hasNext());
		assertFalse(node1.getPropertyIterator().hasNext());
		assertFalse(node1.getRelationshipIterator().hasNext());
		
		assertFalse(node2.getEdgeIterator().hasNext());
		assertFalse(node2.getPropertyIterator().hasNext());
		assertFalse(node2.getRelationshipIterator().hasNext());
		
		assertFalse(node1.getEdges().iterator().hasNext());
		assertFalse(node1.getProperties().iterator().hasNext());
		assertFalse(node1.getRelationships().iterator().hasNext());
		
		assertFalse(node2.getEdges().iterator().hasNext());
		assertFalse(node2.getProperties().iterator().hasNext());
		assertFalse(node2.getRelationships().iterator().hasNext());
	}
	
	/** Tests deleting a duplicate relationship. */
	@Test
	public void testDeleteDuplicateRelationship() {
		Node node1, node2;
		node1 = graph.createNode();
		node2 = graph.createNode();
		
		createRelationshipTypes();
		node1.createRelationship(R1, node2);
		node1.createRelationship(R1, node2).delete();
		
		assertTrue(node1.getNoEdges() == 1);
		assertTrue(node1.getNoProperties() == 0);
		assertTrue(node1.getNoRelationships() == 1);
		
		assertTrue(node2.getNoEdges() == 1);
		assertTrue(node2.getNoProperties() == 0);
		assertTrue(node2.getNoRelationships() == 1);
	}
}
