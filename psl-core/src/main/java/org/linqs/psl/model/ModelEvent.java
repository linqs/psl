/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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

import org.linqs.psl.model.rule.Rule;

/**
 * An event related to a {@link Model}.
 */
public class ModelEvent {
	
	/** Types of ModelEvents */
	public enum Type {
		/** A Rule was added to a model */
		RuleAdded,
		/** A Rule was removed from a model */
		RuleRemoved,
		/** A Rule's parameters were modified */
		RuleParametersModified;
	}
	
	/** A listener for ModelEvents. */
	public interface Listener {
		/**
		 * Notifies this object of a ModelEvent.
		 * 
		 * @param event  event information
		 */
		public void notifyModelEvent(ModelEvent event);
	}
	
	private final Type type;
	private final Model model;
	private final Rule rule;
	
	/**
	 * Constructs a new ModelEvent with associated properties.
	 * 
	 * @param type  the Type of the new event
	 * @param model  the Model for which the event occurred
	 * @param rule  the Rule related to the event
	 */
	public ModelEvent(Type type, Model model, Rule rule) {
		this.type = type;
		this.model = model;
		this.rule = rule;
	}
	
	/**
	 * @return the associated ModelEvent.Type
	 */
	public Type getType() {
		return type;
	}

	/**
	 * @return the associated Model
	 */
	public Model getModel() {
		return model;
	}

	/**
	 * @return the associated Rule, or null if no Rule is associated
	 */
	public Rule getRule() {
		return rule;
	}
	
}
