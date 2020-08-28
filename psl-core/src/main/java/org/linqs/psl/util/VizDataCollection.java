package org.linqs.psl.util;

import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.rule.logical.AbstractLogicalRule;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.FilterOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    public static void dissatisfactionPerGroundRule(GroundRuleStore groundRuleStore) {
        for (GroundRule groundRule : groundRuleStore.getGroundRules()) {
            String strGroundRuleId = Integer.toString(System.identityHashCode(groundRule));
            Map<String, Object> groundRuleObj = vizData.groundRules.get(strGroundRuleId);
            if (groundRule instanceof WeightedGroundRule) {
                WeightedGroundRule weightedGroundRule = (WeightedGroundRule) groundRule;
                groundRuleObj.put("dissatisfaction", weightedGroundRule.getIncompatibility());
            }
        }
    }

    public static String createLogicalGroundRule(AbstractRule parentRule, Map<String, String> varConstMap) {
        // Create patterns to find predicates / constants and label to be placed on them.
        Pattern predicatePattern = Pattern.compile("\\w+\\s*\\(");
        Pattern constantPattern = Pattern.compile("\\'\\w+\\'");
        String nonVariableLabel = "__0_";

        // Find all instances of predicates and constants in the parent rule.
        Matcher predicateMatcher = predicatePattern.matcher(parentRule.getName());
        Matcher constantMatcher = constantPattern.matcher(parentRule.getName());

        // Collect indices for all predicates and constants so we can label them.
        ArrayList<Integer> indicies = new ArrayList<Integer>();
        while (predicateMatcher.find()) {
            indicies.add(predicateMatcher.start());
        }
        while (constantMatcher.find()) {
            indicies.add(constantMatcher.start());
        }

        //Sort in descending order so we can places labels with collected indicies.
        Collections.sort(indicies, Collections.reverseOrder());

        // Apply the lables to a copy of the parent rule.
        String createdGroundRule = parentRule.getName();
        for (int index : indicies){
            createdGroundRule = createdGroundRule.substring(0,index) + nonVariableLabel + createdGroundRule.substring(index);
        }

        // Replace all variables in the labeled parent rule.
        for (Map.Entry<String, String> entry : varConstMap.entrySet()) {
            String re = "\\b"+entry.getKey()+"\\b";
            // Add surrounding single quotes to variables;
            String constant = "\'" + entry.getValue() + "\'";
            createdGroundRule = createdGroundRule.replaceAll(re, constant);
        }

        // Get rid of all labels.
        createdGroundRule = createdGroundRule.replaceAll(nonVariableLabel, "");

        return createdGroundRule;
    }


    // Decorates a given linear combination
    public static ArrayList<Object> decorateGroundAtomList(String[] linearCombination, GroundRule groundRule) {
        ArrayList<Object> decoratedList = new ArrayList<Object>();
        for (int i = 0; i < linearCombination.length; i++) {
            String prev = "";
            if ((i-1) > -1) prev = linearCombination[i-1];
            for (GroundAtom atom : groundRule.getAtoms()){
                if (linearCombination[i].contains(atom.toString())) {
                    if (prev.matches("-?\\d+\\.\\d+")){
                        Integer[] groundAtom = {System.identityHashCode(atom), (int) Double.parseDouble(linearCombination[i-1])};
                        decoratedList.add(groundAtom);
                    }
                    else {
                        decoratedList.add(System.identityHashCode(atom));
                    }
                }
            }
        }
        return decoratedList;
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

        // Get the Non-DNF ground rule
        String groundRuleString;
        if (parentRule instanceof AbstractLogicalRule) {
            groundRuleString = createLogicalGroundRule(parentRule, varConstMap);
        }
        else {
            groundRuleString = groundRule.baseToString();
        }

        // Get the operator of the ground rule string
        Pattern opPattern = Pattern.compile("\\)\\s?([=>><]+)\\s?");
        Matcher opMatcher = opPattern.matcher(groundRuleString);
        String operator = "";
        while (opMatcher.find()) {
            operator = opMatcher.group(1);
        }

        // Split the string into lhs and rhs via the found operator
        String lhsGroundRule;
        String rhsGroundRule;
        if (operator != "") {
            String[] splitGroundRule = groundRuleString.split(operator);
            lhsGroundRule = splitGroundRule[0];
            rhsGroundRule = splitGroundRule[1];
        }
        else {
            lhsGroundRule = groundRuleString;
            rhsGroundRule = "";
        }

        // Split the sides into their atoms
        String[] lhsList;
        String[] rhsList;
        lhsList = lhsGroundRule.split("\\s[&+*/-]\\s");
        rhsList = rhsGroundRule.split("\\s[&+*/-]\\s");

        //  Gather atoms via getAtoms, and create pairs if needed
        ArrayList<Object> lhsDecoratedList = decorateGroundAtomList(lhsList, groundRule);
        ArrayList<Object> rhsDecoratedList = decorateGroundAtomList(rhsList, groundRule);

        // Adds a groundAtom element to RuleMap
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

        // Adds a rule element to RuleMap.
        String ruleStringID = Integer.toString(System.identityHashCode(parentRule));
        Map<String, Object> rulesElementItem = new HashMap<String, Object>();
        rulesElementItem.put("text", parentRule.getName());
        rulesElementItem.put("weighted", parentRule.isWeighted());
        vizData.rules.put(ruleStringID, rulesElementItem);

        // Adds a groundRule element to RuleMap
        Map<String, Object> groundRulesElement = new HashMap<String, Object>();
        groundRulesElement.put("ruleID", Integer.parseInt(ruleStringID));
        groundRulesElement.put("lhs", lhsDecoratedList);
        groundRulesElement.put("rhs", rhsDecoratedList);
        groundRulesElement.put("operator", operator);
        String groundRuleStringID = Integer.toString(System.identityHashCode(groundRule));
        vizData.groundRules.put(groundRuleStringID, groundRulesElement);
    }
}
