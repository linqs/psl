package org.linqs.psl.util;

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

import java.util.ArrayList;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashSet;

public class VizDataCollection {

    private static VizDataCollection vizData = null;
    private static Runtime runtime = null;

    private JSONObject fullJSON;
    private JSONArray truthMap;
    private JSONArray violatedGroundRulesArray;

    JSONObject rules;
    JSONObject groundRules;
    JSONObject groundAtoms;

    public static ArrayList<GroundRule> violatedGroundRulesList = new ArrayList<>();

    static {
        init();
    }

    private VizDataCollection() {
        fullJSON = new JSONObject();
        truthMap = new JSONArray();
        violatedGroundRulesArray = new JSONArray();
        rules = new JSONObject();
        groundRules = new JSONObject();
        groundAtoms = new JSONObject();
    }

    private static synchronized void init() {
        if (runtime != null) {
            return;
        }
        vizData = new VizDataCollection();
        runtime = Runtime.getRuntime();
        runtime.addShutdownHook(new ShutdownHook());
    }

    // We want to make:
    // A jsonObject filled with JSONArrays
    // Will be organized into different JSON arrays that refer to specific modules, all in one object
    //e.x.
        // [
        //     {predicate: Friends((bob,george), prediction: 0.00003, truth: 1}
        //     {predicate: Friends((alice,george), prediction: 0.00003, truth: 1}
        //     etc...
        // ]
    public static void outputJSON() {
        vizData.fullJSON.put("truthMap", vizData.truthMap);
        vizData.fullJSON.put("ViolatedGroundRules", vizData.violatedGroundRulesArray);
        vizData.fullJSON.put("rules", vizData.rules);
        vizData.fullJSON.put("groundRules", vizData.groundRules);
        vizData.fullJSON.put("groundAtoms", vizData.groundAtoms);

        try (FileWriter file = new FileWriter("PSLVizData.json")) {
            file.write(vizData.fullJSON.toString(4));
            file.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ShutdownHook extends Thread {
        @Override
        public void run() {
            outputJSON();
        }
    }

    // Takes in a prediction truth pair and adds it to our map
    public static void addTruth(GroundAtom target, float truthVal ) {
        String groundAtomID = Integer.toString(System.identityHashCode(target));
        JSONObject moduleElement = new JSONObject();
        moduleElement.put(groundAtomID, truthVal);
        vizData.truthMap.put(moduleElement);
    }

     public static void groundingsPerRule(List<Rule> rules, GroundRuleStore groundRuleStore) {
        for (Rule rule: rules)
        {
            String stringRuleId = Integer.toString(System.identityHashCode(rule));
            int groundRuleCount = groundRuleStore.count( rule );
            // Abstract Arithmetic Rules are not currently being added to the data collection
            if ( vizData.rules.isNull(stringRuleId) ) {
                JSONObject newRuleElementItem = new JSONObject();
                newRuleElementItem.put("string", rule.getName());
                vizData.rules.put(stringRuleId, newRuleElementItem);
            }
            JSONObject ruleElement = vizData.rules.getJSONObject(stringRuleId);
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
        HashSet<GroundAtom> atomSet = new HashSet<>(groundRule.getAtoms());
        int atomCount = 0;
        HashMap<String,String> atomMap = new HashMap<>();
        for (GroundAtom a : atomSet) {
            atomHashList.add(System.identityHashCode(a));
            JSONObject groundAtomElement = new JSONObject();
            groundAtomElement.put("string", a.toString());
            groundAtomElement.put("prediction", a.getValue());
            vizData.groundAtoms.put(Integer.toString(System.identityHashCode(a)), groundAtomElement);
            atomCount++;
        }

        // Adds a rule element to RuleMap
        JSONObject rulesElement = new JSONObject();
        String ruleStringID = Integer.toString(System.identityHashCode(parentRule));
        JSONObject rulesElementItem = new JSONObject();
        rulesElementItem.put("text", parentRule);
        vizData.rules.put(ruleStringID, rulesElementItem);

        // Adds a groundRule element to RuleMap
        HashMap<String, String> varConstMap = new HashMap<>();
        for (Map.Entry<Variable, Integer> entry : variableMap.entrySet()) {
            varConstMap.put(entry.getKey().toString(), constantsList[entry.getValue()].rawToString());
        }
        JSONObject groundRulesElement = new JSONObject();
        if (groundRule instanceof WeightedGroundRule) {
              WeightedGroundRule weightedGroundRule = (WeightedGroundRule) groundRule;
              groundRulesElement.put("disatisfaction", weightedGroundRule.getIncompatibility());
        } else {
            UnweightedGroundRule unweightedGroundRule = (UnweightedGroundRule) groundRule;
            groundRulesElement.put("disatisfaction", unweightedGroundRule.getInfeasibility());
        }
        groundRulesElement.put("ruleID", Integer.parseInt(ruleStringID));
        JSONObject constants = new JSONObject();
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
