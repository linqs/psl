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
    private JSONArray predictionTruthArray;
    private JSONArray violatedGroundRulesArray;

    JSONObject rules;
    JSONObject groundRules;
    JSONObject groundAtoms;

    public static ArrayList<GroundRule> violatedGroundRulesList = new ArrayList<>();

    static {
        init();
    }

    // Static only.
    private VizDataCollection() {
        fullJSON = new JSONObject();
        predictionTruthArray = new JSONArray();
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

    //We want to make:
    // A jsonObject filled with JSONArrays
    // Will be organized into different JSON arrays that refer to specific modules, all in one object
    //e.x.
        // [
        //     {predicate: Friends((bob,george), prediction: 0.00003, truth: 1}
        //     {predicate: Friends((alice,george), prediction: 0.00003, truth: 1}
        //     etc...
        // ]
    public static void outputJSON() {
        vizData.fullJSON.put("PredictionTruth", vizData.predictionTruthArray);
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

    //Takes in a prediction truth pair and adds it to our map
    public static void addTruth(GroundAtom target, float predictVal, float truthVal ) {
        String groundAtomID = Integer.toString(System.identityHashCode(target));
        JSONObject moduleElement = new JSONObject();
        moduleElement.put(groundAtomID, truthVal);
        vizData.predictionTruthArray.put(moduleElement);


        // System.out.println(vizData.groundAtoms);
        if (vizData.groundAtoms.isNull(groundAtomID)) {
          // System.out.println("ID : " + groundAtomID + " not in json");
          // System.out.println(target.toString());
          // JSONObject groundAtomObj = new JSONObject();
          // groundAtomObj.put("string", target.toString());
          // groundAtomObj.put("prediction", predictVal);
          // vizData.groundAtoms.put(groundAtomID, groundAtomObj);
          return;
        }
        JSONObject groundAtomElement = vizData.groundAtoms.getJSONObject(groundAtomID);
        groundAtomElement.put("prediction", predictVal);
        // System.out.println("ID : " + groundAtomID + " in json");
        // System.out.println(vizData.groundAtoms.getJSONObject(groundAtomID));

    }

     public static void groundingsPerRule(List<Rule> rules, GroundRuleStore groundRuleStore) {
        for (Rule rule: rules)
        {
            String stringRuleId = Integer.toString(System.identityHashCode(rule));
            int groundRuleCount = groundRuleStore.count( rule );
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

    // public static void violatedGroundRules(List<Rule> rules, GroundRuleStore groundRuleStore) {
    //     for (Rule rule : rules) {
    //         JSONObject moduleElement = new JSONObject();
    //         double violation = 0.0;
    //         boolean weightFlag = false;
    //         Iterable<GroundRule> groundedRuleList = groundRuleStore.getGroundRules(rule);
    //         for (GroundRule groundRule : groundedRuleList) {
    //             if (vizData.violatedGroundRulesList.contains(groundRule)) {
    //                 //There can't be weighted violated rules so we can make an assumption here
    //                 UnweightedGroundRule unweightedGroundRule = (UnweightedGroundRule)groundRule;
    //                 violation = unweightedGroundRule.getInfeasibility();
    //                 moduleElement.put("Violated Rule", groundRule.baseToString());
    //                 moduleElement.put("Parent Rule", rule.getName());
    //                 // moduleElement.put("Weighted", weightFlag);
    //                 moduleElement.put("Violation", violation);
    //                 vizData.violatedGroundRulesArray.put(moduleElement);
    //             }
    //         }
    //     }
    // }

    public static void ruleMapInsertElement(AbstractLogicalRule parentRule, GroundRule groundRule,
                            Map<Variable, Integer> variableMap,  Constant[] constantsList) {
        //Adds a groundAtom element to RuleMap
        //Why are some groundRules null? Perhaps a thread thing??
        ArrayList<Integer> atomHashList = new ArrayList<>();
        if (groundRule != null) {
            HashSet<Atom> atomSet = new HashSet<>(groundRule.getAtoms());
            int atomCount = 0;
            HashMap<String,String> atomMap = new HashMap<>();
            for (Atom a : atomSet) {
                atomHashList.add(System.identityHashCode(a));

                JSONObject groundAtomElement = new JSONObject();
                groundAtomElement.put("string", a.toString());
                vizData.groundAtoms.put(Integer.toString(System.identityHashCode(a)),groundAtomElement);
                atomCount++;
            }
        }

        //Adds a rule element to RuleMap
        JSONObject rulesElement = new JSONObject();
        String ruleStringID = Integer.toString(System.identityHashCode(parentRule));
        JSONObject rulesElementItem = new JSONObject();
        rulesElementItem.put("text", parentRule);
        vizData.rules.put(ruleStringID, rulesElementItem);

        //Adds a groundRule element to RuleMap
        HashMap<String, String> varConstMap = new HashMap<>();
        for (Map.Entry<Variable, Integer> entry : variableMap.entrySet()) {
            varConstMap.put(entry.getKey().toString(), constantsList[entry.getValue()].rawToString());
        }
        JSONObject groundRulesElement = new JSONObject();
        if (groundRule instanceof WeightedGroundRule) {
              WeightedGroundRule weightedGroundRule = (WeightedGroundRule) groundRule;
              groundRulesElement.put("disatisfaction", weightedGroundRule.getIncompatibility());
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
        //We dont get any null groundRules here???
        String groundRuleStringID = Integer.toString(System.identityHashCode(groundRule));
        vizData.groundRules.put(groundRuleStringID, groundRulesElement);
    }

    // public static void debugOutput() {
    //
    // }
    //
    // //These two may want to use as helper functions
    // // e.x. this is where we turn the rules into non dnf form
    // public static void singleRuleHandler() {
    //
    // }
    //
    // public static void singleAtomHandler() {
    //
    // }
}
