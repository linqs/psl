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

import java.lang.management.*;
import java.io.PrintStream;

/**
 * @author Stanley Kok
 * Date: 12/19/10
 * Time: 12:13 PM
 * This class returns CPU, user and system time.
 */

public class Timer
{
  public Timer() {}

  /** Returns CPU time in nanoseconds. */
  public long time() { return cpuTime(); }

  /** Returns CPU time in nanoseconds. */
  public long cpuTime()
  {
    ThreadMXBean b = ManagementFactory.getThreadMXBean();
    return b.isCurrentThreadCpuTimeSupported() ? b.getCurrentThreadCpuTime() : 0L;
  }

  /** Returns user time in nanoseconds. */
  public long userTime()
  {
    ThreadMXBean b = ManagementFactory.getThreadMXBean();
    return b.isCurrentThreadCpuTimeSupported() ? b.getCurrentThreadUserTime() : 0L;
  }

  /** Returns system time in nanoseconds. */
  public long sysTime( )
  {
    ThreadMXBean b = ManagementFactory.getThreadMXBean();
    return b.isCurrentThreadCpuTimeSupported() ? (b.getCurrentThreadCpuTime() - b.getCurrentThreadUserTime()) : 0L;
  }

  /** Print time as days, hours, mins, secs. */
  public static void printTime(PrintStream out, long nanoSec)
  {
    long sec = nanoSec/1000000000;
    if (sec < 60)         out.print(sec + " secs");
    else if (sec < 3600)  out.print((long)(sec/60)    + " mins, " + (sec-(long)(sec/60)*60)              +" secs");
    else if (sec < 86400) out.print((long)(sec/3600)  + " hrs, "  + (sec-(long)(sec/3600)*3600)/60.0     +" mins");
    else                  out.print((long)(sec/86400) + " days, " + (sec-(long)(sec/86400)*86400)/3600.0 + " hrs");
  }

  public static void main(String[] args) throws InterruptedException
  {
    Timer timer = new Timer();
    long startTime = timer.cpuTime();
    long ii = 0;
    for (long i = 0; i < 1000000000; i++)  ii++;
    for (long i = 0; i < 1000000000; i++)  ii++;
    for (long i = 0; i < 1000000000; i++)  ii++;

    long endTime = timer.cpuTime();
    System.out.println(ii);
    System.out.println("startTime = " + startTime);
    System.out.println("endTime = " + endTime);
    System.out.println("Time Elapsed = " + (endTime - startTime));
    timer.printTime(System.out, endTime-startTime);
  }
}

