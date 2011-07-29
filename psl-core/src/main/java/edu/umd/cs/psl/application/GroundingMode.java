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
package edu.umd.cs.psl.application;

public enum GroundingMode { 
	
	Backward {
		@Override
		public boolean isForward() {
			return false;
		}

		@Override
		public boolean isInitial() {
			return false;
		}
	}, 
	
	Forward {
		@Override
		public boolean isForward() {
			return true;
		}

		@Override
		public boolean isInitial() {
			return false;
		}
	}, 
	ForwardInitial {
		@Override
		public boolean isForward() {
			return true;
		}

		@Override
		public boolean isInitial() {
			return true;
		}
	};
	
	public abstract boolean isForward();
	public boolean isBackward() {
		return !isForward();
	}
	public abstract boolean isInitial();
	
	public static final GroundingMode defaultGroundingMode = GroundingMode.Forward;
	
}
