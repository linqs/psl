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
package edu.umd.cs.psl.optimizer.conic.partition;

import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import edu.umd.cs.psl.optimizer.conic.program.Cone;
import edu.umd.cs.psl.optimizer.conic.program.ConicProgram;
import edu.umd.cs.psl.optimizer.conic.program.LinearConstraint;
import edu.umd.cs.psl.optimizer.conic.program.NonNegativeOrthantCone;

/**
 * Tests {@link ConicProgramPartition}. 
 */
public class ConicProgramPartitionTest {
	
	private ConicProgramPartition partition;
	private ConicProgram program;
	
	private LinearConstraint l1;
	private LinearConstraint l2;
	private LinearConstraint l3;
	
	@Before
	public final void setUp() throws Exception {
		program = new ConicProgram();
		
		NonNegativeOrthantCone c1 = program.createNonNegativeOrthantCone();
		NonNegativeOrthantCone c2 = program.createNonNegativeOrthantCone();
		NonNegativeOrthantCone c3 = program.createNonNegativeOrthantCone();
		NonNegativeOrthantCone c4 = program.createNonNegativeOrthantCone();
		NonNegativeOrthantCone c5 = program.createNonNegativeOrthantCone();
		NonNegativeOrthantCone c6 = program.createNonNegativeOrthantCone();
		NonNegativeOrthantCone c7 = program.createNonNegativeOrthantCone();
		
		l1 = program.createConstraint();
		l1.setVariable(c1.getVariable(), 1.0);
		l1.setVariable(c2.getVariable(), 1.0);
		l1.setVariable(c3.getVariable(), 1.0);
		l1.setConstrainedValue(1.0);
		
		l2 = program.createConstraint();
		l2.setVariable(c3.getVariable(), 1.0);
		l2.setVariable(c4.getVariable(), 1.0);
		l2.setVariable(c5.getVariable(), 1.0);
		l2.setConstrainedValue(1.0);
		
		l3 = program.createConstraint();
		l3.setVariable(c5.getVariable(), 1.0);
		l3.setVariable(c6.getVariable(), 1.0);
		l3.setVariable(c7.getVariable(), 1.0);
		
		Collection<Set<Cone>> coneSets = new LinkedList<Set<Cone>>();
		
		HashSet<Cone> coneSet = new HashSet<Cone>();
		coneSet.add(c1);
		coneSet.add(c2);
		coneSet.add(c3);
		coneSet.add(c4);
		
		coneSets.add(coneSet);
		
		coneSet = new HashSet<Cone>();
		coneSet.add(c5);
		coneSet.add(c6);
		coneSet.add(c7);
		
		coneSets.add(coneSet);
		
		partition = new ConicProgramPartition(program, coneSets);
	}
	
	@Test
	public void testCheckOutMatrices() {
		program.checkOutMatrices();
		partition.checkOutMatrices();
	}
	
	@Test
	public void testGetCutConstraints() {
		Set<LinearConstraint> cutConstraints = partition.getCutConstraints();
		
		assertTrue(cutConstraints.size() == 1);
		assertTrue(cutConstraints.contains(l2));
	}
}

