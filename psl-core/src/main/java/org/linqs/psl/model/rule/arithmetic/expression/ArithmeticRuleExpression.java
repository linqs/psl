/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.linqs.psl.model.rule.arithmetic.expression;

import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Cardinality;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.util.HashCode;
import org.linqs.psl.util.MathUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Container for components of an arithmetic rule formula.
 *
 * Full equality checks (when two expressions are the equal, but not the same refernce) are epensive.
 */
public class ArithmeticRuleExpression implements Serializable {
    protected final List<Coefficient> coefficients;
    protected final List<SummationAtomOrAtom> atoms;
    protected final FunctionComparator comparator;
    protected final Coefficient constant;
    protected final Set<Variable> vars;
    protected final Map<SummationVariable, SummationAtom> summationMapping;
    private int hash;

    public ArithmeticRuleExpression(List<Coefficient> coefficients, List<SummationAtomOrAtom> atoms,
            FunctionComparator comparator, Coefficient constant) {
        this(coefficients, atoms, comparator, constant, false);
    }

    // Only skip cardinality validation if you know what you are doing and already validated the input.
    public ArithmeticRuleExpression(List<Coefficient> coefficients, List<SummationAtomOrAtom> atoms,
            FunctionComparator comparator, Coefficient constant,
            boolean skipCardinalityValidation) {
        this.coefficients = Collections.unmodifiableList(coefficients);
        this.atoms = Collections.unmodifiableList(atoms);
        this.comparator = comparator;
        this.constant = constant;

        Set<Variable> vars = new HashSet<Variable>();
        Set<String> sumVarNames = new HashSet<String>();
        Map<SummationVariable, SummationAtom> summationMapping = new HashMap<SummationVariable, SummationAtom>();

        if (atoms.size() == 0) {
            throw new IllegalArgumentException("Cannot have an arithmetic rule without atoms.");
        }

        for (SummationAtomOrAtom atom : getAtoms()) {
            if (atom instanceof SummationAtom) {
                for (SummationVariableOrTerm argument : ((SummationAtom)atom).getArguments()) {
                    if (argument instanceof Variable) {
                        vars.add((Variable) argument);
                    } else if (argument instanceof SummationVariable) {
                        if (summationMapping.containsKey((SummationVariable) argument)) {
                            throw new IllegalArgumentException(
                                    "Each summation variable in an ArithmeticRuleExpression must be unique.");
                        }

                        sumVarNames.add(((SummationVariable)argument).getVariable().getName());
                        summationMapping.put((SummationVariable)argument, (SummationAtom)atom);
                    }
                }
            } else {
                for (Term term : ((Atom)atom).getArguments()) {
                    if (term instanceof Variable) {
                        vars.add((Variable) term);
                    }
                }
            }
        }

        // Check for summation variables used as terms.
        for (Variable var : vars) {
            if (sumVarNames.contains(var.getName())) {
                throw new IllegalArgumentException(String.format(
                        "Summation variable (+%s) cannot be used as a normal variable (%s).",
                        var.getName(), var.getName()));
            }
        }

        // Check for cardinality being used on non-summation variables.
        if (!skipCardinalityValidation) {
            for (Coefficient coefficient : coefficients) {
                if (coefficient instanceof Cardinality) {
                    String name = ((Cardinality)coefficient).getSummationVariable().getVariable().getName();
                    if (!sumVarNames.contains(name)) {
                        throw new IllegalArgumentException(String.format(
                                "Cannot use variable (%s) in cardinality. " +
                                "Only summation variables can be used in cardinality.",
                                name));
                    }
                }
            }
        }

        this.vars = Collections.unmodifiableSet(vars);
        this.summationMapping = Collections.unmodifiableMap(summationMapping);

        hash = HashCode.build(HashCode.build(comparator), constant);

        for (Coefficient coefficient : coefficients) {
            hash = HashCode.build(hash, coefficient);
        }

        for (SummationAtomOrAtom atom : atoms) {
            hash = HashCode.build(hash, atom);
        }
    }

    @Override
    public int hashCode() {
        return hash;
    }

    public List<Coefficient> getAtomCoefficients() {
        return coefficients;
    }

    public List<SummationAtomOrAtom> getAtoms() {
        return atoms;
    }

    public FunctionComparator getComparator() {
        return comparator;
    }

    public Coefficient getFinalCoefficient() {
        return constant;
    }

    /**
     * Get the non-summation variables.
     */
    public Set<Variable> getVariables() {
        return vars;
    }

    public Set<SummationVariable> getSummationVariables() {
        return summationMapping.keySet();
    }

    public Map<SummationVariable, SummationAtom> getSummationMapping() {
        return summationMapping;
    }

    /**
     * Returns true if this expression looks like a functional constraint.
     * A functional constraint takes a form like: Foo(A, +B) = 1.0
     *
     * The following properties will be checked:
     *  - Expression uses equals.
     *  - Expression has only a single summation atom with 1.0 coefficient.
     *  - Expression's RHS (final constant) is 1.0.
     */
    public boolean looksLikeFunctionalConstraint() {
        return FunctionComparator.EQ.equals(comparator)
                && atoms.size() == 1
                && atoms.get(0) instanceof SummationAtom
                && coefficients.size() == 1
                && coefficients.get(0) instanceof ConstantNumber
                && MathUtils.equals(1.0f, coefficients.get(0).getValue(null))
                && constant instanceof ConstantNumber
                && MathUtils.equals(1.0f, constant.getValue(null));
    }

    /**
     * Return true if this expression has all the traits of a negative prior.
     */
    public boolean looksLikeNegativePrior() {
        return summationMapping.size() == 0
                && atoms.size() == 1
                && FunctionComparator.EQ.equals(comparator)
                && constant instanceof ConstantNumber
                && MathUtils.isZero(constant.getValue(null));
    }

    /**
     * Get a formula that can be used in a DatabaseQuery to fetch all the possibilites.
     */
    public Formula getQueryFormula() {
        List<Atom> queryAtoms = new ArrayList<Atom>();

        // First collect all the atoms as query atoms.
        for (SummationAtomOrAtom atom : atoms) {
            if (atom instanceof SummationAtom) {
                queryAtoms.add(((SummationAtom)atom).getQueryAtom());
            } else {
                queryAtoms.add((Atom)atom);
            }
        }

        if (queryAtoms.size() == 1) {
            return queryAtoms.get(0);
        }

        return new Conjunction(queryAtoms.toArray(new Formula[0]));
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();

        // If there are coefficients, print each one.
        if (coefficients.size() > 0) {
            for (int i = 0; i < coefficients.size(); i++) {
                if (i != 0) {
                    s.append(" + ");
                }

                s.append(coefficients.get(i));
                s.append(" * ");
                s.append(atoms.get(i));
            }
        } else {
            // Otherwise, just put in a zero.
            s.append("0.0");
        }

        s.append(" ");
        s.append(comparator);
        s.append(" ");
        s.append(constant);
        return s.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        ArithmeticRuleExpression otherExpression = (ArithmeticRuleExpression)other;

        if (this.hash != otherExpression.hash) {
            return false;
        }

        if (this.comparator != otherExpression.comparator) {
            return false;
        }

        if (this.atoms.size() != otherExpression.atoms.size()) {
            return false;
        }

        if (!this.constant.equals(otherExpression.constant)) {
            return false;
        }

        for (int thisIndex = 0; thisIndex < this.atoms.size(); thisIndex++) {
            int otherIndex = otherExpression.atoms.indexOf(this.atoms.get(thisIndex));
            if (otherIndex == -1) {
                return false;
            }

            if (!this.atoms.get(thisIndex).equals(otherExpression.atoms.get(otherIndex))
                    || !this.coefficients.get(thisIndex).equals(otherExpression.coefficients.get(otherIndex))) {
                return false;
            }
        }

        return true;
    }
}
