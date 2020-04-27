package org.linqs.psl.util;

import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class VizDataCollection {

    private static VizDataCollection vizData = null;
    private static Runtime runtime = null;

    private JSONObject fullJSON;
    private JSONArray predictionTruthArray;
    private JSONArray ruleCountArray;
    private JSONArray totRuleSatArray;
    private JSONArray violatedGroundRulesArray;

    public static ArrayList<GroundRule> violatedGroundRulesList = new ArrayList<>();

    static {
        init();
    }

    // Static only.
    private VizDataCollection() {
        fullJSON = new JSONObject();
        predictionTruthArray = new JSONArray();
        ruleCountArray = new JSONArray();
        totRuleSatArray = new JSONArray();
        violatedGroundRulesArray = new JSONArray();
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
        vizData.fullJSON.put("RuleCount", vizData.ruleCountArray);
        vizData.fullJSON.put("SatDis", vizData.totRuleSatArray);
        vizData.fullJSON.put("ViolatedGroundRules", vizData.violatedGroundRulesArray);

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
    public static void predictionTruth(GroundAtom target, float predictVal, float truthVal ) {
        JSONObject moduleElement = new JSONObject();
        moduleElement.put("Truth", truthVal);
        moduleElement.put("Prediction", predictVal);
        moduleElement.put("Predicate", target.toString());
        vizData.predictionTruthArray.put(moduleElement);
    }

     public static void groundingsPerRule(List<Rule> rules, GroundRuleStore groundRuleStore) {
        HashMap<String, Integer> groundRuleCountPerRule = new HashMap<>();
        for (Rule rule: rules)
        {
            int groundRuleCount = groundRuleStore.count( rule );
            groundRuleCountPerRule.put(rule.getName(), groundRuleCount);
        }

        for (Map.Entry<String, Integer> entry: groundRuleCountPerRule.entrySet())
        {
            JSONObject moduleElement = new JSONObject();
            moduleElement.put("Rule", entry.getKey());
            moduleElement.put("Count", entry.getValue());
            vizData.ruleCountArray.put(moduleElement);
        }
     }

    public static void totalRuleSatDis(List<Rule> rules, GroundRuleStore groundRuleStore) {
        for (Rule rule : rules) {
            Iterable<GroundRule> groundedRuleList = groundRuleStore.getGroundRules(rule);
            double totalSat = 0.0;
            double totalDis = 0.0;
            int groundRuleCount = 0;
            JSONObject moduleElement = new JSONObject();

            for (GroundRule groundRule : groundedRuleList) {
                if (groundRule instanceof WeightedGroundRule) {
                    WeightedGroundRule weightedGroundRule = (WeightedGroundRule)groundRule;
                    totalSat += 1.0 - weightedGroundRule.getIncompatibility();
                    totalDis += weightedGroundRule.getIncompatibility();
                }
                groundRuleCount++;
            }
            moduleElement.put("Rule", rule.getName());
            moduleElement.put("Total Satisfaction", totalSat);
            moduleElement.put("Satisfaction Percentage", totalSat / groundRuleCount);
            moduleElement.put("Total Dissatisfaction", totalDis);
            moduleElement.put("Dissatisfaction Percentage", totalDis / groundRuleCount);
            vizData.totRuleSatArray.put(moduleElement);
        }
    }

    public static void violatedGroundRules(List<Rule> rules, GroundRuleStore groundRuleStore) {
        for (Rule rule : rules) {
            JSONObject moduleElement = new JSONObject();
            double violation = 0.0;
            boolean weightFlag = false;
            Iterable<GroundRule> groundedRuleList = groundRuleStore.getGroundRules(rule);
            for (GroundRule groundRule : groundedRuleList) {
                if (vizData.violatedGroundRulesList.contains(groundRule)) {
                    //There can't be weighted violated rules so we can make an assumption here
                    UnweightedGroundRule unweightedGroundRule = (UnweightedGroundRule)groundRule;
                    violation = unweightedGroundRule.getInfeasibility();
                    moduleElement.put("Violated Rule", groundRule.baseToString());
                    moduleElement.put("Parent Rule", rule.getName());
                    // moduleElement.put("Weighted", weightFlag);
                    moduleElement.put("Violation", violation);
                    vizData.violatedGroundRulesArray.put(moduleElement);
                }
            }
        }
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
