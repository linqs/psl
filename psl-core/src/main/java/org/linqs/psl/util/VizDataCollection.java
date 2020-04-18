package org.linqs.psl.util;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;

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
    // A jsonArray filled with jsonObjects
    // each object represents a predicate, prediction val, and truth value
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
            // System.out.println("ShutdownHook running");
            outputJSON();
        }
    }

    //Tests:
    //predictionTruth -> ContinuousEvaluatorTest, GaussianProcessPriorTest, InitialWeightHyperbandTest (not called from cli)

    //All the methods can be void as we will just be outputting to JSON

    //Takes in a prediction truth pair and adds it to our map
    public static void predictionTruth(GroundAtom target, float predictVal, float truthVal ) {
        JSONObject valueObj = new JSONObject();
        valueObj.put("Truth", truthVal);
        valueObj.put("Prediction", predictVal);
        valueObj.put("Predicate", target.toString());
        vizData.predictionTruthArray.put(valueObj);
    }

     public static void groundingsPerRule(List<Rule> rules, GroundRuleStore groundRuleStore) {
        HashMap<String, Integer> groundRuleCountPerRule = new HashMap();
        for (Rule rule: rules)
        {
            int groundRuleCount = groundRuleStore.count( rule );
            groundRuleCountPerRule.put(rule.getName(), groundRuleCount);
        }

        for (Map.Entry<String, Integer> entry: groundRuleCountPerRule.entrySet())
        {
            JSONObject valueObj = new JSONObject();
            valueObj.put("Rule", entry.getKey());
            valueObj.put("Count", entry.getValue());
            vizData.ruleCountArray.put(valueObj);
        }
     }

    public static void totalRuleSatDis(List<Rule> rules, GroundRuleStore groundRuleStore) {
        for (Rule rule : rules) {
            Iterable<GroundRule> groundedRuleList = groundRuleStore.getGroundRules(rule);
            double totalSat = 0.0;
            double totalDis = 0.0;
            int groundRuleCount = 0;
            JSONObject valueObj = new JSONObject();

            for (GroundRule groundRule : groundedRuleList) {
                if (groundRule instanceof WeightedGroundRule) {
                    WeightedGroundRule weightedGroundRule = (WeightedGroundRule)groundRule;
                    totalSat += 1.0 - weightedGroundRule.getIncompatibility();
                    totalDis += weightedGroundRule.getIncompatibility();
                }
                groundRuleCount++;
            }
            valueObj.put("Rule", rule.getName());
            valueObj.put("Total Satisfaction", totalSat);
            valueObj.put("Satisfaction Percentage", totalSat / groundRuleCount);
            valueObj.put("Total Dissatisfaction", totalDis);
            valueObj.put("Dissatisfaction Percentage", totalDis / groundRuleCount);
            vizData.totRuleSatArray.put(valueObj);
        }
    }

    //To test this
    // mvn -Dtest=SimpleAcquaintancesTest#testBase test -DfailIfNoTests=false -Dadmmreasoner.maxiterations=1
    public static void violatedGroundRules(List<Rule> rules, GroundRuleStore groundRuleStore) {
        // System.out.println(vizData.violatedGroundRulesList.size());
        for (Rule rule : rules) {
            JSONObject valueObj = new JSONObject();
            double violation = 0.0;
            boolean weightFlag = false;
            Iterable<GroundRule> groundedRuleList = groundRuleStore.getGroundRules(rule);
            for (GroundRule groundRule : groundedRuleList) {
                if (vizData.violatedGroundRulesList.contains(groundRule)) {
                    if (groundRule instanceof WeightedGroundRule) {
                        WeightedGroundRule weightedGroundRule = (WeightedGroundRule)groundRule;
                        violation = weightedGroundRule.getIncompatibility();
                        weightFlag = true;
                    }
                    else {
                        UnweightedGroundRule unweightedGroundRule = (UnweightedGroundRule)groundRule;
                        violation = unweightedGroundRule.getInfeasibility();
                    }
                    valueObj.put("Violated Rule", groundRule.baseToString());
                    valueObj.put("Parent Rule", rule.getName());
                    valueObj.put("Weighted", weightFlag);
                    valueObj.put("Violation", violation);
                    vizData.violatedGroundRulesArray.put(valueObj);
                }
            }
        }
    }

    // public static void individualRuleSatDis(List<Rule> rules, GroundRuleStore groundRuleStore) {
        // for (GroundRule groundRule : groundRuleStore.getGroundRules()) {
        //     String row = "";
        //     double satisfaction = 0.0;
        //     JSONObject valueObj = new JSONObject();
        //
        //     valueObj.put("Rule", groundRule.baseToString());
        //
        //     if (groundRule instanceof WeightedGroundRule) {
        //         WeightedGroundRule weightedGroundRule = (WeightedGroundRule)groundRule;
        //         valueObj.put("Satisfaction", 1.0 - weightedGroundRule.getIncompatibility());
        //     } else {
        //         UnweightedGroundRule unweightedGroundRule = (UnweightedGroundRule)groundRule;
        //         valueObj.put("Satisfaction", 1.0 - unweightedGroundRule.getInfeasibility());
        //     }
        //     vizData.totRuleSatArray.put(valueObj);
        // }
    // }

    //
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
