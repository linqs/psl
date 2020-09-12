package org.linqs.psl.util;

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

public class VizDataCollection {
    private static final Logger log = LoggerFactory.getLogger(VizDataCollection.class);

    private static Runtime runtime = null;
    private static VisualizationData vizData = null;

    private static String outputPath = null;

    static {
        init();
    }

    private VizDataCollection() {}

    private static synchronized void init() {
        if (runtime != null) {
            return;
        }
        vizData = new VisualizationData();
        runtime = Runtime.getRuntime();
        runtime.addShutdownHook(new ShutdownHook());
    }

    public static void outputJSON() throws IOException {
        FilterOutputStream stream = System.out;

        if (outputPath != null) {
            try {
                stream = new GZIPOutputStream(new PrintStream(outputPath));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        writeToStream(stream);

        if (outputPath != null) {
            stream.close();
        }
    }

    /**
     * Write to stream with JSON formatting.
     */
    private static void writeToStream(FilterOutputStream stream) throws IOException {
        // JSON format reference: https://www.json.org/json-en.html.
        stream.write("{ \"truthMap\" :".getBytes());

        // Write each map as a JSON object, each JSON object is comma delimited.
        writeMap(stream, vizData.truthMap, "truthMap");
        stream.write(", \"rules\" :".getBytes());
        writeMap(stream, vizData.rules, "rules");
        stream.write(", \"groundRules\" :".getBytes());
        writeMap(stream, vizData.groundRules, "groundRules");
        stream.write(", \"groundAtoms\" :".getBytes());
        writeMap(stream, vizData.groundAtoms, "groundAtoms");

        stream.write('}');
    }

    /**
     * Write map to stream with JSON formatting.
     */
    @SuppressWarnings("unchecked")
    private static void writeMap(FilterOutputStream stream, Object map, String z) throws IOException {
        stream.write('{');

        Map<String, Object> stringObjMap = (Map<String, Object>) map;
        Iterator<Map.Entry<String, Object>> iterator = stringObjMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            stream.write((" \"" + entry.getKey() + "\" :").getBytes());

            // Values of the map will either be a Float or Map
            if (entry.getValue() instanceof Float) {
                stream.write(entry.getValue().toString().getBytes());
            } else {
                // Assumption that the JSON Objects carry small amounts of data
                Map<String, Object> data = (Map<String, Object>) entry.getValue();
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
            try {
                outputJSON();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public static class VisualizationData {
        public Map<String, Float> truthMap;
        public Map<String, Map<String, Object>> rules;
        public Map<String, Map<String, Object>> groundRules;
        public Map<String, Map<String, Object>> groundAtoms;

        public VisualizationData() {
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
        vizData.truthMap.put(groundAtomID, truthVal);
    }

    // Adds a ground rules dissatisfaction / infeasibility.
    public static void dissatisfactionPerGroundRule(GroundRuleStore groundRuleStore) {
        for (GroundRule groundRule : groundRuleStore.getGroundRules()) {
            String strGroundRuleId = Integer.toString(System.identityHashCode(groundRule));
            Map<String, Object> groundRuleObj = vizData.groundRules.get(strGroundRuleId);
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
    public static Object decorateFormula(String groundAtom, GroundRule groundRule, boolean negation){
        for (GroundAtom atom : groundRule.getAtoms()) {
            if (groundAtom.contains(atom.toString())){
                if (negation) {
                    Integer[] groundAtomObj = {System.identityHashCode(atom), 1};
                    return groundAtomObj;
                }
                else {
                    return System.identityHashCode(atom);
                }
            }
        }
        return null;
    }

    // Parses through an atom object, and fills out variables with their proper values.
    public static String parseAtom (Formula f, Map<String, String> varConstMap) {
        Atom atom = (Atom) f;
        String groundedAtom = atom.toString();
        Term[] arguments = atom.getArguments();
        for (Term t : arguments){
            if (t instanceof Variable) {
                // Only want to replace terms, to do so make sure they are in a parenthesis.
                String leftVarParen = "(" + t.toString();
                String rightVarParen = t.toString() + ")";
                if (groundedAtom.contains(leftVarParen)) {
                    String replacement = "(\'" + varConstMap.get(t.toString()) + "\'";
                    groundedAtom = groundedAtom.replace(leftVarParen, replacement);
                }
                else if (groundedAtom.contains(rightVarParen)) {
                    String replacement = "\'" + varConstMap.get(t.toString()) + "\')";
                    groundedAtom = groundedAtom.replace(rightVarParen, replacement);
                }
            }
        }
        return groundedAtom;
    }

    // Parses through a formula object, and creates the needed object for vizData object consumption.
    public static ArrayList<Object> parseFormula(Formula f, Map<String, String> varConstMap, GroundRule groundRule, boolean negation) {
        ArrayList<Object> groundAtoms = new ArrayList<Object>();
        if (f instanceof QueryAtom){
            String groundedAtom = parseAtom(f, varConstMap);
            Object decoratedFormula = decorateFormula(groundedAtom, groundRule, negation);
            if (decoratedFormula != null) {
                groundAtoms.add(decoratedFormula);
            }
        }
        else {
            AbstractBranchFormula branchFormula = (AbstractBranchFormula) f;
            for (int i = 0; i < branchFormula.length(); i++){
                String groundedAtom = parseAtom(branchFormula.get(i), varConstMap);
                Object decoratedFormula = decorateFormula(groundedAtom, groundRule, negation);
                if (decoratedFormula != null) {
                    groundAtoms.add(decoratedFormula);
                }
            }
        }
        return groundAtoms;
    }

    public static synchronized void addGroundRule(AbstractRule parentRule,
            GroundRule groundRule, Map<Variable, Integer> variableMap,  Constant[] constantsList) {
        if (groundRule == null) {
            return;
        }

        // Create the variable constant map used for replacement.
        Map<String, String> varConstMap = new HashMap<String, String>();
        for (Map.Entry<Variable, Integer> entry : variableMap.entrySet()) {
            varConstMap.put(entry.getKey().toString(), constantsList[entry.getValue()].rawToString());
        }

        // Captures the lhs and rhs of a ground rule in order to add to the vizData object.
        String groundRuleString;
        ArrayList<Object> lhs = new ArrayList<Object>();
        ArrayList<Object> rhs = new ArrayList<Object>();
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
                lhs = parseFormula(body, varConstMap, groundRule, negationFlag);
                if (head instanceof Negation) {
                    Negation negation = (Negation) head;
                    head = negation.getFormula();
                    negationFlag = true;
                }
                rhs = parseFormula(head, varConstMap, groundRule, negationFlag);
            }
            else if (formula instanceof Conjunction || formula instanceof Disjunction){
                lhs = parseFormula(formula, varConstMap, groundRule, negationFlag);
            }
            else if (formula instanceof Negation){
                Negation negation = (Negation) formula;
                Formula negationFormula = negation.getFormula();
                negationFlag = true;
                lhs = parseFormula(negationFormula, varConstMap, groundRule, negationFlag);
            }
        } else {
            AbstractGroundArithmeticRule abstractArithmetic = (AbstractGroundArithmeticRule) groundRule;
            GroundAtom[] orderedAtoms = abstractArithmetic.getOrderedAtoms();
            float[] coefficients = abstractArithmetic.getCoefficients();

            for (int i = 0; i < orderedAtoms.length; i++) {
                if (i < coefficients.length) {
                    Object[] atomObject = {System.identityHashCode(orderedAtoms[i]), coefficients[i]};
                    lhs.add(atomObject);
                }
                else {
                    lhs.add(System.identityHashCode(orderedAtoms[i]));
                }
            }
            operator = abstractArithmetic.getComparator().toString();
        }

        // Adds a groundAtom element to the vizData object.
        ArrayList<Integer> atomHashList = new ArrayList<Integer>();
        HashSet<GroundAtom> atomSet = new HashSet<GroundAtom>(groundRule.getAtoms());
        int atomCount = 0;
        for (GroundAtom groundAtom : atomSet) {
            atomHashList.add(System.identityHashCode(groundAtom));
            Map<String, Object> groundAtomElement = new HashMap<String, Object>();
            groundAtomElement.put("text", groundAtom.toString());
            groundAtomElement.put("prediction", groundAtom.getValue());
            vizData.groundAtoms.put(Integer.toString(System.identityHashCode(groundAtom)), groundAtomElement);
            atomCount++;
        }

        // Adds a rule element to the vizData object.
        String ruleStringID = Integer.toString(System.identityHashCode(parentRule));
        Map<String, Object> rulesElementItem = new HashMap<String, Object>();
        rulesElementItem.put("text", parentRule.getName());
        if (parentRule instanceof WeightedRule) {
            WeightedRule weightedParentRule = (WeightedRule) parentRule;
            rulesElementItem.put("weighted", weightedParentRule.getWeight());
        }
        else {
            rulesElementItem.put("weighted", null);
        }
        vizData.rules.put(ruleStringID, rulesElementItem);

        // Adds a groundRule element to the vizData object.
        Map<String, Object> groundRulesElement = new HashMap<String, Object>();
        groundRulesElement.put("ruleID", Integer.parseInt(ruleStringID));
        groundRulesElement.put("lhs", lhs);
        groundRulesElement.put("rhs", rhs);
        groundRulesElement.put("operator", operator);
        String groundRuleStringID = Integer.toString(System.identityHashCode(groundRule));
        vizData.groundRules.put(groundRuleStringID, groundRulesElement);
    }
}
