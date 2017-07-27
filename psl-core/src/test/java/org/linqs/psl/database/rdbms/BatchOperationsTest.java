package org.linqs.psl.database.rdbms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.linqs.psl.TestModelFactory;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BatchOperationsTest {
	private TestModelFactory.ModelInformation model;
	private Database database;

   @Before
   public void setup() {
		model = TestModelFactory.getModel();
		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		toClose.add(model.predicates.get("Nice"));
		toClose.add(model.predicates.get("Person"));
		database = model.dataStore.getDatabase(model.targetPartition, toClose, model.observationPartition);
   }

	@Test
	public void testSerial() {
      List<RandomVariableAtom> atoms = new ArrayList<RandomVariableAtom>();

      // TEST
      long start = System.currentTimeMillis();
      for (int i = 0; i < 100; i++) {
         for (int j = 0; j < 100; j++) {
            atoms.add((RandomVariableAtom)database.getAtom(
                  model.predicates.get("Friends"),
                  model.dataStore.getUniqueID("" + i), model.dataStore.getUniqueID("" + j)));
         }
      }

      // TEST
      System.out.println("Serial Fetch time: " + (System.currentTimeMillis() - start));

      // TEST
      start = System.currentTimeMillis();
      for (RandomVariableAtom atom : atoms) {
         atom.commitToDB();
      }

      // TEST
      System.out.println("Serial Commit time: " + (System.currentTimeMillis() - start));
	}

	@Test
	public void testBatch() {
      List<RandomVariableAtom> atoms = new ArrayList<RandomVariableAtom>();

      // TEST
      long start = System.currentTimeMillis();
      for (int i = 0; i < 100; i++) {
         for (int j = 0; j < 100; j++) {
            atoms.add((RandomVariableAtom)database.getAtom(
                  model.predicates.get("Friends"),
                  model.dataStore.getUniqueID("" + i), model.dataStore.getUniqueID("" + j)));
         }
      }

      // TEST
      System.out.println("Batch Fetch time: " + (System.currentTimeMillis() - start));

      // TEST
      start = System.currentTimeMillis();

      // TEST
      database.commit(atoms);

      // TEST
      System.out.println("Batch Commit time: " + (System.currentTimeMillis() - start));
	}

	@After
	public void cleanup() {
		database.close();
		model.dataStore.close();
	}
}
