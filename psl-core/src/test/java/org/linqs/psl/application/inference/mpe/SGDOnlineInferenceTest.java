package org.linqs.psl.application.inference.mpe;

import org.junit.Before;
import org.junit.Test;
import org.linqs.psl.TestModel;
import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.online.actions.AddAtom;
import org.linqs.psl.application.inference.online.actions.Close;
import org.linqs.psl.application.inference.online.actions.OnlineAction;
import org.linqs.psl.application.inference.online.actions.UpdateObservation;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseTestUtil;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.predicate.GroundingOnlyPredicate;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class SGDOnlineInferenceTest {
    private TestModel.ModelInformation modelInfo;
    private Database inferDB;
    private DatabaseDriver driver = DatabaseTestUtil.getH2Driver();
    private Map<String, StandardPredicate> baselinePredicates = new HashMap<>();
    private List<Rule> baselineRules = new ArrayList<Rule>();
    private Map<StandardPredicate, List<TestModel.PredicateData>> baselineObservations = new HashMap<>();
    private Map<StandardPredicate, List<TestModel.PredicateData>> baselineTargets = new HashMap<>();
    private Map<StandardPredicate, List<TestModel.PredicateData>> baselineTruths = new HashMap<>();

    /**
     * Initialize a baseline model that we will be modifying with the online inference application
     */
    private void initBaselineModel() {
        // Define Predicates
        Map<String, ConstantType[]> predicatesInfo = new HashMap<String, ConstantType[]>();
        predicatesInfo.put("Sim_Users", new ConstantType[]{ConstantType.UniqueStringID, ConstantType.UniqueStringID});
        predicatesInfo.put("Rating", new ConstantType[]{ConstantType.UniqueStringID, ConstantType.UniqueStringID});

        for (Map.Entry<String, ConstantType[]> predicateEntry : predicatesInfo.entrySet()) {
            StandardPredicate predicate = StandardPredicate.get(predicateEntry.getKey(), predicateEntry.getValue());
            baselinePredicates.put(predicateEntry.getKey(), predicate);
        }

        // Define Rules
        // Rating(U1, M) && Sim_Users(U1, U2) => Rating(U2, M)
        baselineRules.add(new WeightedLogicalRule(
                new Implication(
                        new Conjunction(
                                new QueryAtom(baselinePredicates.get("Rating"), new Variable("A"), new Variable("M")),
                                new QueryAtom(baselinePredicates.get("Sim_Users"), new Variable("A"), new Variable("B")),
                                new QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))
                        ),
                        new QueryAtom(baselinePredicates.get("Rating"), new Variable("B"), new Variable("M"))
                ),
                1.0,
                true));

        // Data

        // Observed
        // Rating
        baselineObservations.put(baselinePredicates.get("Rating"), new ArrayList<TestModel.PredicateData>(Arrays.asList(
                new TestModel.PredicateData(1.0, new Object[]{"Alice", "Avatar"})
        )));

        // Sim_Users
        baselineObservations.put(baselinePredicates.get("Sim_Users"), new ArrayList<TestModel.PredicateData>(Arrays.asList(
                new TestModel.PredicateData(1.0, new Object[]{"Alice", "Bob"}),
                new TestModel.PredicateData(1.0, new Object[]{"Bob", "Alice"}),
                new TestModel.PredicateData(1.0, new Object[]{"Eddie", "Alice"}),
                new TestModel.PredicateData(1.0, new Object[]{"Alice", "Eddie"}),
                new TestModel.PredicateData(0.0, new Object[]{"Eddie", "Bob"}),
                new TestModel.PredicateData(0.0, new Object[]{"Bob", "Eddie"})
        )));

        // Targets
        // Rating
        baselineTargets.put(baselinePredicates.get("Rating"), new ArrayList<TestModel.PredicateData>(Arrays.asList(
                new TestModel.PredicateData(new Object[]{"Eddie", "Avatar"}),
                new TestModel.PredicateData(new Object[]{"Bob", "Avatar"})
        )));

        // Truths
        baselineTruths.put(baselinePredicates.get("Rating"), new ArrayList<TestModel.PredicateData>(Arrays.asList(
                new TestModel.PredicateData(1.0, new Object[]{"Bob", "Avatar"}),
                new TestModel.PredicateData(1.0, new Object[]{"Eddie", "Avatar"})
        )));

        modelInfo = TestModel.getModel(driver, baselinePredicates, baselineRules,
                baselineObservations, baselineTargets, baselineTruths);
    }

    @Before
    public void setup() {
        if (inferDB != null) {
            inferDB.close();
            inferDB = null;
        }

        if (modelInfo != null) {
            modelInfo.dataStore.close();
            modelInfo = null;
        }

        Options.ONLINE.set(true);
        
        initBaselineModel();

        // Close the predicates we are using.
        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();

        inferDB = modelInfo.dataStore.getDatabase(modelInfo.targetPartition, toClose, modelInfo.observationPartition);
    }

    protected InferenceApplication getInference(List<Rule> rules, Database db) {
        return new SGDOnlineInference(rules, db);
    }

    @Test
    public void testUpdateObservation(){
        SGDOnlineInference inference = (SGDOnlineInference)getInference(modelInfo.model.getRules(), inferDB);

        // create new action
        String command = "UpdateObservation\tSim_Users\tAlice\tEddie\t0.0";
        String[] tokenized_command = command.split("\t");
        UpdateObservation updateObservation = (UpdateObservation)OnlineAction.getOnlineAction(tokenized_command[0]);
        updateObservation.initAction(tokenized_command);

        // create close action
        tokenized_command = "Close".split("\t");
        Close closeAction = (Close)OnlineAction.getOnlineAction(tokenized_command[0]);
        closeAction.initAction(tokenized_command);

        // add actions to queue
        inference.server.enqueue(updateObservation);
        inference.server.enqueue(closeAction);

        // Run Inference
        inference.inference();
        Predicate predicate = Predicate.get(updateObservation.getPredicateName());
        GroundAtom atom = inference.getAtomManager().getAtom(predicate, updateObservation.getArguments());
        assertEquals(((StreamingTermStore)inference.getTermStore()).getAtomValue(
                ((StreamingTermStore)inference.getTermStore()).getAtomIndex(atom)), 0.0, 0.01);
    }

    @Test
    public void testAddAtoms(){
        SGDOnlineInference inference = (SGDOnlineInference)getInference(modelInfo.model.getRules(), inferDB);
        String[] tokenized_command;
        String command;

        // create new action
        command = "AddAtom\tRead\tSim_Users\tConnor\tAlice\t1.0";
        tokenized_command = command.split("\t");
        AddAtom addAtom1 = (AddAtom)OnlineAction.getOnlineAction(tokenized_command[0]);
        addAtom1.initAction(tokenized_command);

        command = "AddAtom\tRead\tSim_Users\tAlice\tConnor\t1.0";
        tokenized_command = command.split("\t");
        AddAtom addAtom2 = (AddAtom)OnlineAction.getOnlineAction(tokenized_command[0]);
        addAtom2.initAction(tokenized_command);

        command = "AddAtom\tWrite\tRating\tConnor\tAvatar\t0.5";
        tokenized_command = command.split("\t");
        AddAtom addAtom3 = (AddAtom)OnlineAction.getOnlineAction(tokenized_command[0]);
        addAtom3.initAction(tokenized_command);

        // create close action
        tokenized_command = "Close".split("\t");
        Close closeAction = (Close)OnlineAction.getOnlineAction(tokenized_command[0]);
        closeAction.initAction(tokenized_command);

        // add actions to queue
        inference.server.enqueue(addAtom1);
        inference.server.enqueue(addAtom2);
        inference.server.enqueue(addAtom3);
        inference.server.enqueue(closeAction);

        // Run Inference
        inference.inference();
        System.out.println("Done");
    }
}
