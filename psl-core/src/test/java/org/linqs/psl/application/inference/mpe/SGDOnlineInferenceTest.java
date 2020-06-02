package org.linqs.psl.application.inference.mpe;

import org.junit.Before;
import org.junit.Test;
import org.linqs.psl.TestModel;
import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.online.actions.OnlineAction;
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
import org.linqs.psl.model.term.*;
import org.linqs.psl.reasoner.sgd.term.SGDObjectiveTerm;
import org.linqs.psl.reasoner.term.VariableTermStore;
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

    private void queueCommands(SGDOnlineInference inference, ArrayList<String> commands){
        String[] tokenized_command;

        for(String command : commands) {
            tokenized_command = command.split("\t");
            OnlineAction action = OnlineAction.getOnlineAction(tokenized_command[0]);
            action.initAction(tokenized_command);
            inference.server.enqueue(action);
        }
    }

    private GroundAtom getAtom(SGDOnlineInference inference, String predicateName, String[] argumentStrings) {
        Constant[] arguments = new Constant[argumentStrings.length];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = new UniqueStringID(argumentStrings[i]);
        }

        Predicate predicate = Predicate.get(predicateName);
        return inference.getAtomManager().getAtom(predicate, arguments);
    }

    private float getAtomValue(SGDOnlineInference inference, String predicateName, String[] argumentStrings) {
        StreamingTermStore termstore = (StreamingTermStore)inference.getTermStore();
        GroundAtom atom = getAtom(inference, predicateName, argumentStrings);

        return termstore.getVariableValue(termstore.getVariableIndex(atom));
    }

    @Test
    public void testUpdateObservation(){
        SGDOnlineInference inference = (SGDOnlineInference)getInference(modelInfo.model.getRules(), inferDB);
        ArrayList<String> commands = new ArrayList<String>(Arrays.asList(
                "UpdateObservation\tSim_Users\tAlice\tEddie\t0.0",
                "WriteInferredPredicates",
                "Close"));

        queueCommands(inference, commands);
        inference.inference();

        float atomValue = getAtomValue(inference, "Sim_Users", new String[]{"Alice", "Eddie"});
        assertEquals(atomValue, 0.0, 0.01);
    }

    @Test
    public void testAddAtoms(){
        SGDOnlineInference inference = (SGDOnlineInference)getInference(modelInfo.model.getRules(), inferDB);
        ArrayList<String> commands = new ArrayList<String>(Arrays.asList(
                "AddAtom\tRead\tSim_Users\tAlice\tConnor\t1.0",
                "AddAtom\tWrite\tRating\tConnor\tAvatar",
                "WriteInferredPredicates",
                "Close"));

        queueCommands(inference, commands);
        inference.inference();

        VariableTermStore<SGDObjectiveTerm, GroundAtom> termStore = (VariableTermStore<SGDObjectiveTerm, GroundAtom>)inference.getTermStore();

        float atomValue = getAtomValue(inference, "Rating", new String[]{"Connor", "Avatar"});
        assertEquals(atomValue, 1.0, 0.01);
    }

    @Test
    public void testPageRewriting(){
        Options.STREAMING_TS_PAGE_SIZE.set(2);
        SGDOnlineInference inference = (SGDOnlineInference)getInference(modelInfo.model.getRules(), inferDB);
        ArrayList<String> commands = new ArrayList<String>(Arrays.asList(
                "AddAtom\tRead\tSim_Users\tConnor\tAlice\t0.0",
                "AddAtom\tRead\tSim_Users\tAlice\tConnor\t0.0",
                "AddAtom\tWrite\tRating\tConnor\tAvatar",
                "WriteInferredPredicates",
                "AddAtom\tRead\tSim_Users\tConnor\tBob\t1.0",
                "AddAtom\tRead\tSim_Users\tBob\tConnor\t1.0",
                "AddAtom\tRead\tRating\tBob\tSurfs Up\t0.5",
                "AddAtom\tWrite\tRating\tConnor\tSurfs Up",
                "WriteInferredPredicates",
                "Close"));

        queueCommands(inference, commands);
        inference.inference();

        float atomValue = getAtomValue(inference, "Rating", new String[]{"Connor", "Avatar"});
        assertEquals(atomValue, 1.0, 0.01);
    }

    @Test
    public void testAtomDeleting(){
        SGDOnlineInference inference = (SGDOnlineInference)getInference(modelInfo.model.getRules(), inferDB);
        ArrayList<String> commands = new ArrayList<String>(Arrays.asList(
                "DeleteAtom\tRead\tSim_Users\tAlice\tEddie",
                "AddAtom\tRead\tSim_Users\tAlice\tEddie\t1.0",
                "DeleteAtom\tRead\tSim_Users\tAlice\tEddie",
                "WriteInferredPredicates",
                "Close"));
        queueCommands(inference, commands);

        int numTerms = 0;
        VariableTermStore<SGDObjectiveTerm, GroundAtom> termStore = (VariableTermStore<SGDObjectiveTerm, GroundAtom>)inference.getTermStore();
        for (SGDObjectiveTerm term : termStore) {
            numTerms++;
        }
        assertEquals(numTerms, 2.0, 0.01);

        inference.inference();

        numTerms = 0;
        for (SGDObjectiveTerm term : termStore) {
            System.out.println(term);
            numTerms++;
        }
        assertEquals(numTerms, 1.0, 0.01);
    }

    @Test
    public void testChangeAtomPartition(){
        Options.STREAMING_TS_PAGE_SIZE.set(4);
        SGDOnlineInference inference = (SGDOnlineInference)getInference(modelInfo.model.getRules(), inferDB);
        ArrayList<String> commands = new ArrayList<String>(Arrays.asList(
                "AddAtom\tRead\tSim_Users\tConnor\tAlice\t1.0",
                "AddAtom\tRead\tSim_Users\tAlice\tConnor\t1.0",
                "AddAtom\tWrite\tRating\tConnor\tAvatar",
                "AddAtom\tRead\tSim_Users\tConnor\tBob\t1.0",
                "AddAtom\tRead\tSim_Users\tBob\tConnor\t1.0",
                "AddAtom\tRead\tRating\tBob\tSurfs Up\t0.5",
                "AddAtom\tWrite\tRating\tConnor\tSurfs Up",
                "AddAtom\tRead\tRating\tAlice\tAvatar\t0.5",
                "WriteInferredPredicates",
                "Close"));

        queueCommands(inference, commands);
        inference.inference();
        float atomValue = getAtomValue(inference, "Rating", new String[]{"Alice", "Avatar"});
        assertEquals(atomValue, 0.5, 0.01);
    }
}
