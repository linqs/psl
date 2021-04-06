package org.linqs.psl.util;

import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.AbstractBranchFormula;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Disjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.rule.arithmetic.AbstractGroundArithmeticRule;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.logical.AbstractLogicalRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class ModelDataCollector {
    private static final Logger log = LoggerFactory.getLogger(ModelDataCollector.class);

    private static Runtime runtime = null;
    private static ModelData modelData = null;

    private static String outputPath = null;

    static {
        init();
    }

    private ModelDataCollector() {}

    private static synchronized void init() {
        if (runtime != null) {
            return;
        }

        modelData = new ModelData();
        runtime = Runtime.getRuntime();
        runtime.addShutdownHook(new ShutdownHook());
    }

    public static void outputJSON() {

        try {
            if (outputPath == null) {
                throw new RuntimeException();
            }

            GZIPOutputStream stream = new GZIPOutputStream(new PrintStream(outputPath));
            writeToStream(stream);
            stream.close();
        } catch (IOException ex) {
            if (outputPath == null) {
                throw new RuntimeException("Path not specified for output file.");
            } else {
                throw new RuntimeException("Could not write to path: " + outputPath, ex);
            }
        }
    }

    /**
     * Write to stream with JSON formatting.
     */
    private static void writeToStream(FilterOutputStream stream) throws IOException {
        // Write each map as a JSON object, each JSON object is comma delimited.
        stream.write('{');

        stream.write("\"truthMap\":".getBytes());
        writeMap(stream, modelData.truthMap);
        stream.write(',');

        stream.write("\"rules\":".getBytes());
        writeMap(stream, modelData.rules);
        stream.write(',');

        stream.write("\"groundRules\":".getBytes());
        writeMap(stream, modelData.groundRules);
        stream.write(',');

        stream.write("\"groundAtoms\":".getBytes());
        writeMap(stream, modelData.groundAtoms);

        stream.write('}');
    }

    /**
     * Write map to stream with JSON formatting.
     * Assumption for optimizing write buffer: Map values use small amount of memory
     */
    private static void writeMap(FilterOutputStream stream, Object map) throws IOException {
        stream.write('{');

        @SuppressWarnings("unchecked")
        Map<String, Object> stringObjMap = (Map<String, Object>)map;
        Iterator<Map.Entry<String, Object>> iterator = stringObjMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            stream.write(("\"" + entry.getKey() + "\":").getBytes());

            // Values of the map will either be a Float or Map.
            if (entry.getValue() instanceof Float) {
                stream.write(entry.getValue().toString().getBytes());
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>)entry.getValue();
                JSONObject jsonObject = new JSONObject(data);
                stream.write(jsonObject.toString().getBytes());
            }

            if (iterator.hasNext()) {
                stream.write(',');
            }
        }

        stream.write('}');
    }

    private static class ShutdownHook extends Thread {
        @Override
        public void run() {
            outputJSON();
        }
    }

    public static class ModelData {
        public Map<String, Float> truthMap;
        public Map<String, Map<String, Object>> rules;
        public Map<String, Map<String, Object>> groundRules;
        public Map<String, Map<String, Object>> groundAtoms;

        public ModelData() {
            truthMap = new HashMap<String, Float>();
            rules = new HashMap<String, Map<String, Object>>();
            groundRules = new HashMap<String, Map<String, Object>>();
            groundAtoms = new HashMap<String, Map<String, Object>>();
        }
    }

    public static void setOutputPath(String path) {
        outputPath = path;
    }

    // Takes in a prediction truth pair and adds it to the Truth Map.
    public static void addTruth(GroundAtom target, float truthVal ) {
        String groundAtomID = Integer.toString(System.identityHashCode(target));
        modelData.truthMap.put(groundAtomID, truthVal);
    }

    // Adds a ground rules dissatisfaction / infeasibility to the groundRules HashMap.
    public static void dissatisfactionPerGroundRule(GroundRuleStore groundRuleStore) {
        for (GroundRule groundRule : groundRuleStore.getGroundRules()) {
            String strGroundRuleId = Integer.toString(System.identityHashCode(groundRule));
            Map<String, Object> groundRuleObj = modelData.groundRules.get(strGroundRuleId);
            if (groundRule instanceof WeightedGroundRule) {
                WeightedGroundRule weightedGroundRule = (WeightedGroundRule) groundRule;
                groundRuleObj.put("dissatisfaction", weightedGroundRule.getIncompatibility());
            } else {
                UnweightedGroundRule unweightedGroundRule = (UnweightedGroundRule) groundRule;
                groundRuleObj.put("dissatisfaction", unweightedGroundRule.getInfeasibility());
            }
        }
    }

    // Decorates an entry in a Formula to have constants or negations marked.
    public static Object decorateFormula(GroundAtom groundAtom, GroundRule groundRule, boolean negation) {
        if (negation) {
            return new Integer[] {System.identityHashCode(groundAtom), 1};
        }

        return System.identityHashCode(groundAtom);
    }

    // Parses through an atom object, gets ground atoms from AtomManager via predicate and arguments.
    public static GroundAtom parseAtom (Formula formula, Map<Variable, Constant> varConstMap, AtomManager atomManager) {
        Atom atom = (Atom)formula;
        Predicate predicate = atom.getPredicate();
        Term[] arguments = atom.getArguments();

        Constant[] constants = new Constant[2]; // Atoms will have 2 constants each, so can used a fixed array size to pass into atomManager.getAtom()
        int i = 0;
        for (Term t : arguments){
            if (!(t instanceof Variable)) {
                continue;
            }
            constants[i] = varConstMap.get(t);
            i++;
        }
        if (predicate instanceof StandardPredicate) {
            return atomManager.getAtom((StandardPredicate)predicate, constants);
        }

        return null;
    }

    // Parses through a formula object, and creates the needed object for modelData object consumption.
    public static List<Object> parseFormula(Formula formula, Map<Variable, Constant> varConstMap, GroundRule groundRule, boolean negation, AtomManager atomManager) {
        ArrayList<Object> groundAtoms = new ArrayList<Object>();
        if (formula instanceof QueryAtom){
            GroundAtom groundedAtom = parseAtom(formula, varConstMap, atomManager);
            if (groundedAtom != null) {
                Object decoratedFormula = decorateFormula(groundedAtom, groundRule, negation);
                if (decoratedFormula != null) {
                    groundAtoms.add(decoratedFormula);
                }
            }
        } else {
            AbstractBranchFormula branchFormula = (AbstractBranchFormula) formula;
            for (int i = 0; i < branchFormula.length(); i++){
                GroundAtom groundedAtom = parseAtom(branchFormula.get(i), varConstMap, atomManager);
                if (groundedAtom != null) {
                    Object decoratedFormula = decorateFormula(groundedAtom, groundRule, negation);
                    if (decoratedFormula != null) {
                        groundAtoms.add(decoratedFormula);
                    }
                }
            }
        }
        return groundAtoms;
    }

    public static synchronized void addGroundRule(AbstractRule parentRule,
            GroundRule groundRule, Map<Variable, Integer> variableMap,  Constant[] constantsList, AtomManager atomManager) {
        if (groundRule == null) {
            return;
        }

        // Create the variable constant map used for replacement.
        Map<Variable, Constant> varConstMap = new HashMap<Variable, Constant>();
        for (Map.Entry<Variable, Integer> entry : variableMap.entrySet()) {
            varConstMap.put(entry.getKey(), constantsList[entry.getValue()]);
        }

        // Captures the lhs and rhs of a ground rule in order to add to the modelData object.
        String groundRuleString;
        List<Object> lhs = new ArrayList<Object>();
        List<Object> rhs = new ArrayList<Object>();
        String operator = "";
        if (parentRule instanceof AbstractLogicalRule) {
            AbstractLogicalRule abstractLogicalParent = (AbstractLogicalRule) parentRule;
            Formula formula = abstractLogicalParent.getFormula();
            boolean negationFlag = false;
            if (formula instanceof Implication) {
                operator = ">>";
                Implication implication = (Implication) formula;
                Formula body = implication.getBody();
                Formula head = implication.getHead();
                if (body instanceof Negation) {
                    Negation negation = (Negation) body;
                    body = negation.getFormula();
                    negationFlag = true;
                }
                lhs = parseFormula(body, varConstMap, groundRule, negationFlag, atomManager);

                if (head instanceof Negation) {
                    Negation negation = (Negation) head;
                    head = negation.getFormula();
                    negationFlag = true;
                }
                rhs = parseFormula(head, varConstMap, groundRule, negationFlag, atomManager);
            } else if (formula instanceof Conjunction || formula instanceof Disjunction){
                lhs = parseFormula(formula, varConstMap, groundRule, negationFlag, atomManager);
            } else if (formula instanceof Negation){
                Negation negation = (Negation) formula;
                Formula negationFormula = negation.getFormula();
                negationFlag = true;
                lhs = parseFormula(negationFormula, varConstMap, groundRule, negationFlag, atomManager);
            }
        } else {
            AbstractGroundArithmeticRule abstractArithmetic = (AbstractGroundArithmeticRule) groundRule;
            GroundAtom[] orderedAtoms = abstractArithmetic.getOrderedAtoms();
            float[] coefficients = abstractArithmetic.getCoefficients();

            for (int i = 0; i < orderedAtoms.length; i++) {
                if (i < coefficients.length) {
                    Object[] atomObject = {System.identityHashCode(orderedAtoms[i]), coefficients[i]};
                    lhs.add(atomObject);
                } else {
                    lhs.add(System.identityHashCode(orderedAtoms[i]));
                }
            }
            operator = abstractArithmetic.getComparator().toString();
        }

        // Adds a groundAtom element to the modelData object.
        ArrayList<Integer> atomHashList = new ArrayList<Integer>();
        HashSet<GroundAtom> atomSet = new HashSet<GroundAtom>(groundRule.getAtoms());
        int atomCount = 0;
        for (GroundAtom groundAtom : atomSet) {
            atomHashList.add(System.identityHashCode(groundAtom));
            Map<String, Object> groundAtomElement = new HashMap<String, Object>();
            groundAtomElement.put("text", groundAtom.toString());
            groundAtomElement.put("prediction", groundAtom.getValue());
            modelData.groundAtoms.put(Integer.toString(System.identityHashCode(groundAtom)), groundAtomElement);
            atomCount++;
        }

        // Adds a rule element to the modelData object.
        String ruleStringID = Integer.toString(System.identityHashCode(parentRule));
        Map<String, Object> rulesElementItem = new HashMap<String, Object>();
        rulesElementItem.put("text", parentRule.getName());
        if (parentRule instanceof WeightedRule) {
            WeightedRule weightedParentRule = (WeightedRule) parentRule;
            rulesElementItem.put("weighted", weightedParentRule.getWeight());
        } else {
            rulesElementItem.put("weighted", null);
        }
        modelData.rules.put(ruleStringID, rulesElementItem);

        // Adds a groundRule element to the modelData object.
        Map<String, Object> groundRulesElement = new HashMap<String, Object>();
        groundRulesElement.put("ruleID", Integer.parseInt(ruleStringID));
        groundRulesElement.put("lhs", lhs);
        groundRulesElement.put("rhs", rhs);
        groundRulesElement.put("operator", operator);
        String groundRuleStringID = Integer.toString(System.identityHashCode(groundRule));
        modelData.groundRules.put(groundRuleStringID, groundRulesElement);
    }
}
