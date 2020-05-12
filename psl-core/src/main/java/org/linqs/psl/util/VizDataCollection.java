package org.linqs.psl.util;

import org.linqs.psl.config.Options;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.logical.AbstractLogicalRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;

import org.json.JSONArray;
import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;


public class VizDataCollection {
    private static Runtime runtime = null;
    private static VisualizationData vizData = null;

    private static final Logger log = LoggerFactory.getLogger(VizDataCollection.class);

    public static String outputPath = null;

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
        boolean closeStream = false;

        if (outputPath != null) {
            try {
                stream = new PrintStream(outputPath);
                closeStream = true;
            } catch (IOException ex) {
                log.error(String.format("Unable to open file (%s) for visualization data, using stdout instead.", outputPath), ex);
            }
        }

        // Adding this string to our data file turns the json into a js variable
        String jsHeader = "window.pslviz = window.pslviz || {} \nwindow.pslviz.data = ";
        stream.println(jsHeader);
        stream.println(fullJson.toString(4));

        if (closeStream) {
            stream.close();
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

    // Takes in a prediction truth pair and adds it to our map
    public static void addTruth(GroundAtom target, float truthVal ) {
        String groundAtomID = Integer.toString(System.identityHashCode(target));
        vizData.truthMap.put(groundAtomID, truthVal);
    }

    public static void groundingsPerRule(List<Rule> rules, GroundRuleStore groundRuleStore) {
    for (Rule rule: rules) {
        String stringRuleId = Integer.toString(System.identityHashCode(rule));
        int groundRuleCount = groundRuleStore.count( rule );
        // Abstract Arithmetic Rules are not currently being added to the data collection
        if ( vizData.rules.get(stringRuleId) == null ) {
            HashMap<String, Object> newRuleElementItem = new HashMap<String, Object>();
            newRuleElementItem.put("text", rule.getName());
            vizData.rules.put(stringRuleId, newRuleElementItem);
        }
        Map<String, Object> ruleElement = vizData.rules.get(stringRuleId);
        ruleElement.put("count", groundRuleCount);
        ruleElement.put("weighted", rule.isWeighted());
        }
    }

    public static void ruleMapInsertElement(AbstractLogicalRule parentRule, GroundRule groundRule,
                            Map<Variable, Integer> variableMap,  Constant[] constantsList) {
        if (groundRule == null) {
            return;
        }
        // Adds a groundAtom element to RuleMap
        ArrayList<Integer> atomHashList = new ArrayList<Integer>();
        HashSet<GroundAtom> atomSet = new HashSet<GroundAtom>(groundRule.getAtoms());
        int atomCount = 0;
        for (GroundAtom a : atomSet) {
            atomHashList.add(System.identityHashCode(a));
            Map<String, Object> groundAtomElement = new HashMap<String, Object>();
            groundAtomElement.put("text", a.toString());
            groundAtomElement.put("prediction", a.getValue());
            vizData.groundAtoms.put(Integer.toString(System.identityHashCode(a)), groundAtomElement);
            atomCount++;
        }

        // Adds a rule element to RuleMap
        JSONObject rulesElement = new JSONObject();
        String ruleStringID = Integer.toString(System.identityHashCode(parentRule));
        Map<String, Object> rulesElementItem = new HashMap<String, Object>();
        rulesElementItem.put("text", parentRule.getName());
        vizData.rules.put(ruleStringID, rulesElementItem);

        // Adds a groundRule element to RuleMap
        Map<String, String> varConstMap = new HashMap<String, String>();
        for (Map.Entry<Variable, Integer> entry : variableMap.entrySet()) {
            varConstMap.put(entry.getKey().toString(), constantsList[entry.getValue()].rawToString());
        }
        Map<String, Object> groundRulesElement = new HashMap<String, Object>();
        if (groundRule instanceof WeightedGroundRule) {
              WeightedGroundRule weightedGroundRule = (WeightedGroundRule) groundRule;
              groundRulesElement.put("dissatisfaction", weightedGroundRule.getIncompatibility());
        }
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
