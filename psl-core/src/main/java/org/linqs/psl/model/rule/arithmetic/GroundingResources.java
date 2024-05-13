package org.linqs.psl.model.rule.arithmetic;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Weight;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.model.term.Constant;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resources that every arithmetic rule grounding thread will use and reuse.
 */
public class GroundingResources {
    // Because multiple ground rules can be generated from a single rule,
    // we need a place to hold onto ground rules until we pass them back.
    public List<GroundRule> groundRules;

    // Atoms that cause trouble for the atom manager.
    public Set<GroundAtom> accessExceptionAtoms;

    // Shared resources.

    public List<QueryAtom> queryAtoms;
    public GroundAtom[] groundAtoms;
    public Constant[][] argumentBuffer;
    public float[] coefficients;
    public float finalCoefficient;

    // Resources for deep weights.
    public QueryAtom weightQueryAtom;
    public GroundAtom weightGroundAtom;
    public Constant[] weightArgumentsBuffer;

    // More resources necessary for summations.

    public boolean summationDataLoaded;

    // The context expression with all summation variables expanded.
    public ArithmeticRuleExpression flatExpression;

    // The maximum counts of all summation variable replacements.
    public Map<SummationVariable, Integer> totalSummationCounts;

    // A buffer for counting actual replacements.
    // If we filter out an atom, we can mark it here.
    // This will allow us to make accurate coefficient computations.
    public Map<SummationVariable, Integer> summationCounts;

    // A marker for every variables that shows which are summation variables.
    public List<SummationVariable[]> flatSummationVariables;

    // True for each summation atom.
    boolean[] flatSummationAtoms;

    public GroundingResources() {
        groundRules = new ArrayList<GroundRule>();
        accessExceptionAtoms = new HashSet<GroundAtom>(4);

        queryAtoms = null;
        groundAtoms = null;
        argumentBuffer = null;
        coefficients = null;
        finalCoefficient = 0.0f;

        weightQueryAtom = null;
        weightGroundAtom = null;
        weightArgumentsBuffer = null;

        summationDataLoaded = false;
        flatExpression = null;
        totalSummationCounts = null;
        summationCounts = null;
        flatSummationVariables = null;
        flatSummationAtoms = null;
    }

    public void parseExpression(ArithmeticRuleExpression expression, boolean computeCoefficients) {
        parseExpression(expression, computeCoefficients, null);
    }

    public void parseExpression(ArithmeticRuleExpression expression, boolean computeCoefficients, Weight weight) {
        queryAtoms = new ArrayList<QueryAtom>();

        for (SummationAtomOrAtom atom : expression.getAtoms()) {
            queryAtoms.add((QueryAtom)atom);
        }

        groundAtoms = new GroundAtom[queryAtoms.size()];

        argumentBuffer = new Constant[queryAtoms.size()][];
        for (int i = 0; i < queryAtoms.size(); i++) {
            argumentBuffer[i] = new Constant[queryAtoms.get(i).getArity()];
        }

        if ((weight != null) && !(weight.isDeep())) {
            assert (weight.getAtom() instanceof QueryAtom);

            weightQueryAtom = (QueryAtom)weight.getAtom();
            weightArgumentsBuffer = new Constant[weightQueryAtom.getArity()];
        }

        coefficients = new float[queryAtoms.size()];
        finalCoefficient = 0.0f;

        if (computeCoefficients) {
            for (int i = 0; i < coefficients.length; i++) {
                coefficients[i] = expression.getAtomCoefficients().get(i).getValue(null);
            }

            finalCoefficient = expression.getFinalCoefficient().getValue(null);
        }
    }
}
