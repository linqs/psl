/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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
package edu.umd.cs.psl.optimizer.conic.program.graph;

import java.util.Collection;

public interface Edge extends Node {
	public int getArity();

	public String getEdgeType();

	public Collection<? extends Node> getEndNodes();

	public Collection<? extends Node> getNodes();

	public Node getStart();

	public Collection<? extends Node> getStartNodes();

	public boolean isBinaryEdge();

	public boolean isIncidentOn(Node n);

	public boolean isProperty();

	public boolean isRelationship();

	public boolean isSelfLoop(Node node);

	public boolean isSimple();

}
