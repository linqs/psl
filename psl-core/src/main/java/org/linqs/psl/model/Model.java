/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.linqs.psl.application.ModelApplication;
import org.linqs.psl.model.rule.Rule;

/**
 * A probabilistic soft logic model.
 * <p>
 * Encapsulates a set of {@link Rule Rules}. A {@link ModelApplication}
 * can be used to combine a Model with data to perform inference or learn.
 * <p>
 * Objects which use a Model should register with it to listen
 * for {@link ModelEvent ModelEvents}.
 */
public class Model {

	protected final List<Rule> rules;
	/** Redundant set for fast membership checks */
	protected final Set<Rule> ruleSet;
	protected final Set<ModelEvent.Listener> modelObservers;
	
	/**
	 * Sole constructor.
	 */
	public Model() {
		rules = new LinkedList<Rule>();
		ruleSet = new HashSet<Rule>();
		modelObservers = new HashSet<ModelEvent.Listener>();
	}
	
	/**
	 * Registers an observer to receive {@link ModelEvent ModelEvents}.
	 * 
	 * @param observer  object to notify of events
	 */
	public void registerModelObserver(ModelEvent.Listener observer) {
		if (!modelObservers.contains(observer)) modelObservers.add(observer);
	}
	
	/**
	 * Unregisters an observer so it will no longer receive {@link ModelEvent ModelEvents}
	 * from this model.
	 * 
	 * @param observer  object to stop notifying
	 */
	public void unregisterModelObserver(ModelEvent.Listener observer) {
		if  (!modelObservers.contains(observer))
			throw new IllegalArgumentException("Object is not a registered observer of this model.");
		modelObservers.remove(observer);
	}
	
	/**
	 * @return the {@link Rule Rules} contained in this model
	 */
	public Iterable<Rule> getRules() {
		return Collections.unmodifiableList(rules);
	}
	
	/**
	 * Adds a Rule to this Model.
	 * <p>
	 * All observers of this Model will receive a {@link ModelEvent#RuleAdded} event.
	 * 
	 * @param r  Rule to add
	 * @throws IllegalArgumentException  if the Rule is already in this Model
	 */
	public void addRule(Rule r) {
		if (ruleSet.contains(r))
			throw new IllegalArgumentException("Rule already added to this model.");
		else {
			rules.add(r);
			ruleSet.add(r);
			broadcastModelEvent(new ModelEvent(ModelEvent.Type.RuleAdded, this, r));
		}
	}
	
	/**
	 * Removes a Rule from this Model.
	 * <p>
	 * All observers of this Model will receive a {@link ModelEvent#RuleRemoved} event.
	 * 
	 * @param r  Rule to remove
	 * @throws IllegalArgumentException  if the Rule is not in this Model
	 */
	public void removeRule(Rule r) {
		if (!ruleSet.contains(r))
			throw new IllegalArgumentException("Rule not in this model.");
		else {
			rules.remove(r);
			ruleSet.remove(r);
			
			broadcastModelEvent(new ModelEvent(ModelEvent.Type.RuleRemoved, this, r));
		}
	}
	
	/**
	 * Notifies this Model that a Rule's parameters were modified.
	 * <p>
	 * All observers of this model will receive a {@link ModelEvent#RuleParametersModified} event.
	 * 
	 * @param r  the Rule that was modified
	 * @throws IllegalArgumentException  if the Rule is not in this Model
	 */
	public void notifyRuleParametersModified(Rule r) {
		if (!ruleSet.contains(r))
			throw new IllegalArgumentException("Rule not in this model.");
		broadcastModelEvent(new ModelEvent(ModelEvent.Type.RuleParametersModified, this, r));
	}
	
	/**
	 * Returns a String representation of this Model.
	 * 
	 * @return the String representation
	 */
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append("Model:\n");
		s.append(asString());
		return s.toString();
	}
	
	public String asString() {
		StringBuilder s = new StringBuilder();
		//s.append("Model:\n");
		if (rules.size() > 0) {
			s.append(rules.get(0));
		}
		for (int i = 1; i < rules.size(); i++) {
			s.append("\n").append(rules.get(i));
		}
		return s.toString();
	}
	
	protected void broadcastModelEvent(ModelEvent event) {
		for (ModelEvent.Listener observer : modelObservers) {
			observer.notifyModelEvent(event);
		}
	}
	
}
