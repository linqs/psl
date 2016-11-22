/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package org.linqs.psl.application.learning.weight.maxlikelihood;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimplexSampler {

	Logger log = LoggerFactory.getLogger(SimplexSampler.class);

	private final Random rand;

	public SimplexSampler(int seed) {
		rand = new Random(seed);
	}

	public SimplexSampler() {
		rand = new Random();
	}


	/**
	 * Samples a uniform point on the simplex of dimension d
	 * @param d
	 * @return
	 */
	public double [] getNext(int d) {
		double[] x = new double[d];

		double sum = 0.0;

		for (int i = 0; i < d; i++) {
			x[i] = -Math.log(rand.nextDouble());
			sum += x[i];
		}

		if (sum == Double.POSITIVE_INFINITY) {
			sum = 0.0;
			for (int i = 0; i < d; i++) 
				if (x[i] == Double.POSITIVE_INFINITY) {
					x[i] = 1.0;
					sum += 1.0;
				} else
					x[i] = 0.0;
			for (int i = 0; i < d; i++) {
				x[i] /= sum;
			}
		} else {
			for (int i = 0; i < d; i++)
				x[i] /= sum;
		}

		return x;
	}


	public static void main(String [] args) {
		SimplexSampler sampler = new SimplexSampler();

		for (int d = 1; d <=3; d++) {
			for (int i = 0; i < 1000; i++) {
				double [] x = sampler.getNext(d);
				for (int j = 0; j < d; j++) {
					System.out.print("" + x[j] + "\t");
				}
				System.out.println();
			}
		}
	}	

}
