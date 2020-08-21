package org.linqs.psl.util;

import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.logical.AbstractLogicalRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

    public static void outputJSON() {
        String[] keyNames = {"truthMap", "rules", "groundRules", "groundAtoms"};
        JSONObject fullJson = new JSONObject(vizData, keyNames);

        PrintStream stream = System.out;

        if (outputPath != null) {
            try {
                stream = new PrintStream(outputPath);
                if (outputPath.endsWith(".gz")) {
                    GZIPOutputStream gzipStream = new GZIPOutputStream(stream, true);
                    byte[] jsonByteArray = fullJson.toString().getBytes();
                    gzipStream.write(jsonByteArray, 0, jsonByteArray.length);
                    gzipStream.close();
                } else {
                    stream.println(fullJson.toString());
                }
                stream.close();
            } catch (IOException ex) {
                throw new RuntimeException();
            }
        } else {
            stream.println(fullJson.toString());
        }
    }

    private static class ShutdownHook extends Thread {
        @Override
        public void run() {
            outputJSON();
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
            if (groundRule instanceof WeightedGroundRule) {
                Map<String, Object> groundRuleObj = vizData.groundRules.get(strGroundRuleId);
                WeightedGroundRule weightedGroundRule = (WeightedGroundRule) groundRule;
                groundRuleObj.put("dissatisfaction", weightedGroundRule.getIncompatibility());
            }
        }
    }

    public static List<String> matchAll(Pattern p, String targetString) {
        List<String> matches = new ArrayList<String>();
        Matcher m = p.matcher(targetString);
        while (m.find()) {
          matches.add(m.group());
          System.out.println(m.group());
          System.out.println(m.start());
          System.out.println(m.end());
        }
        return matches;
    }

    public static String createLogicalGroundRule(AbstractLogicalRule parentRule, Map<String, String> varConstMap) {
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

    // TODO: Arithmetic Ground Rules Collection
    public static synchronized void addGroundRule(AbstractLogicalRule parentRule,
            GroundRule groundRule, Map<Variable, Integer> variableMap,  Constant[] constantsList) {

        //TEST NOTE
        // Arithmetic ground rules will be in non-DNF
        // normal ground rules will be in dnf

        //TODO:
        // So maybe when we pass into this function we can have a flag saying if its an
        // arithmetic or not (or just check the type duh).
        // If not, we parse how we normally have been doing it (rely on constant map)
        // If so, then we just parse over non-DNF ground rule

        //This would mean parseing on the parent / ground rule and trying to
        // recreate as that is just a system for doing this without constant map

        //This should solve parsing problems

        //TODO:
        // We will also have to think of a way to keep the ID system intact
        // as our interaction systems rely on those

        if (groundRule == null) {
            return;
        }

        // Create the variable constant map used for replacement.
        Map<String, String> varConstMap = new HashMap<String, String>();
        for (Map.Entry<Variable, Integer> entry : variableMap.entrySet()) {
            varConstMap.put(entry.getKey().toString(), constantsList[entry.getValue()].rawToString());
        }

        createLogicalGroundRule(parentRule, varConstMap);

        System.out.println("<------------------->");

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

        // Adds a rule element to RuleMap
        String ruleStringID = Integer.toString(System.identityHashCode(parentRule));
        Map<String, Object> rulesElementItem = new HashMap<String, Object>();
        rulesElementItem.put("text", parentRule.getName());
        rulesElementItem.put("weighted", parentRule.isWeighted());
        vizData.rules.put(ruleStringID, rulesElementItem);

        Map<String, Object> groundRulesElement = new HashMap<String, Object>();
        groundRulesElement.put("ruleID", Integer.parseInt(ruleStringID));
        Map<String, Object> constants = new HashMap<String, Object>();
        for (Map.Entry varConstElement : varConstMap.entrySet()) {
          String key = (String)varConstElement.getKey();
          String val = (String)varConstElement.getValue();
          constants.put(key,val);
        }

        groundRulesElement.put("constants", constants);
        groundRulesElement.put("groundAtoms", atomHashList);
        String groundRuleStringID = Integer.toString(System.identityHashCode(groundRule));
        vizData.groundRules.put(groundRuleStringID, groundRulesElement);
    }
}
