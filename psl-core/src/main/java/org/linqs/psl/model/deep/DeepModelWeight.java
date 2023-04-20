package org.linqs.psl.model.deep;

import org.linqs.psl.config.Options;
import org.linqs.psl.database.AtomStore;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.Logger;

import java.util.HashMap;
import java.util.Map;

public class DeepModelWeight extends DeepModel {
    private static final Logger log = Logger.getLogger(DeepModelWeight.class);

    protected static final String CONFIG_RULES = "rules";

    private AtomStore atomStore;
    private TermStore termStore;
    private float[] symbolicGradients;

    private String modelPath;
    private Map<String, Integer> ruleIndexMap;

    private boolean initialFit;
    private int maxVariables;
    private int numWeightedTerms;
    private float[] weights;

    public DeepModelWeight() {
        super();

        this.deepModel = "DeepModelWeight";

        this.termStore = null;
        this.atomStore = null;
        this.symbolicGradients = null;

        this.modelPath = Options.WLA_GRADIENT_DESCENT_DEEP_WEIGHTS_MODEL_PATH.getString();
        this.ruleIndexMap = new HashMap<String, Integer>();

        this.initialFit = true;
        this.maxVariables = -1;
        this.numWeightedTerms = 0;
        this.weights = null;
    }

    public int init() {
        log.debug("Initializing deep model weights.");

        pythonOptions.put(CONFIG_MODEL_PATH, modelPath);

        StringBuilder rulesString = new StringBuilder();

        int variableSize = 0;
        numWeightedTerms = 0;
        for (Object rawTerm : termStore) {
            if (!(((ReasonerTerm) rawTerm).getRule() instanceof WeightedRule) || ((ReasonerTerm) rawTerm).getRule().toString().contains("AUGMENTED")) {
                continue;
            }

            numWeightedTerms++;
            if (ruleIndexMap.containsKey(((ReasonerTerm) rawTerm).getRule().toString())) {
                continue;
            }

            variableSize = 0;

            ruleIndexMap.put(((ReasonerTerm) rawTerm).getRule().toString(), ruleIndexMap.size());
            rulesString.append(((ReasonerTerm) rawTerm).getRule().toString()).append("\n");

            log.debug("Rule: {}", ((ReasonerTerm) rawTerm).getRule().toString());
            for (int atomIndex : ((ReasonerTerm)rawTerm).getAtomIndexes()) {
                variableSize += ((GroundAtom)atomStore.getAtom(atomIndex)).getArguments().length;
            }
            if (maxVariables < variableSize) {
                maxVariables = variableSize;
            }
        }
        weights = new float[numWeightedTerms];

        pythonOptions.put(CONFIG_RULES, rulesString.toString());
        pythonOptions.put("max-variables", String.valueOf(maxVariables));
        return Integer.SIZE + numWeightedTerms * (maxVariables + 1) * Long.SIZE + numWeightedTerms * Float.SIZE;
    }

    public void writeFitData() {
        log.debug("Writing fit data for deep model weights.");
        if (initialFit) {
            writeRuleData();
            initialFit = false;
        } else {
            writeGradientData(symbolicGradients);
        }
    }

    public void writePredictData() {
        log.debug("Writing predict data for deep model weight");
    }

    public void readPredictData() {
        log.debug("Reading predict data for deep model weight");
        int index = 0;
        for (Object rawTerm : termStore) {
            if (!(((ReasonerTerm) rawTerm).getRule() instanceof WeightedRule) || ((ReasonerTerm) rawTerm).getRule().toString().contains("AUGMENTED")) {
                continue;
            }
            weights[index] = sharedBuffer.getFloat();
            ((ReasonerTerm)rawTerm).setWeight(weights[index]);
            index++;
        }
    }

    public void writeEvalData() {
        log.debug("Writing eval data for deep model weight");
    }

    public void setTermStore(TermStore termStore) {
        this.termStore = termStore;
    }

    public void setAtomStore(AtomStore atomStore) {
        this.atomStore = atomStore;
    }

    public void setSymbolicGradients(float[] symbolicGradients) {
        this.symbolicGradients = symbolicGradients;
    }

    public float[] getWeights() {
        return weights;
    }

    private void writeGradientData(float[] data) {
        sharedBuffer.putInt(data.length);
        for (int i = 0; i < data.length; i++) {
            sharedBuffer.putFloat(data[i]);
        }
    }

    private void writeRuleData() {
        sharedBuffer.putInt( numWeightedTerms);
        int numVariables = 0;
        int variableCurrent = 0;
        int totalVariables = 0;
        for (Object rawTerm : termStore) {
            if (!(((ReasonerTerm) rawTerm).getRule() instanceof WeightedRule) || ((ReasonerTerm) rawTerm).getRule().toString().contains("AUGMENTED")) {
                continue;
            }

            numVariables = 0;
            sharedBuffer.putInt(ruleIndexMap.get(((ReasonerTerm) rawTerm).getRule().toString()));
            for (int atomIndex : ((ReasonerTerm)rawTerm).getAtomIndexes()) {
                for (Constant constant : ((GroundAtom)atomStore.getAtom(atomIndex)).getArguments()) {
                    variableCurrent = Integer.parseInt(constant.toString().replace("'", ""));
                    sharedBuffer.putInt(variableCurrent);
                    numVariables++;
                }
            }

            for (int i = numVariables; i < maxVariables; i++) {
                sharedBuffer.putInt(-1);
            }
            totalVariables += numVariables;
        }
        log.debug("Total variables: {}", totalVariables);
    }
}
