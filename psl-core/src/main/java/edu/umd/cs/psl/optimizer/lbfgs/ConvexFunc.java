/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
package edu.umd.cs.psl.optimizer.lbfgs;

/**
 * User: Stanley Kok
 * Date: 12/20/10
 * Time: 7:08 PM
 */
public interface ConvexFunc
{
  /**
   * Returns the value and gradients with respect to each parameter.
   * @param g    array storing returned gradients
   * @param wts  array storing weights (parameters of function)
   * @return value of function
   */
  public double getValueAndGradient(double[] g, final double[] wts);
}

