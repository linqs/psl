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
package edu.umd.cs.psl.optimizer.conic.program.graph.cosi;

import edu.umd.cs.psl.optimizer.conic.program.graph.Property;

public class COSIProperty extends COSIEdge implements Property {
	
	COSIProperty(COSIGraph g, edu.umd.umiacs.dogma.diskgraph.core.Property p) {
		super(g, p);
	}

	@Override
	public Object getAttribute() {
		return ((edu.umd.umiacs.dogma.diskgraph.core.Property) node).getAttribute();
	}

	@Override
	public <O> O getAttribute(Class<O> clazz) {
		return clazz.cast(getAttribute());
	}

	@Override
	public String getPropertyType() {
		return ((edu.umd.umiacs.dogma.diskgraph.core.Property) node).getPropertyType().getName();
	}

	@Override
	public void delete() {
		graph.notifyPropertyDeleted(COSINode.wrap(graph, ((edu.umd.umiacs.dogma.diskgraph.core.Property) node).getStart()), this);
		super.delete();
	}
}
