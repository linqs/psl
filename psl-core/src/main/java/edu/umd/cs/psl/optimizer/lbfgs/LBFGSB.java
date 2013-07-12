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
import java.io.*;

/**
 * @author Stanley Kok
 * Date: 12/19/10
 * Time: 5:28 PM
 * The code below is ported from:
 * C. Zhu, R.H. Byrd, P. Lu, J. Nocedal, ``L-BFGS-B: a limited memory FORTRAN code for solving bound constrained
 * optimization problems'', Tech. Report, NAM-11, EECS Department, Northwestern University.
 */

public class LBFGSB
{
  private final int LBFGSB_PRINT = -1; //-1: no output; 1: output for every iteration
  private final int MMAX = 17;
  private final int M    =  5; //max number of limited memory corrections

  ConvexFunc func_;
  int      maxIter_;
  double   ftol_;
  int      numWts_;
  int[]    nbd_ = null;
  double[] l_   = null;
  double[] u_   = null;
  double[] g_   = null;

  String[]  task_ = new String[1];
  String[]  csave_ = new String[1];
  boolean[] lsave_ = new boolean[5];
  int[]     isave_ = new int[21];

  double[] ws_  = null;
  double[] wy_  = null;
  double[] sy_  = null;
  double[] ss_  = null;
  double[] yy_  = null;
  double[] wt_  = null;
  double[] wn_  = null;
  double[] snd_ = null;
  double[] z_   = null;
  double[] r_   = null;
  double[] d_   = null;
  double[] t_   = null;
  double[] wa0_ = null;
  double[] wa1_ = null;
  double[] wa2_ = null;
  double[] wa3_ = null;
  double[] sg_  = null;
  double[] sgo_ = null;
  double[] yg_  = null;
  double[] ygo_ = null;
  int[]    index_  = null;
  int[]    iwhere_ = null;
  int[]    indx2_  = null;
  int[]    isave2_ = new int[21+1];
  int[]    isave3_ = new int[2+1];
  double[] dsave1_ = new double[17+1];
  double[] dsave2_ = new double[14+1];

  Timer timer_ = new Timer();

  public LBFGSB(final int maxIter, final double ftol, final int numWts, ConvexFunc func)
  {
    maxIter_ = maxIter;
    ftol_    = ftol;
    func_    = func;
    init(numWts);
  }

  private void init(final int numWts)
  {
    numWts_ = numWts;
    nbd_ = new int[numWts_+1];
    l_ = new double[numWts_+1];
    u_ = new double[numWts_+1];
    g_ = new double[numWts_+1];
  }

  public void reInit(final int numWts)
  {
   if (numWts_ == numWts) return;
   init(numWts);
 }

  public void setMaxIter(final int iter) { maxIter_ = iter; }
  public void setFtol(final int tol)     { ftol_ = tol;     }

  public void setLowerBound(final int idx, final double bnd) { l_[idx] = bnd;    }
  public void setUpperBound(final int idx, final double bnd) { u_[idx] = bnd;    }
  public void setBoundSpec(final int idx,  final int spec)   { nbd_[idx] = spec; }

  public double minimize(final int numWts, double[] wts, int[] iter, boolean[] error)
  {
    reInit(numWts);
    return minimize(wts, iter, error);
  }

  public double minimize(double[] wts, int[] iter, boolean[] error)
  {
    error[0] = false;
    double[] f = new double[1];// value of function to be optimized
    double factr = 0;
    double pgtol = 0;
     // -1: silent (-1); 1: out at every iteration

    Writer itfile = null;
    int iprint = LBFGSB_PRINT;
    if (iprint >= 1)
    {
      try { itfile = new BufferedWriter(new FileWriter("iterate.dat")); }
      catch (IOException e) { System.err.println("failed to open iterate.dat"); System.exit(-1); }
    }

    iter[0] = 0;
    //indicate that the elements of x[] are unbounded
    //for (int i = 0; i <= numWts_; i++) nbd_[i] = 0;

    task_[0] = "START";

    setulb(wts,f,factr,pgtol,iprint,itfile);

    double initialValue = 0.0, prevValue = 0.0, newValue;
    boolean firstIter = true;

    //while routine returns "FG" or "NEW_X" in task, keep calling it
    while (matchPrefix(task_[0],"FG") || matchPrefix(task_[0],"NEW_X"))
    {
      if (matchPrefix(task_[0],"FG"))
      {
        f[0] = getValueAndGradient(g_, wts);
        setulb(wts,f,factr,pgtol,iprint,itfile);

        if (firstIter) { firstIter = false; prevValue = f[0]; initialValue = f[0]; }
        ++iter[0];
      }
      else
      {
        //the minimization routine has returned with a new iterate,
        //and we have opted to continue the iteration
        if (iter[0] +1 > maxIter_) break;
        ++iter[0];
        newValue = f[0];

        if (Math.abs(newValue-prevValue) < ftol_*Math.abs(prevValue)) break;
        prevValue = newValue;

        setulb(wts,f,factr,pgtol,iprint,itfile);
      }
    }


    //If task is neither FG nor NEW_X we terminate execution.
    //the minimization routine has returned with one of
    //{CONV, ABNO, ERROR}

    if (matchPrefix(task_[0],"ABNO"))
    {
      System.err.println("ERROR: LBFGSB failed. Returned ABNO");
      error[0] = true;
      return initialValue;
    }

    if (matchPrefix(task_[0],"ERROR"))
    {
      System.err.println("ERROR: LBFGSB failed. Returned ERROR");
      error[0] = true;
      return initialValue;
    }

    if (matchPrefix(task_[0],"CONV"))
    {
      //cout << "LBFGSB converged!" << endl;
    }

    return f[0];
  }

  private double getValueAndGradient(double[] g, final double[] wts)
  {
    return func_.getValueAndGradient(g, wts);
  }

  private int getIdx(final int i, final int j, final int idim) { return (j-1)*idim + i; }

  private double max(final double a, final double b, final double c)
  {
    if (a >= b && a >= c) return a;
    if (b >= a && b >= c) return b;
    assert(c >= a && c >= b);
    return c;
  }

  private boolean matchPrefix(String task, String str)
  {
    if (task.length() < str.length()) return false;
    for (int i = 0; i < str.length(); i++)
      if (task.charAt(i) != str.charAt(i)) return false;
    return true;
  }

  private void setulb(double[] x, double[] f, final double factr, final double pgtol, final int iprint, Writer itfile)
  {
    int l1,l2,l3,lws,lr,lz,lt,ld,lsg,lwa,lyg, lsgo,lwy,lsy,lss,lyy,lwt,lwn,lsnd,lygo;

    if (matchPrefix(task_[0],"START"))
    {
      isave_[1]  = M*numWts_;
      isave_[2]  = M*M;
      isave_[3]  = 4*M*M;
      isave_[4]  = 1;
      isave_[5]  = isave_[4]  + isave_[1];
      isave_[6]  = isave_[5]  + isave_[1];
      isave_[7]  = isave_[6]  + isave_[2];
      isave_[8]  = isave_[7]  + isave_[2];
      isave_[9]  = isave_[8]  + isave_[2];
      isave_[10] = isave_[9]  + isave_[2];
      isave_[11] = isave_[10] + isave_[3];
      isave_[12] = isave_[11] + isave_[3];
      isave_[13] = isave_[12] + numWts_;
      isave_[14] = isave_[13] + numWts_;
      isave_[15] = isave_[14] + numWts_;
      isave_[16] = isave_[15] + numWts_;
      isave_[17] = isave_[16] + 8*M;
      isave_[18] = isave_[17] + M;
      isave_[19] = isave_[18] + M;
      isave_[20] = isave_[19] + M;
    }
    l1   = isave_[1];
    l2   = isave_[2];
    l3   = isave_[3];
    lws  = isave_[4];
    lwy  = isave_[5];
    lsy  = isave_[6];
    lss  = isave_[7];
    lyy  = isave_[8];
    lwt  = isave_[9];
    lwn  = isave_[10];
    lsnd = isave_[11];
    lz   = isave_[12];
    lr   = isave_[13];
    ld   = isave_[14];
    lt   = isave_[15];
    lwa  = isave_[16];
    lsg  = isave_[17];
    lsgo = isave_[18];
    lyg  = isave_[19];
    lygo = isave_[20];

    if (matchPrefix(task_[0],"START"))
    {
      ws_  = new double[lwy-lws+1];
      wy_  = new double[lsy-lwy+1];
      sy_  = new double[lss-lsy+1];
      ss_  = new double[lyy-lss+1];
      yy_  = new double[lwt-lyy+1];
      wt_  = new double[lwn-lwt+1];
      wn_  = new double[lsnd-lwn+1];
      snd_ = new double[lz-lsnd+1];
      z_   = new double[lr-lz+1];
      r_   = new double[ld-lr+1];
      d_   = new double[lt-ld+1];
      t_   = new double[lwa-lt+1];
      wa0_ = new double[2*M+1];
      wa1_ = new double[2*M+1];
      wa2_ = new double[2*M+1];
      wa3_ = new double[2*M+1];
      sg_  = new double[lsgo-lsg+1];
      sgo_ = new double[lyg-lsgo+1];
      yg_  = new double[lygo-lyg+1];
      ygo_ = new double[2*MMAX*numWts_+4*numWts_+12*MMAX*MMAX+12*MMAX-lygo+1];
      index_  = new int[numWts_+1];
      iwhere_ = new int[numWts_+1];
      indx2_  = new int[3*numWts_+1-2*numWts_+1];
    }

    mainlb(numWts_,M,x,l_,u_,nbd_,f,g_,factr,pgtol,  ws_, wy_, sy_, ss_, yy_, wt_, wn_, snd_, z_, r_, d_, t_,
           wa0_, wa1_, wa2_, wa3_, sg_, sgo_, yg_, ygo_,  index_, iwhere_, indx2_,
           task_, iprint, csave_, lsave_, isave2_, isave3_, dsave1_, dsave2_, itfile);
  }

  private void mainlb(final int n, final int m, double[] x, final double[] l, final double[] u, final int[] nbd,
                      double[] f,  double[] g, final double factr, final double pgtol,
                      double[] ws,  double[] wy,  double[] sy,  double[] ss,  double[] yy, double[] wt,  double[] wn,
                      double[] snd, double[] z,   double[] r,   double[] d,   double[] t,  double[] wa0, double[] wa1,
                      double[] wa2, double[] wa3, double[] sg,  double[] sgo, double[] yg,  double[] ygo, int[] index,
                      int[] iwhere, int[] indx2, String[] task, final int iprint, String[] csave, boolean[] lsave,
                      int[] isave, int[] isave3, double[] dsave, double[] dsave2, Writer itfile)
  {
    boolean[] prjctd = new boolean[1];
    boolean[] cnstnd = new boolean[1];
    boolean[] boxed  = new boolean[1];
    boolean[] wrk    = new boolean[1];
    int[]     nint   = new int[1];
    int[]     info   = new int[1];
    int[]     k      = new int[1];
    int[]     nfree  = new int[1];
    int[]     nenter = new int[1];
    int[]     ileave = new int[1];
    int[]     iword  = new int[1];
    int[]     ifun   = new int[1]; ifun[0] = 0;
    int[]     iback  = new int[1];
    int[]     nfgv   = new int[1];
    int[]     itail  = new int[1];
    //int[]     iupdat = new int[1];
    int[]     col    = new int[1];
    int[]     head   = new int[1];
    double[]  fold   = new double[1]; fold[0] = 0.0;
    double[]  gd     = new double[1];
    double[]  gdold  = new double[1]; gdold[0] = 0.0;
    double[]  stp    = new double[1];
    double[]  dnorm  = new double[1]; dnorm[0] = 0.0;
    double[]  dtd    = new double[1];
    double[]  xstep  = new double[1];
    double[]  stpmx  = new double[1]; stpmx[0] = 0.0;
    double[]  theta  = new double[1];
    boolean updatd = false;
    String  word = "";
    int     nintol=0, nskip=0, iter=0, nact=0, iupdat=0;
    double  tol=0.0,sbgnrm=0.0,ddum=0.0,epsmch=0.0,cpu1=0.0,cpu2=0.0,cachyt=0.0,sbtime=0.0,lnscht=0.0,rr=0.0,dr=0.0;
    long    time1=0L,time2=0L,time=0L;

    if (iprint > 1) { assert itfile != null; }

    int goId = 0;
    while(goId >= 0)
    {
      if (goId == 0)
      {
        if (matchPrefix(task[0],"START"))
        {
          time1 = timer_.time();

          //Generate the current machine precision.
          epsmch = dpmeps();
          //epsmch = 1.08420217E-19;
          //cout << "L-BFGS-B computed machine precision = " << epsmch << endl;

          //Initialize counters and scalars when task='START'.

          //for the limited memory BFGS matrices:
          col[0]    = 0;
          head[0]   = 1;
          theta[0]  = 1.0;
          iupdat = 0;
          updatd = false;

          //for operation counts:
          iter     = 0;
          nfgv[0]  = 0;
          nint[0]  = 0;
          nintol   = 0;
          nskip    = 0;
          nfree[0] = n;

          //for stopping tolerance:
          tol = factr * epsmch;

          //for measuring running time:
          cachyt = 0;
          sbtime = 0;
          lnscht = 0;

          //'word' records the status of subspace solutions.
          word = "---";

          //'info' records the termination information.
          info[0] = 0;

          //Check the input arguments for errors.
          errclb(n, m, factr, l, u, nbd, task, info,k);
          if (matchPrefix(task[0],"ERROR"))
          {
            prn3lb(n, x, f, task, iprint, info[0], k[0], itfile, iter, nfgv[0], nintol, nskip, nact, sbgnrm, 0.0, nint[0],
                   iback[0], stp[0], xstep[0], cachyt, sbtime, lnscht);
            return;
          }

          prn1lb(n, m, l, u, x, iprint, itfile, epsmch);

          //Initialize iwhere & project x onto the feasible set.
          active(n, l, u, nbd, x, iwhere, iprint, prjctd, cnstnd, boxed);

          //The end of the initialization.
        }
        else
        {
          //restore local variables.

          prjctd[0] = lsave[1];
          cnstnd[0] = lsave[2];
          boxed[0]  = lsave[3];
          updatd    = lsave[4];

          nintol = isave[1];
          //itfile = isave[3];
          iback[0]  = isave[4];
          nskip     = isave[5];
          head[0]   = isave[6];
          col[0]    = isave[7];
          itail[0]  = isave[8];
          iter      = isave[9];
          iupdat = isave[10];
          nint[0]   = isave[12];
          nfgv[0]   = isave[13];
          info[0]   = isave[14];
          ifun[0]   = isave[15];
          iword[0]  = isave[16];
          nfree[0]  = isave[17];
          nact      = isave[18];
          ileave[0] = isave[19];
          nenter[0] = isave[20];

          theta[0]  = dsave[1];
          fold[0]   = dsave[2];
          tol       = dsave[3];
          dnorm[0]  = dsave[4];
          epsmch    = dsave[5];
          cpu1      = dsave[6];
          cachyt    = dsave[7];
          sbtime    = dsave[8];
          lnscht    = dsave[9];
          time1     = (int) dsave[10];
          gd[0]     = dsave[11];
          stpmx[0]  = dsave[12];
          sbgnrm    = dsave[13];
          stp[0]    = dsave[14];
          gdold[0]  = dsave[15];
          dtd[0]    = dsave[16];

          //After returning from the driver go to the point where execution is to resume.
          if (matchPrefix(task[0],"FG_LN")) { goId = 66;  continue; }
          if (matchPrefix(task[0],"NEW_X")) { goId = 777; continue; }
          if (matchPrefix(task[0],"FG_ST")) { goId = 111; continue; }
        }

        //Compute f0 and g0.
        task[0] = "FG_START";
        goId = 1000; continue;
      }
      else if (goId == 111)
      {
        nfgv[0] = 1;

        //Compute the infinity norm of the (-) projected gradient.

        sbgnrm = projgr(n, l, u, nbd, x, g);

        if (iprint >= 1)
        {
          System.err.println(sbgnrm + "At iterate " + iter + ": f=" + f[0] + ",  |proj g|=");
          write(itfile, "At iterate " + iter + ": nfgv=" + nfgv[0] + ", sbgnrm=" + sbgnrm + ", f=" + f[0] + "\n");
        }

        if (sbgnrm <= pgtol)
        {
          //terminate the algorithm.
          task[0] = "CONVERGENCE: NORM OF PROJECTED GRADIENT <= PGTOL";
          goId = 999; continue;
        }
        goId = 222; continue;
      }
      else if (goId == 222)
      {
        //----------------- the beginning of the loop ---------------------

        if (iprint >= 99) System.out.println("ITERATION " + (iter + 1));
        iword[0] = -1;

        if (!cnstnd[0] && col[0] > 0)
        {
          //skip the search for GCP.
          dcopy(n, x, 0, 1, z, 0, 1);
          wrk[0] = updatd;
          nint[0] = 0;
          goId = 333; continue;
        }

        /*********************************************************************
         *
         *     Compute the Generalized Cauchy Point (GCP).
         *
         *********************************************************************/

        cpu1 = timer_.time();

        //HERE
        cauchy(n, x, l, u, nbd, g, indx2, iwhere, t, d, z, m, wy, ws, sy, wt, theta[0], col[0], head[0], wa0, wa1, wa2, wa3,
               nint, sg, yg, iprint, sbgnrm, info, epsmch);

        if (info[0] > 0)
        {
          //singular triangular system detected; refresh the lbfgs memory.
          if (iprint >= 1) System.out.println("Singular triangular system detected; refresh the lbfgs memory and restart the iteration.");
          info[0] = 0;
          col[0] = 0;
          head[0] = 1;
          theta[0] = 1.0;
          iupdat = 0;
          updatd = false;
          cpu2 = timer_.time();
          cachyt = cachyt + cpu2 - cpu1;
          goId = 222;
          continue;
        }
        cpu2 = timer_.time();
        cachyt = cachyt + cpu2 - cpu1;
        nintol = nintol + nint[0];

        //Count the entering and leaving variables for iter > 0;
        //find the index set of free and active variables at the GCP.

        freev(n, nfree, index, nenter, ileave, indx2, iwhere, wrk, updatd, cnstnd[0], iprint, iter);

        nact = n - nfree[0];
        goId = 333; continue;
      }
      else if (goId == 333)
      {
        //If there are no free variables or B=theta*I, then
        //skip the subspace minimization.

        if (nfree[0] == 0 || col[0] == 0) { goId = 555; continue; }

        /**********************************************************************
         *
         *     Subspace minimization.
         *
         **********************************************************************/

        cpu1 = timer_.time();

        //Form  the LEL^T factorization of the indefinite
        //matrix    K = [-D -Y'ZZ'Y/theta     L_a'-R_z'  ]
        //              [L_a -R_z           theta*S'AA'S ]
        //where     E = [-I  0]
        //              [ 0  I]

        if (wrk[0]) formk(n, nfree[0], index, nenter[0], ileave[0], indx2, iupdat, updatd, wn, snd, m, ws, wy, sy, theta[0], col[0], head[0], info);

        if (info[0] != 0)
        {
          //nonpositive definiteness in Cholesky factorization;
          //refresh the lbfgs memory and restart the iteration.
          if (iprint >= 1) System.out.println("Nonpositive definiteness in Cholesky factorization in formk; refresh the lbfgs memory and restart the iteration.");

          info[0] = 0;
          col[0] = 0;
          head[0] = 1;
          theta[0] = 1.0;
          iupdat = 0;
          updatd = false;
          cpu2 = timer_.time();
          sbtime = sbtime + cpu2 - cpu1;
          goId = 222; continue;
        }

        //compute r=-Z'B(xcp-xk)-Z'g (using wa(2m+1)=W'(xcp-x)
        //from 'cauchy').

        cmprlb(n, m, x, g, ws, wy, sy, wt, z, r, wa0, wa1, index, theta[0], col[0], head[0], nfree[0], cnstnd[0], info);

        if (info[0] != 0) { goId = 444; continue; }

        //call the direct method.
        subsm(n, m, nfree[0], index, l, u, nbd, z, r, ws, wy, theta[0], col[0], head[0], iword, wa0, wn, iprint, info);
        goId = 444; continue;
      }
      else if (goId == 444)
      {
        if (info[0] != 0)
        {
          //singular triangular system detected;
          //refresh the lbfgs memory and restart the iteration.
          if (iprint >= 1) System.out.println("Singular triangular system detected; refresh the lbfgs memory and restart the iteration.");

          info[0]   = 0;
          col[0]    = 0;
          head[0]   = 1;
          theta[0]  = 1.0;
          iupdat = 0;
          updatd = false;
          cpu2 = timer_.time();
          sbtime = sbtime + cpu2 - cpu1;
          goId = 222; continue;
        }

        cpu2 = timer_.time();
        sbtime = sbtime + cpu2 - cpu1;
        goId = 555; continue;
      }
      else if (goId == 555)
      {
        /*********************************************************************
         *
         *     Line search and optimality tests.
         *
         *********************************************************************/

        //Generate the search direction d:=z-x.

        for (int i = 1; i <= n; i++)
          d[i] = z[i] - x[i];

        cpu1 = timer_.time();
        goId = 66; continue;
      }
      else if (goId == 66)
      {
        lnsrlb(n, l, u, nbd, x, f[0], fold, gd, gdold, g, d, r, t, z, stp, dnorm, dtd, xstep, stpmx, iter,
               ifun, iback, nfgv, info, task, boxed[0], cnstnd[0], csave, isave3, dsave2);

        if (info[0] != 0 || iback[0] >= 20)
        {
          //restore the previous iterate.
          dcopy(n, t, 0, 1, x, 0, 1);
          dcopy(n, r, 0, 1, g, 0, 1);
          f[0] = fold[0];
          if (col[0] == 0)
          {
            //abnormal termination.
            if (info[0] == 0)
            {
              info[0] = -9;
              //restore the actual number of f and g evaluations etc.
              nfgv[0] -= 1;
              ifun[0] -= 1;
              iback[0] -= 1;
            }
            task[0] = "ABNORMAL_TERMINATION_IN_LNSRCH";
            iter += 1;
            goId = 999; continue;
          }
          else
          {
            //refresh the lbfgs memory and restart the iteration.
            if (iprint >= 1) System.out.println("Bad direction in the line search; the lbfgs memory and restart the iteration");

            if (info[0] == 0) nfgv[0] = nfgv[0] - 1;
            info[0]   = 0;
            col[0]    = 0;
            head[0]   = 1;
            theta[0]  = 1.0;
            iupdat = 0;
            updatd = false;
            task[0] = "RESTART_FROM_LNSRCH";
            cpu2 = timer_.time();
            lnscht += cpu2 - cpu1;
            goId = 222; continue;
          }
        }
        else if (matchPrefix(task[0], "FG_LN")) { goId = 1000; continue; }
        else
        {
          //calculate and print out the quantities related to the new X.
          cpu2 = timer_.time();
          lnscht += cpu2 - cpu1;
          iter += 1;

          //Compute the infinity norm of the projected (-)gradient.

          sbgnrm = projgr(n, l, u, nbd, x, g);

          //Print iteration information.

          prn2lb(n, x, f[0], g, iprint, itfile, iter, nfgv[0], nact, sbgnrm, nint[0], word, iword[0], iback[0], stp[0], xstep[0]);
          goId = 1000; continue;
        }
        //goId = 777; continue;
      }
      else if (goId == 777)
      {
        //Test for termination.

        if (sbgnrm <= pgtol)
        {
          //terminate the algorithm.
          task[0] = "CONVERGENCE: NORM OF PROJECTED GRADIENT <= PGTOL";
          goId = 999; continue;
        }

        ddum = max(Math.abs(fold[0]), Math.abs(f[0]), 1.0);
        if ((fold[0] - f[0]) <= tol * ddum)
        {
          //terminate the algorithm.
          task[0] = "CONVERGENCE: REL_REDUCTION_OF_F <= FACTR*EPSMCH";
          if (iback[0] >= 10) info[0] = -5;
          //i.e., to issue a warning if iback>10 in the line search.
          goId = 999; continue;
        }

        //Compute d=newx-oldx, r=newg-oldg, rr=y'y and dr=y's.

        for (int i = 1; i <= n; i++)
          r[i] = g[i] - r[i];
        rr = ddot(n, r, 0, 1, r, 0, 1);
        if (stp[0] == 1.0)
        {
          dr = gd[0] - gdold[0];
          ddum = -gdold[0];
        }
        else
        {
          dr = (gd[0] - gdold[0]) * stp[0];
          dscal(n, stp[0], d, 0, 1);
          ddum = -gdold[0] * stp[0];
        }

        if (dr <= epsmch * ddum)
        {
          //skip the L-BFGS update.
          nskip = nskip + 1;
          updatd = false;
          if (iprint >= 1) System.out.println("  ys=" + dr + "   -gs=" + ddum + " BFSG update SKIPPED");
          goId = 888; continue;
        }

        /*********************************************************************
         *
         *     Update the L-BFGS matrix.
         *
         *********************************************************************/

        updatd = true;
        iupdat += 1;

        //Update matrices WS and WY and form the middle matrix in B.

        matupd(n, m, ws, wy, sy, ss, d, r, itail, iupdat, col, head, theta, rr, dr, stp[0], dtd[0]);

        //Form the upper half of the pds T = theta*SS + L*D^(-1)*L';
        //Store T in the upper triangular of the array wt;
        //Cholesky factorize T to J*J' with
        //J' stored in the upper triangular of wt.

        formt(m, wt, sy, ss, col[0], theta[0], info);

        if (info[0] != 0)
        {
          //nonpositive definiteness in Cholesky factorization;
          //refresh the lbfgs memory and restart the iteration.
          if (iprint >= 1) System.out.println("Nonpositive definiteness in Cholesky factorization in formt; refresh the lbfgs memory and restart the iteration.");

          info[0]   = 0;
          col[0]    = 0;
          head[0]   = 1;
          theta[0]  = 1.0;
          iupdat = 0;
          updatd    = false;
          goId = 222; continue;
        }

        //Now the inverse of the middle matrix in B is

        //[  D^(1/2)      O ] [ -D^(1/2)  D^(-1/2)*L' ]
        //[ -L*D^(-1/2)   J ] [  0        J'          ]
        goId = 888; continue;
      }
      else if (goId == 888)
      {
        //-------------------- the end of the loop -----------------------------
        goId = 222; continue;
      }
      else if (goId == 999)
      {
        time2 = timer_.time();
        time = time2 - time1;
        prn3lb(n, x, f, task, iprint, info[0], k[0], itfile, iter, nfgv[0], nintol, nskip, nact, sbgnrm, time, nint[0],
               iback[0], stp[0], xstep[0], cachyt, sbtime, lnscht);
        goId = 1000; continue;
      }
      else if (goId == 1000)
      {
        //Save local variables.
        lsave[1] = prjctd[0];
        lsave[2] = cnstnd[0];
        lsave[3] = boxed[0];
        lsave[4] = updatd;

        isave[1]  = nintol;
        //isave[3]  = itfile;
        isave[4]  = iback[0];
        isave[5]  = nskip;
        isave[6]  = head[0];
        isave[7]  = col[0];
        isave[8]  = itail[0];
        isave[9]  = iter;
        isave[10] = iupdat;
        isave[12] = nint[0];
        isave[13] = nfgv[0];
        isave[14] = info[0];
        isave[15] = ifun[0];
        isave[16] = iword[0];
        isave[17] = nfree[0];
        isave[18] = nact;
        isave[19] = ileave[0];
        isave[20] = nenter[0];

        dsave[1]  = theta[0];
        dsave[2]  = fold[0];
        dsave[3]  = tol;
        dsave[4]  = dnorm[0];
        dsave[5]  = epsmch;
        dsave[6]  = cpu1;
        dsave[7]  = cachyt;
        dsave[8]  = sbtime;
        dsave[9]  = lnscht;
        dsave[10] = time1;
        dsave[11] = gd[0];
        dsave[12] = stpmx[0];
        dsave[13] = sbgnrm;
        dsave[14] = stp[0];
        dsave[15] = gdold[0];
        dsave[16] = dtd[0];
        break;
      }
      else
      {
        System.out.println("ERROR: in mainlb(): Unknown goId " + goId);
        System.exit(-1);
      }

    }//while(true)
  }

  private void write(Writer itfile, String str)
  {
    try { itfile.write(str); }
    catch(IOException e) { System.out.println("Failed to write itfile: " + str); System.exit(-1); }
  }

  private void active(final int n, final double[] l, final double[] u, final int[] nbd, double[] x, int[] iwhere,
                      final int iprint, boolean[] prjctd, boolean[]cnstnd, boolean[] boxed)
  {
    int nbdd;

    //Initialize nbdd, prjctd, cnstnd and boxed.
    nbdd   = 0;
    prjctd[0] = false;
    cnstnd[0] = false;
    boxed[0]  = true;

    //Project the initial x to the feasible set if necessary.

    for (int i = 1; i <= n; i++)
    {
      if (nbd[i] > 0)
      {
        if (nbd[i] <= 2 && x[i] <= l[i])
        {
	        if (x[i] < l[i])
          {
            prjctd[0] = true;
	          x[i] = l[i];
          }
          nbdd += 1;
        }
        else
        if (nbd[i] >= 2 && x[i] >= u[i])
        {
	        if (x[i] > u[i])
          {
            prjctd[0] = true;
	          x[i] = u[i];
          }
          nbdd += 1;
        }
      }
    }
    //Initialize iwhere and assign values to cnstnd and boxed.

    for (int i = 1; i <= n; i++)
    {
      if (nbd[i] != 2) boxed[0] = false;
      if (nbd[i] == 0)
      {
        //this variable is always free
        iwhere[i] = -1;
        //otherwise set x(i)=mid(x(i), u(i), l(i)).
      }
      else
      {
        cnstnd[0] = true;
        if (nbd[i] == 2 && (u[i]-l[i]) <= 0.0)
        {
          //this variable is always fixed
          iwhere[i] = 3;
        }
        else
          iwhere[i] = 0;
      }
    }

    if (iprint >= 0)
    {
      if (prjctd[0])  System.out.println("The initial X is infeasible.  Restart with its projection.");
      if (!cnstnd[0]) System.out.println("This problem is unconstrained.");
    }

    if (iprint > 0) System.out.println("At X0 " + nbdd + " variables are exactly at the bounds");
  }

  private void bmv(final int m, double[] sy, double[] wt, final int col, final double[] v, double[] p, int[] info)
  {
    int i2;
    double sum;

    if (col == 0) return;

    //PART I: solve [  D^(1/2)      O ] [ p1 ] = [ v1 ]
    //              [ -L*D^(-1/2)   J ] [ p2 ]   [ v2 ].

    //solve Jp2=v2+LD^(-1)v1.
    p[col+1] = v[col+1];

    for (int i = 2; i <= col; i++)
    {
      i2 = col + i;
      sum = 0;
      for (int k = 1; k <= i-1; k++)
        sum += sy[getIdx(i,k,m)]*v[k]/sy[getIdx(k,k,m)];

      p[i2] = v[i2] + sum;
    }

    //Solve the triangular system
    dtrsl(wt,m,col,p,col+1-1,11,info);

    if (info[0] != 0) return;

    //solve D^(1/2)p1=v1.
    for (int i = 1; i <= col; i++)
      p[i] = v[i]/Math.sqrt(sy[getIdx(i, i, m)]);

    //PART II: solve [ -D^(1/2)   D^(-1/2)*L'  ] [ p1 ] = [ p1 ]
    //               [  0         J'           ] [ p2 ]   [ p2 ].

    //solve J^Tp2=p2.
    dtrsl(wt,m,col,p,col+1-1,1,info);

    if (info[0] != 0) return;

    //compute p1=-D^(-1/2)(p1-D^(-1/2)L'p2)
    //          =-D^(-1/2)p1+D^(-1)L'p2.
    for (int i = 1; i <= col; i++)
      p[i] = -p[i]/Math.sqrt(sy[getIdx(i, i, m)]);

    for (int i = 1; i <= col; i++)
    {
      sum = 0;
      for (int k = i+1; i <= col; i++)
        sum += sy[getIdx(k,i,m)]*p[col+k]/sy[getIdx(i,i,m)];
      p[i] += sum;
    }
  }

  private void cauchy(final int n, final double[] x, final double[] l, final double[] u, final int[] nbd,
                      final double[] g, int[] iorder, int[] iwhere, double[] t, double[] d, double[] xcp, final int m,
                      double[] wy, double[] ws, double[] sy, double[] wt, final double theta, final int col,
                      final int head, double[] p, double[] c, double[] wbp, double[] v, int[] nint, final double[] sg,
                      final double[] yg, final int iprint, final double sbgnrm, int[] info, final double epsmch)
  {
    boolean xlower=false,xupper=false,bnded=false;
    int col2=0,nfree=0,nbreak=0,pointr=0,ibp=0,nleft=0,ibkmin=0,iter=0;
    double f1=0.0,f2=0.0,dt=0.0,dtm=0.0,tsum=0.0,dibp=0.0,zibp=0.0,dibp2=0.0,bkmin=0.0,tu=0.0,tl=0.0,wmc=0.0,wmp=0.0,
           wmw=0.0,tj=0.0,tj0=0.0,neggi=0.0,f2_org=0.0;

    //Check the status of the variables, reset iwhere(i) if necessary;
    //compute the Cauchy direction d and the breakpoints t; initialize
    //the derivative f1 and the vector p = W'd (for theta = 1).

    int goId = 0;
    while(goId >= 0)
    {

    if (goId == 0)
    {
      if (sbgnrm <= 0.0)
      {
        if (iprint >= 0) System.out.println("Subgnorm = 0.  GCP = X.");
        dcopy(n,x,0,1,xcp,0,1);
        return;
      }
      bnded = true;
      nfree = n + 1;
      nbreak = 0;
      ibkmin = 0;
      bkmin = 0.0;
      col2 = 2*col;
      f1 = 0.0;
      if (iprint >= 99) System.out.println("---------------- CAUCHY entered-------------------");

      //We set p to zero and build it up as we determine d.
      for (int i = 1; i <= col2; i++)
        p[i] = 0.0;

      //In the following loop we determine for each variable its bound
      //status and its breakpoint, and update p accordingly.
      //Smallest breakpoint is identified.

      for (int i = 1; i <= n; i++)
      {
        neggi = -g[i];
        if (iwhere[i] != 3 && iwhere[i] != -1)
        {
          //if x(i) is not a constant and has bounds,
          //compute the difference between x(i) and its bounds.
          if (nbd[i] <= 2) tl = x[i] - l[i];
          if (nbd[i] >= 2) tu = u[i] - x[i];

          //If a variable is close enough to a bound
          //we treat it as at bound.
          xlower = (nbd[i] <= 2 && tl <= 0.0);
          xupper = (nbd[i] >= 2 && tu <= 0.0);

          //reset iwhere(i).
          iwhere[i] = 0;
          if (xlower)
          {
            if (neggi <= 0.0) iwhere[i] = 1;
          }
          else if (xupper)
          {
            if (neggi >= 0.0) iwhere[i] = 2;
          }
          else
          {
            if (Math.abs(neggi) <= 0.0) iwhere[i] = -3;
          }
        }
        pointr = head;
        if (iwhere[i] != 0 && iwhere[i] != -1)
          d[i] = 0.0;
        else
        {
          d[i] = neggi;
          f1 -= neggi*neggi;
          //calculate p := p - W'e_i* (g_i).
          for (int j = 1; j <= col; j++)
          {
            p[j] += wy[getIdx(i,pointr,n)] * neggi;
            p[col + j] += ws[getIdx(i,pointr,n)] * neggi;
            pointr = pointr%m + 1;
          }
          if (nbd[i] <= 2 && nbd[i] != 0 && neggi < 0.0)
          {
            //x[i] + d[i] is bounded; compute t[i].
            nbreak += 1;
            iorder[nbreak] = i;
            t[nbreak] = tl/(-neggi);
            if (nbreak == 1 || t[nbreak] < bkmin)
            {
              bkmin = t[nbreak];
              ibkmin = nbreak;
            }
          }
          else if (nbd[i] >= 2 && neggi > 0.0)
          {
            //x(i) + d(i) is bounded; compute t(i).
            nbreak += 1;
            iorder[nbreak] = i;
            t[nbreak] = tu/neggi;
            if (nbreak == 1 || t[nbreak] < bkmin)
            {
              bkmin = t[nbreak];
              ibkmin = nbreak;
            }
          }
          else
          {
            //x(i) + d(i) is not bounded.
            nfree -= 1;
            iorder[nfree] = i;
            if (Math.abs(neggi) > 0.0) bnded = false;
          }
        }
      } //for (int i = 1; i <= n; i++)


      //The indices of the nonzero components of d are now stored
      //in iorder(1),...,iorder(nbreak) and iorder(nfree),...,iorder(n).
      //The smallest of the nbreak breakpoints is in t(ibkmin)=bkmin.

      if (theta != 1.0)
      {
        //complete the initialization of p for theta not= one.
        dscal(col,theta,p,col+1-1,1);
      }

      //Initialize GCP xcp = x.

      dcopy(n,x,0,1,xcp,0,1);

      if (nbreak == 0 && nfree == n + 1)
      {
        //is a zero vector, return with the initial xcp as GCP.
        if (iprint > 100)
        {
          System.out.println("Cauchy X = ");
          for (int i = 1; i <= n; i++)
            System.out.print(xcp[i] + " ");
          System.out.println();
        }
        return;
      }

      //Initialize c = W'(xcp - x) = 0.

      for (int j = 1; j <= col2; j++)
        c[j] = 0.0;

      //Initialize derivative f2.

      f2 = -theta*f1;
      f2_org = f2;
      if (col > 0)
      {
        bmv(m,sy,wt,col,p,v,info);
        if (info[0] != 0) return;
        f2 -= ddot(col2,v,0,1,p,0,1);
      }
      dtm = -f1/f2;
      tsum = 0.0;
      nint[0] = 1;
      if (iprint >= 99) System.out.println("There are " + nbreak + " breakpoints");

      //If there are no breakpoints, locate the GCP and return.

      if (nbreak == 0) { goId = 888; continue; }

      nleft = nbreak;
      iter = 1;

      tj = 0.0;
      goId = 777; continue;
      //------------------- the beginning of the loop -------------------------
    }
    else if (goId == 777)
    {
      //Find the next smallest breakpoint;
      //compute dt = t(nleft) - t(nleft + 1).

      tj0 = tj;
      if (iter == 1)
      {
        //Since we already have the smallest breakpoint we need not do
        //heapsort yet. Often only one breakpoint is used and the
        //cost of heapsort is avoided.
        tj = bkmin;
        ibp = iorder[ibkmin];
      }
      else
      {
        if (iter == 2)
        {
          //Replace the already used smallest breakpoint with the
          //breakpoint numbered nbreak > nlast, before heapsort call.
          if (ibkmin != nbreak)
          {
            t[ibkmin] = t[nbreak];
            iorder[ibkmin] = iorder[nbreak];
          }
          //Update heap structure of breakpoints
          //(if iter=2, initialize heap).
        }

        hpsolb(nleft,t,iorder,iter-2);
        tj = t[nleft];
        ibp = iorder[nleft];
      }

      dt = tj - tj0;

      if (dt != 0.0 && iprint >= 100)
      {
        System.out.println("Piece    " + nint[0] + " --f1, f2 at start point " + f1 + "," + f2);
        System.out.println("Distance to the next break point = " + dt);
        System.out.println("Distance to the stationary point = " + dtm);
      }

      //If a minimizer is within this interval, locate the GCP and return.

      if (dtm < dt) { goId = 888; continue; }

      //Otherwise fix one variable and
      //reset the corresponding component of d to zero.

      tsum += dt;
      nleft -= 1;
      iter += 1;
      dibp = d[ibp];
      d[ibp] = 0.0;
      if (dibp > 0.0)
      {
        zibp = u[ibp] - x[ibp];
        xcp[ibp] = u[ibp];
        iwhere[ibp] = 2;
      }
      else
      {
        zibp = l[ibp] - x[ibp];
        xcp[ibp] = l[ibp];
        iwhere[ibp] = 1;
      }
      if (iprint >= 100) System.out.println("Variable  " + ibp + " is fixed.");
      if (nleft == 0 && nbreak == n)
      {
        //all n variables are fixed, return with xcp as GCP.
        dtm = dt;
        goId = 999; continue;
      }

      //Update the derivative information.

      nint[0] += 1;
      dibp2 = dibp*dibp;

      //Update f1 and f2.

      //temporarily set f1 and f2 for col=0.
      f1 += dt*f2 + dibp2 - theta*dibp*zibp;
      f2 -= theta*dibp2;

      if (col > 0)
      {
        //update c = c + dt*p.
        daxpy(col2,dt,p,0,1,c,0,1);

        //choose wbp,
        //the row of W corresponding to the breakpoint encountered.
        pointr = head;
        for (int j = 1; j <= col; j++)
        {
          wbp[j] = wy[getIdx(ibp,pointr,n)];
          wbp[col + j] = theta*ws[getIdx(ibp,pointr,n)];
          pointr = pointr%m + 1;
        }
        //compute (wbp)Mc, (wbp)Mp, and (wbp)M(wbp)'.
        bmv(m,sy,wt,col,wbp,v,info);
        if (info[0] != 0) return;
        wmc = ddot(col2,c,0,1,v,0,1);
        wmp = ddot(col2,p,0,1,v,0,1);
        wmw = ddot(col2,wbp,0,1,v,0,1);

        //update p = p - dibp*wbp.
        daxpy(col2,-dibp,wbp,0,1,p,0,1);

        //complete updating f1 and f2 while col > 0.
        f1 += dibp*wmc;
        f2 += 2.0*dibp*wmp - dibp2*wmw;
      }

      f2 = Math.max(epsmch*f2_org,f2);
      if (nleft > 0)
      {
        dtm = -f1/f2;
        goId = 777; continue;
        //to repeat the loop for unsearched intervals.
      }
      else if(bnded)
      {
        f1  = 0.0;
        f2  = 0.0;
        dtm = 0.0;
      }
      else
        dtm = -f1/f2;

      goId = 888; continue;
      //------------------- the end of the loop -------------------------------
    }
    else if (goId == 888)
    {
      if (iprint >= 99)
      {
        System.out.println("\nGCP found in this segment\n" +
                           "Piece    " + nint[0] + " --f1, f2 at start point " + f1 + ","  + f2 +"\n" +
                           "Distance to the stationary point = " + dtm );
      }

      if (dtm <= 0.0) dtm = 0.0;
      tsum += dtm;

      //Move free variables (i.e., the ones w/o breakpoints) and
      //the variables whose breakpoints haven't been reached.

      daxpy(n,tsum,d,0,1,xcp,0,1);
      goId = 999; continue;
    }
    else if (goId == 999)
    {
      //Update c = c + dtm*p = W'(x^c - x)
      //which will be used in computing r = Z'(B(x^c - x) + g).

      if (col > 0) daxpy(col2,dtm,p,0,1,c,0,1);
      if (iprint > 100)
      {
        System.out.print("Cauchy X = ");
        for (int i = 1; i <=n; i++)
          System.out.print(xcp[i] + " ");
        System.out.println();
      }
      if (iprint >= 99) System.out.println("---------------- exit CAUCHY----------------------");
      break;
    }
    else
    {
      System.out.println("ERROR: in caunchy(). Unknown goId " + goId);
      System.exit(-1);
    }

    }//while(goId >= 0)
  }

  private void cmprlb(final int n, final int m, final double[] x, final double[] g, double[] ws, double[] wy,
                      double[] sy, double[] wt, final double[] z, double[] r, double[] wa0, double[] wa1,
                      final int[] index, final double theta, final int col, final int head, final int nfree,
                      final boolean cnstnd, int[] info)
  {
    //int i,j;
    int k,pointr,idx;
    double a1,a2;

    if (!cnstnd && col > 0)
    {
      for (int i = 1; i <= n; i++)
        r[i] = -g[i];
    }
    else
    {
      for (int i = 1; i <= nfree; i++)
      {
        k = index[i];
        r[i] = -theta*(z[k] - x[k]) - g[k];
      }
      bmv(m,sy,wt,col,wa1,wa0,info);

      if (info[0] != 0)
      {
        info[0] = -8;
        return;
      }
      pointr = head;
      for (int j = 1; j <= col; j++)
      {
        a1 = wa0[j];
        a2 = theta*wa0[col + j];
        for (int i = 1; i <= nfree; i++)
        {
          k = index[i];
          idx = getIdx(k,pointr,n);
          r[i] += wy[idx]*a1 + ws[idx]*a2;
        }

        pointr = pointr%m + 1;
      }
    }
  }

  private void errclb(final int n, final int m, final double factr, final double[] l, final double[] u, final int[] nbd,
                      String[] task, int[] info, int[] k)
    {
      //Check the input arguments for errors.

      if (n <= 0)      task[0] = "ERROR: N <= 0";
      if (m <= 0)      task[0] = "ERROR: M <= 0";
      if (factr < 0.0) task[0] = "ERROR: FACTR < 0";

      //Check the validity of the arrays nbd[i], u[i], and l[i].
      for (int i = 1; i <= n; i++)
      {
        if (nbd[i] < 0 || nbd[i] > 3)
        {
          //return
          task[0] = "ERROR: INVALID NBD";
          info[0] = -6;
          k[0] = i;
        }
        if (nbd[i] == 2)
        {
          if (l[i] > u[i])
          {
            //return
            task[0] = "ERROR: NO FEASIBLE SOLUTION";
            info[0] = -7;
            k[0] = i;
          }
        }
      }
    }

  private void formk(final int n, final int nsub, final int[] ind, final int nenter, final int ileave, final int[] indx2,
                     final int iupdat, final boolean updatd, double[] wn, double[] wn1, final int m, double[] ws,
                     double[] wy, double[] sy, final double theta, final int col, final int head, int[] info)
  {
      int m2,ipntr,jpntr,iy,is,jy,js,is1,js1,k1,col2,pbegin,pend,dbegin,dend,upcl;
      int idx1, idx2;
      double temp1,temp2,temp3,temp4;

      m2 = 2*m;

      //Form the lower triangular part of
      //WN1 = [Y' ZZ'Y   L_a'+R_z']
      //      [L_a+R_z   S'AA'S   ]
      //where L_a is the strictly lower triangular part of S'AA'Y
      //      R_z is the upper triangular part of S'ZZ'Y.

      if (updatd)
      {
        if (iupdat > m)
        {
          //shift old part of WN1.
          for (jy = 1; jy <= m-1; jy++)
          {
            js = m + jy;
            dcopy(m-jy,wn1,getIdx(jy+1,jy+1,m2)-1,1,
                       wn1,getIdx(jy,jy,m2)-1,1);
            dcopy(m-jy,wn1,getIdx(js+1,js+1,m2)-1,1,
                       wn1,getIdx(js,js,m2)-1, 1);
            dcopy(m-1, wn1,getIdx(m+2,jy+1,m2)-1, 1,
                       wn1,getIdx(m+1,jy,m2)-1,1);
          }
        }

        //put new rows in blocks (1,1), (2,1) and (2,2).
        pbegin = 1;
        pend = nsub;
        dbegin = nsub + 1;
        dend = n;
        iy = col;
        is = m + col;
        ipntr = head + col - 1;
        if (ipntr > m) ipntr -= m ;
        jpntr = head;

        for (jy = 1; jy <= col; jy++)
        {
          js = m + jy;
          temp1 = 0.0;
          temp2 = 0.0;
          temp3 = 0.0;
          //compute element jy of row 'col' of Y'ZZ'Y
          for (int k = pbegin; k <= pend; k++)
          {
            k1 = ind[k];
            temp1 += wy[getIdx(k1,ipntr,n)]*wy[getIdx(k1,jpntr,n)];
          }
          //compute elements jy of row 'col' of L_a and S'AA'S
          for (int k = dbegin; k <= dend; k++)
          {
            k1 = ind[k];
            idx1 = getIdx(k1,ipntr,n);
            idx2 = getIdx(k1,jpntr,n);
            temp2 += ws[idx1] * ws[idx2];
            temp3 += ws[idx1] * wy[idx2];
          }
          wn1[getIdx(iy,jy,m2)] = temp1;
          wn1[getIdx(is,js,m2)] = temp2;
          wn1[getIdx(is,jy,m2)] = temp3;
          jpntr = jpntr%m + 1;
        }

        //put new column in block (2,1).
        jy = col;
        jpntr = head + col - 1;
        if (jpntr > m) jpntr -= m;
        ipntr = head;
        for (int i = 1; i <= col; i++)
        {
          is = m + i;
          temp3 = 0.0;
          //compute element i of column 'col' of R_z
          for (int k = pbegin; k <= pend; k++)
          {
            k1 = ind[k];
            temp3 += ws[getIdx(k1,ipntr,n)]*wy[getIdx(k1,jpntr,n)];
          }
          ipntr = ipntr%m + 1;
          wn1[getIdx(is,jy,m2)] = temp3;
        }
        upcl = col - 1;
      }
      else
        upcl = col;

      //modify the old parts in blocks (1,1) and (2,2) due to changes
      //in the set of free variables.
      ipntr = head;

      for (iy = 1; iy <= upcl; iy++)
      {
        is = m + iy;
        jpntr = head;

        for (jy = 1; jy <= iy; jy++)
        {
          js = m + jy;
          temp1 = 0.0;
          temp2 = 0.0;
          temp3 = 0.0;
          temp4 = 0.0;
          for (int k = 1; k <= nenter; k++)
          {
            k1 = indx2[k];
            idx1 = getIdx(k1,ipntr,n);
            idx2 = getIdx(k1,jpntr,n);
            temp1 += wy[idx1]*wy[idx2];
            temp2 += ws[idx1]*ws[idx2];
          }
          for (int k = ileave; k <= n; k++)
          {
            k1 = indx2[k];
            idx1 = getIdx(k1,ipntr,n);
            idx2 = getIdx(k1,jpntr,n);
            temp3 += wy[idx1]*wy[idx2];
            temp4 += ws[idx1]*ws[idx2];
          }
          wn1[getIdx(iy,jy,m2)] += temp1 - temp3;
          wn1[getIdx(is,js,m2)] += temp4 - temp2;
          jpntr = jpntr%m + 1;
        }
        ipntr = ipntr%m + 1;
      }
      //modify the old parts in block (2,1).
      ipntr = head;

      for (is = m+1; is <= m + upcl; is++)
      {
        jpntr = head;
        for (jy = 1; jy <= upcl; jy++)
        {
          temp1 = 0.0;
          temp3 = 0.0;

          for (int k = 1; k <= nenter; k++)
          {
            k1 = indx2[k];
            temp1 += ws[getIdx(k1,ipntr,n)]*wy[getIdx(k1,jpntr,n)];
          }
          for (int k = ileave; k <= n; k++)
          {
            k1 = indx2[k];
            temp3 += ws[getIdx(k1,ipntr,n)]*wy[getIdx(k1,jpntr,n)];
          }
          if (is <= jy + m)
            wn1[getIdx(is,jy,m2)] += temp1 - temp3;
          else
            wn1[getIdx(is,jy,m2)] += temp3 - temp1;

          jpntr = jpntr%m + 1;
        }
        ipntr = ipntr%m + 1;
      }
      //Form the upper triangle of WN = [D+Y' ZZ'Y/theta   -L_a'+R_z' ]
      //                                [-L_a +R_z        S'AA'S*theta]

      for (iy = 1; iy <= col; iy++)
      {
        is = col + iy;
        is1 = m + iy;
        for (jy = 1; jy <= iy; jy++)
        {
          js = col + jy;
          js1 = m + jy;
          wn[getIdx(jy,iy,m2)] = wn1[getIdx(iy,jy,m2)]/theta;
          wn[getIdx(js,is,m2)] = wn1[getIdx(is1,js1,m2)]*theta;
        }

        for (jy = 1; jy <= iy-1; jy++)
          wn[getIdx(jy,is,m2)] = -wn1[getIdx(is1,jy,m2)];

        for (jy = iy; jy <= col; jy++)
          wn[getIdx(jy,is,m2)] = wn1[getIdx(is1,jy,m2)];
        wn[getIdx(iy,iy,m2)] += sy[getIdx(iy,iy,m)];
      }

      //Form the upper triangle of WN= [  LL'            L^-1(-L_a'+R_z')]
      //                               [(-L_a +R_z)L'^-1   S'AA'S*theta  ]

      //first Cholesky factor (1,1) block of wn to get LL'
      //with L' stored in the upper triangle of wn.
      dpofa(wn,0,m2,col,info,false);
      if (info[0] != 0)
      {
        info[0] = -1;
        return;
      }

      //then form L^-1(-L_a'+R_z') in the (1,2) block.
      col2 = 2*col;
      for (js = col+1; js <= col2; js++)
      {
        dtrsl(wn,m2,col,wn,getIdx(1,js,m2)-1,11,info);
      }

      //Form S'AA'S*theta + (L^-1(-L_a'+R_z'))'L^-1(-L_a'+R_z') in the
      //upper triangle of (2,2) block of wn.
      for (is = col+1; is <= col2; is++)
        for (js = is; js <= col2; js++)
          wn[getIdx(is,js,m2)] += ddot(col,wn,getIdx(1,is,m2)-1,1,
                                           wn,getIdx(1,js,m2)-1,1);

      //Cholesky factorization of (2,2) block of wn.
      dpofa(wn,getIdx(col+1,col+1,m2)-1,m2,col,info,false);

      if (info[0] != 0)
      {
        info[0] = -2;
        return;
      }
    }

  private void formt(final int m, double[] wt, double[] sy, double[] ss, final int col, final double theta, int[] info)
  {
    int k1, idx;
    double ddum;

    //Form the upper half of  T = theta*SS + L*D^(-1)*L',
    //store T in the upper triangle of the array wt.
    for (int j = 1; j <= col; j++)
    {
      idx = getIdx(1,j,m);
      wt[idx] = theta*ss[idx];
    }

    for (int i = 2; i <= col; i++)
      for (int j = i; j <= col; j++)
      {
        k1 = Math.min(i,j) - 1;
        ddum = 0.0;
        for (int k = 1; k <= k1; k++)
          ddum  += sy[getIdx(i,k,m)]*sy[getIdx(j,k,m)]/sy[getIdx(k,k,m)];
        wt[getIdx(i,j,m)] = ddum + theta*ss[getIdx(i,j,m)];
      }

    //Cholesky factorize T to J*J' with
    //J' stored in the upper triangle of wt.

    dpofa(wt,0,m,col,info,false);
    if (info[0] != 0) info[0] = -3;
  }

  private void freev(final int n, int[] nfree, int[] index, int[] nenter, int[] ileave, int[] indx2, int[] iwhere,
                     boolean[] wrk, final boolean updatd, final boolean cnstnd, final int iprint, final int iter)
  {
    int iact,k;

    nenter[0] = 0;
    ileave[0] = n + 1;
    if (iter > 0 && cnstnd)
    {
      //count the entering and leaving variables.
      for (int i = 1; i <= nfree[0]; i++)
      {
        k = index[i];
        if (iwhere[k] > 0)
        {
          ileave[0] -= 1;
          indx2[ileave[0]] = k;
          if (iprint >= 100) System.out.println("Variable " + k + " leaves the set of free variables");
        }
      }

      for (int i = 1+nfree[0]; i <= n; i++)
      {
        k = index[i];
        if (iwhere[k] <= 0)
        {
          nenter[0] += 1;
          indx2[nenter[0]] = k;
          if (iprint >= 100) System.out.println("Variable " + k + " enters the set of free variables");
        }
      }
      if (iprint >= 99) System.out.println(n+1-ileave[0] + " variables leave; " + nenter[0] + " variables enter");
    }
    wrk[0] = (ileave[0] < n+1) || (nenter[0] > 0) || updatd;

    //Find the index set of free and active variables at the GCP.

    nfree[0] = 0;
    iact = n + 1;
    for (int i = 1; i <= n; i++)
    {
      if (iwhere[i] <= 0)
      {
        nfree[0] += 1;
        index[nfree[0]] = i;
      }
      else
      {
        iact -= 1;
        index[iact] = i;
      }
    }

    if (iprint >= 99) System.out.println(nfree[0] + " variables are free at GCP " + (iter + 1));
  }

  private void hpsolb(final int n, double[] t, int[] iorder, final int iheap)
  {
    int i,j,indxin,indxou;
    double ddum,out;

    if (iheap == 0)
    {
      //Rearrange the elements t[1] to t[n] to form a heap.
      for (int k = 2; k <= n; k++)
      {
        ddum  = t[k];
        indxin = iorder[k];

        //Add ddum to the heap.
        i = k;
        while (i>1)
        {
          j = i/2;
          if (ddum < t[j])
          {
            t[i] = t[j];
            iorder[i] = iorder[j];
            i = j;
          }
          else break;
        }
        t[i] = ddum;
        iorder[i] = indxin;
      }
    }

    //Assign to 'out' the value of t(1), the least member of the heap,
    //and rearrange the remaining members to form a heap as
    //elements 1 to n-1 of t.

    if (n > 1)
    {
      i = 1;
      out = t[1];
      indxou = iorder[1];
      ddum = t[n];
      indxin = iorder[n];

      //Restore the heap
      while(true)
      {
        j = i+i;
        if (j <= n-1)
        {
          if (t[j+1] < t[j]) j = j+1;
          if (t[j] < ddum )
          {
            t[i] = t[j];
            iorder[i] = iorder[j];
            i = j;
          }
          else break;
        }
        else break;
      }
      t[i] = ddum;
      iorder[i] = indxin;

      //Put the least member in t(n).

      t[n] = out;
      iorder[n] = indxou;
    }
  }

  private void lnsrlb(final int n, final double[] l, final double[] u, final int[] nbd, double[] x, final double f,
                      double[] fold, double[] gd, double[] gdold, double[] g, double[] d, double[] r, double[] t,
                      double[] z, double[] stp, double[] dnorm, double[] dtd, double[] xstep, double[] stpmx,
                      final int iter, int[] ifun, int[] iback, int[] nfgv, int[] info, String[] task,
                      final boolean boxed, final boolean cnstnd, String[] csave, int[] isave, double[] dsave)
  {
    double a1,a2;
    double big=1e10,ftol=1.0e-3,gtol=0.9,xtol=0.1;

    int goId = 0;
    while (goId >= 0)
    {

    if (goId == 0)
    {
      if (matchPrefix(task[0],"FG_LN")) { goId = 556; continue; }

      dtd[0] = ddot(n,d,0,1,d,0,1);
      dnorm[0] = Math.sqrt(dtd[0]);

      //Determine the maximum step length.

      stpmx[0] = big;
      if (cnstnd)
      {
        if (iter == 0)
        stpmx[0] = 1.0;
        else
        {
          for (int i = 1; i <= n; i++)
          {
            a1 = d[i];
            if (nbd[i] != 0)
            {
              if (a1 < 0.0 && nbd[i] <= 2)
              {
                a2 = l[i] - x[i];
                if (a2 >= 0.0)
                  stpmx[0] = 0.0;
                else if (a1*stpmx[0] < a2)
                  stpmx[0] = a2/a1;
              }
              else if (a1 > 0.0 && nbd[i] >= 2)
              {
                a2 = u[i] - x[i];
                if (a2 <= 0.0)
                  stpmx[0] = 0.0;
                else if (a1*stpmx[0] > a2)
                  stpmx[0] = a2/a1;
              }
            }
          } // for (int i = 1; i <= n; i++)
        }
      }

      if (iter == 0 && !boxed)
        stp[0] = Math.min(1.0 / dnorm[0], stpmx[0]);
      else
        stp[0] = 1.0;

      dcopy(n,x,0,1,t,0,1);
      dcopy(n,g,0,1,r,0,1);
      fold[0] = f;
      ifun[0] = 0;
      iback[0] = 0;
      csave[0] = "START";
      goId = 556; continue;
    }
    else if (goId == 556)
    {
      gd[0] = ddot(n,g,0,1,d,0,1);
      if (ifun[0] == 0)
      {
        gdold[0]=gd[0];
        if (gd[0] >= 0.0)
        {
          //the directional derivative >=0.
          //Line search is impossible.
          info[0] = -4;
          return;
        }
      }

      dcsrch(f,gd,stp,ftol,gtol,xtol,0.0,stpmx[0],csave,isave,dsave);

      xstep[0] = stp[0]*dnorm[0];
      if (!matchPrefix(csave[0],"CONV") && !matchPrefix(csave[0],"WARN"))
      {
        task[0] = "FG_LNSRCH";
        ifun[0] += 1;
        nfgv[0] += 1;
        iback[0] = ifun[0] - 1;
        if (stp[0] == 1.0)
          dcopy(n,z,0,1,x,0,1);
        else
        {
          for (int i = 1; i <= n; i++)
            x[i] = stp[0]*d[i] + t[i];
        }
      }
      else
        task[0] = "NEW_X";
      break;
    }
    else
    {
      System.out.println("ERROR: in lnsrlb(). Unknown goId " + goId);
      System.exit(-1);
    }

    }//while (goId >= 0)
  }

  private void matupd(final int n, final int m, double[] ws, double[] wy, double[] sy, double[] ss,
                      double[] d, double[] r, int[] itail, final int iupdat, int[] col, int[] head, double[] theta,
                      final double rr, final double dr, final double stp, final double dtd)
  {
    int pointr;
    int idx;

    //Set pointers for matrices WS and WY.
    if (iupdat <= m)
    {
      col[0] = iupdat;
      itail[0] = (head[0]+iupdat-2)%m + 1;
    }
    else
    {
      itail[0] = itail[0]%m + 1;
      head[0] = head[0]%m + 1;
    }

    //Update matrices WS and WY.
    idx = getIdx(1,itail[0],n)-1;
    dcopy(n,d,0,1,ws,idx,1);
    dcopy(n,r,0,1,wy,idx,1);

    //Set theta=yy/ys.
    theta[0] = rr/dr;

    //Form the middle matrix in B.
    //update the upper triangle of SS,
    //and the lower triangle of SY:
    if (iupdat > m)
    {
      //move old information
      for (int j = 1; j <= col[0]-1; j++)
      {
        dcopy(j,ss,getIdx(2,j+1,m)-1,1,ss,getIdx(1,j,m)-1,1);
        dcopy(col[0]-j,sy,getIdx(j+1,j+1,m)-1,1,sy,getIdx(j,j,m)-1,1);
      }
    }

    //add new information: the last row of SY
    //and the last column of SS:
    pointr = head[0];
    for (int j = 1; j <= col[0]-1; j++)
    {
      idx = getIdx(1,pointr,n)-1;
      sy[getIdx(col[0],j,m)] = ddot(n,d,0,1,wy,idx,1);
      ss[getIdx(j,col[0],m)] = ddot(n,ws,idx,1,d,0,1);
      pointr = pointr%m + 1;
    }

    idx = getIdx(col[0],col[0],m);
    if (stp == 1.0)
      ss[idx] = dtd;
    else
      ss[idx] = stp*stp*dtd;

    sy[idx] = dr;
  }

  private void prn1lb(final int n, final int m, final double[] l,final double[] u, final double[] x, final int iprint,
                      Writer itfile, final double epsmch)
  {
    if (iprint >= 0)
    {
      System.out.println("RUNNING THE L-BFGS-B CODE, \n"
                         + "epsmch = machine precision\n"
                         + "it    = iteration number\n"
                         + "nf    = number of function evaluations\n"
                         + "nint  = number of segments explored during the Cauchy search\n"
                         + "nact  = number of active bounds at the generalized Cauchy point\n"
                         + "sub   = manner in which the subspace minimization terminated:\n"
                         +"con = converged, bnd = a bound was reached\n"
                         + "itls  = number of iterations performed in the line search\n"
                         + "stepl = step length used\n"
                         + "tstep = norm of the displacement (total step)\n"
                         + "projg = norm of the projected gradient\n"
                         + "f     = function[0]\n"
                         + "Machine precision = " + epsmch);
      System.out.println("N = " + n + ",    M = " + m );
      if (iprint >= 1)
      {
        write(itfile,"RUNNING THE L-BFGS-B CODE, \n"
                     + "epsmch = machine precision\n"
                     + "it    = iteration number\n"
                     + "nf    = number of function evaluations\n"
                     + "nint  = number of segments explored during the Cauchy search\n"
                     + "nact  = number of active bounds at the generalized Cauchy point\n"
                     + "sub   = manner in which the subspace minimization terminated:\n"
                     +"con = converged, bnd = a bound was reached\n"
                     + "itls  = number of iterations performed in the line search\n"
                     + "stepl = step length used\n"
                     + "tstep = norm of the displacement (total step)\n"
                     + "projg = norm of the projected gradient\n"
                     + "f     = function[0]\n"
                     + "Machine precision = " + epsmch + "\n");
        write(itfile, "N = " + n + ",    M = " + m + "\n");
        write(itfile,"   it   nf  nint  nact  sub  itls  stepl    tstep     projg        f\n");
        if (iprint > 100)
        {
          System.out.println("L = ");
          for (int i = 1; i <= n; i++) System.out.print(l[i] + " ");
          System.out.println();
          System.out.println("X0 = ");
          for (int i = 1; i <= n; i++) System.out.print(x[i] + " ");
          System.out.println();
          System.out.println("U = ");
          for (int i = 1; i <= n; i++) System.out.print(u[i] + " ");
          System.out.println();
        }
      }
    }
  }

  private void prn2lb(final int n, final double[] x, final double f, final double[] g, final int iprint,
                      Writer itfile, final int iter, final int nfgv, final int nact, final double sbgnrm, final int nint,
                      String word, final int iword, final int iback, final double stp, final double xstep)
  {
    int imod;

    //'word' records the status of subspace solutions.
    if (iword == 0)
    {
      //the subspace minimization converged.
      word = "con";
    }
    else
    if (iword == 1)
    {
      //the subspace minimization stopped at a bound.
      word = "bnd";
    }
    else
    if (iword == 5)
    {
      //the truncated Newton step has been used.
      word = "TNT";

    }
    else
      word = "---";

    if (iprint >= 99)
    {
      System.out.println("LINE SEARCH " + iback + " times; norm of step = " + xstep);
      System.out.println("At iterate " + iter + " f=" + f + "  |proj g|=" + sbgnrm);
      if (iprint > 100)
      {
        System.out.println("X = ");
        for (int i = 1; i <= n; i++) System.out.print(x[i] + " ");
        System.out.println();
        System.out.println("G = ");
        for (int i = 1; i <= n; i++) System.out.println(g[i] + " ");
        System.out.println();
      }
    }
    else
    if (iprint > 0)
    {
      imod = iter%iprint;
      if (imod == 0) System.out.println("At iterate " + iter + " f="+f+ "  |proj g|="+sbgnrm);
    }
    if (iprint >= 1) write(itfile, " " + iter + " " + nfgv + " " + nint + " " + nact + " " + word + " " + iback + " " +
                                   stp + " " + xstep + " " + sbgnrm + " " + f + "\n");
  }

  private void prn3lb(final int n, final double[] x, double[] f, final String[] task, final int iprint,
                      final int info, final int k, Writer itfile, final int iter, final int nfgv, final int nintol,
                      final int nskip, final int nact, final double sbgnrm, final double time, final int nint,
                      final int iback, final double stp, final double xstep, final double cachyt, final double sbtime,
                      final double lnscht)
  {
    if (!matchPrefix(task[0],"ERROR"))
    {
      if (iprint >= 0)
      {
        System.out.println("           * * *"
                           + "Tit   = total number of iterations\n"
                           + "Tnf   = total number of function evaluations\n"
                           + "Tnint = total number of segments explored during"
                           + " Cauchy searches\n"
                           + "Skip  = number of BFGS updates skipped\n"
                           + "Nact  = number of active bounds at final generalized"
                           + " Cauchy point\n"
                           + "Projg = norm of the final projected gradient\n"
                           + "F     = final function[0]\n"
                           + "           * * *");

        System.out.println("   N   Tit  Tnf  Tnint  Skip  Nact     Projg        F");
        System.out.println(" " + n + " " + iter + " " + nfgv + " " + nintol + " " + nskip + " " + nact + " " + sbgnrm
                           + " " + f);
        if (iprint >= 100)
        {
          System.out.print("X = ");
          for (int i = 1; i <= n; i++)
            System.out.println( x[i] + " ");
          System.out.println();
        }
        if (iprint >= 1) System.out.println(" F = " + f);
      }
    }

    if (iprint >= 0)
    {
      System.out.println(task[0]);
      if (info != 0)
      {
        if      (info == -1) System.out.println("Matrix in 1st Cholesky factorization in formk is not Pos. Def.");
        else if (info == -2) System.out.println("Matrix in 2st Cholesky factorization in formk is not Pos. Def.");
        else if (info == -3) System.out.println("Matrix in the Cholesky factorization in formt is not Pos. Def.");
        else if (info == -4) System.out.println("Derivative >= 0, backtracking line search impossible.\n" +
                                                "Previous x, f and g restored.\n" +
                                                "Possible causes: 1 error in function or gradient evaluation;\n" +
                                                "2 rounding errors dominate computation.\n");
        else if (info == -5) System.out.println("Warning:  more than 10 function and gradient\n" +
                                                "evaluations in the last line search.  Termination" +
                                                "may possibly be caused by a bad search direction.");
        else if (info == -6) System.out.println(" Input nbd(" + k + ") is invalid.");
        else if (info == -7) System.out.println(" l(" + k + ") > u(" + k + ").  No feasible solution.");
        else if (info == -8) System.out.println("The triangular system is singular");
        else if (info == -9) System.out.println(" Line search cannot locate an adequate point after\n"+
                                                " 20 function and gradient evaluations. Previous x, f and g restored.\n"+
                                                "Possible causes: 1 error in function or gradient evaluation; 2 rounding error dominate computation");
      }

      if (iprint >= 1) System.out.println("Cauchy                time " + cachyt + " seconds.\n" +
                                          "Subspace minimization time " + sbtime + " seconds.\n" +
                                          "Line search           time " + lnscht + " seconds.");

      System.out.println("Total User time " + time + " seconds.");

      if (iprint >= 1)
      {
        if (info == -4 || info == -9)
        {
          write(itfile, " " + n + " " + iter + " " + nfgv + " " + nintol + " " + nskip + " " + nact + " " + sbgnrm + " " + f[0] + "\n");
        }
        write(itfile, task[0] + "\n");
        if (info != 0)
        {
          if      (info == -1) write(itfile,"Matrix in 1st Cholesky factorization in formk is not Pos. Def.\n");
          else if (info == -2) write(itfile,"Matrix in 2st Cholesky factorization in formk is not Pos. Def.\n");
          else if (info == -3) write(itfile,"Matrix in the Cholesky factorization in formt is not Pos. Def.\n");
          else if (info == -4) write(itfile,"Derivative >= 0, backtracking line search impossible.\n" +
                                            "Previous x, f and g restored.\n" +
                                            "Possible causes: 1 error in function or gradient evaluation;\n" +
                                            "2 rounding errors dominate computation.\n\n");
          else if (info == -5) write(itfile,"Warning:  more than 10 function and gradient\n" +
                                                  "evaluations in the last line search.  Termination" +
                                                  "may possibly be caused by a bad search direction.\n");
          else if (info == -6) write(itfile," Input nbd(" + k + ") is invalid.\n");
          else if (info == -7) write(itfile," l(" + k + ") > u(" + k + ").  No feasible solution.\n");
          else if (info == -8) write(itfile,"The triangular system is singular\n");
          else if (info == -9) write(itfile," Line search cannot locate an adequate point after\n"+
                                            " 20 function and gradient evaluations. Previous x, f and g restored.\n"+
                                            "Possible causes: 1 error in function or gradient evaluation; 2 rounding error dominate computation\n");
        }
        write(itfile,"Total User time " + time + " nanoseconds\n");
      }
    }
  }

  private double projgr(final int n, final double[] l, final double[] u, final int[] nbd, final double[] x, final double[] g)
  {
    double gi;
    double sbgnrm = 0.0;
    for (int i = 1; i <= n; i++)
    {
      gi = g[i];
      if (nbd[i] != 0)
      {
        if (gi < 0.0)
        {
          if (nbd[i] >= 2) gi = Math.max((x[i] - u[i]), gi);
        }
        else
        {
          if (nbd[i] <= 2) gi = Math.min((x[i] - l[i]), gi);
        }
      }
      sbgnrm = Math.max(sbgnrm, Math.abs(gi));
    }
    return sbgnrm;
  }

  private void subsm(final int n, final int m, final int nsub, final int[] ind, final double[] l, final double[] u,
                     final int[] nbd, double[] x, double[] d, double[] ws, double[] wy, final double theta,
                     final int col, final int head, int[] iword, double[] wv, double[] wn, final int iprint, int[] info)
  {
    //int jy,i,j;
    int pointr,m2,col2,ibd=0,js,k;
    double alpha,dk,temp1,temp2;
    int idx;

    if (nsub <= 0) return;
    if (iprint >= 99) System.out.println("----------------SUBSM entered-----------------");

    //Compute wv = W'Zd.

    pointr = head;
    for (int i = 1; i <= col; i++)
    {
      temp1 = 0.0;
      temp2 = 0.0;
      for (int j = 1; j <= nsub; j++)
      {
        k = ind[j];
        idx = getIdx(k,pointr,n);
        temp1 += wy[idx]*d[j];
        temp2 += ws[idx]*d[j];
      }
      wv[i] = temp1;
      wv[col + i] = theta*temp2;
      pointr = pointr%m + 1;
    }

    //Compute wv:=K^(-1)wv.

    m2 = 2*m;
    col2 = 2*col;
    dtrsl(wn,m2,col2,wv,0,11,info);

    if (info[0] != 0) return;
    for (int i = 1; i <= col; i++)
      wv[i] = -wv[i];
    dtrsl(wn,m2,col2,wv,0,01,info);

    if (info[0] != 0) return;

    //Compute d = (1/theta)d + (1/theta**2)Z'W wv.

    pointr = head;
    for (int jy = 1; jy <= col; jy++)
    {
      js = col + jy;
      for (int i = 1; i <= nsub; i++)
      {
        k = ind[i];
        idx = getIdx(k,pointr,n);
        d[i] += wy[idx]*wv[jy]/theta + ws[idx]*wv[js];
      }
      pointr = pointr%m + 1;
    }

    for (int i = 1; i <= nsub; i++)
      d[i] /= theta;

    //Backtrack to the feasible region.

    alpha = 1.0;
    temp1 = alpha;

    for (int i = 1; i <= nsub; i++)
    {
      k = ind[i];
      dk = d[i];
      if (nbd[k] != 0)
      {
   	    if (dk < 0.0 && nbd[k] <= 2)
        {
	        temp2 = l[k] - x[k];
	        if (temp2 >= 0.0)
   		      temp1 = 0.0;
	        else
          if (dk*alpha < temp2)
		        temp1 = temp2/dk;
        }
   	    else
        if (dk > 0.0 && nbd[k] >= 2)
        {
	        temp2 = u[k] - x[k];
	        if (temp2 <= 0.0)
		        temp1 = 0.0;
          else
          if (dk*alpha > temp2)
		        temp1 = temp2/dk;
        }
        if (temp1 < alpha)
        {
	        alpha = temp1;
	        ibd = i;
        }
      }
    }

    if (alpha < 1.0)
    {
      dk = d[ibd];
      k = ind[ibd];
      if (dk > 0.0)
      {
        x[k] = u[k];
        d[ibd] = 0.0;
      }
      else
      if (dk < 0.0)
      {
        x[k] = l[k];
        d[ibd] = 0.0;
      }
    }

    for (int i = 1; i <= nsub; i++)
    {
      k = ind[i];
      x[k] += alpha*d[i];
    }

    if (iprint >= 99)
    {
      if (alpha < 1.0) System.out.println("ALPHA = " + alpha + " backtrack to the BOX");
      else             System.out.println("SM solution inside the box");

      if (iprint >100)
      {
        System.out.print("Subspace solution X = ");
        for (int i = 1; i <= n; i++) System.out.print(x[i]);
        System.out.println();
      }
    }

    if (alpha < 1.0)
      iword[0] = 1;
    else
      iword[0] = 0;

    if (iprint >= 99) System.out.println("----------------exit SUBSM --------------------");
  }

  private void dcsrch(final double f, double[] g, double[] stp, final double ftol, final double gtol, final double xtol,
                      final double stpmin, final double stpmax, String[] task, int[] isave, double[] dsave)
  {
    double xtrapl=1.1,xtrapu=4.0;

    boolean[] brackt = new boolean[1];
    int stage;
    double finit,ftest,fm,ginit,gtest,gm,stmin,stmax,width,width1;
    double[] stx = new double[1];
    double[] sty = new double[1];
    double[] fxm = new double[1];
    double[] fym = new double[1];
    double[] gxm = new double[1];
    double[] gym = new double[1];
    double[] fx  = new double[1];
    double[] fy  = new double[1];
    double[] gx  = new double[1];
    double[] gy  = new double[1];

    //Initialization block.

    if (matchPrefix(task[0],"START"))
    {
      //Check the input arguments for errors.

      if (stp[0] < stpmin)    task[0] = "ERROR: STP < STPMIN";
      if (stp[0] > stpmax)    task[0] = "ERROR: STP > STPMAX";
      if (g[0] > 0.0)         task[0] = "ERROR: INITIAL G >ZERO";
      if (ftol < 0.0)         task[0] = "ERROR: FTOL < ZERO";
      if (gtol < 0.0)         task[0] = "ERROR: GTOL < ZERO";
      if (xtol < 0.0)         task[0] = "ERROR: XTOL < ZERO";
      if (stpmin < 0.0)       task[0] = "ERROR: STPMIN < ZERO";
      if (stpmax < stpmin)    task[0] = "ERROR: STPMAX < STPMIN";

      //Exit if there are errors on input.
      if (matchPrefix(task[0],"ERROR")) return;

      //Initialize local variables.

      brackt[0] = false;
      stage = 1;
      finit = f;
      ginit = g[0];
      gtest = ftol*ginit;
      width = stpmax - stpmin;
      width1 = width/0.5;

      //The variables stx, fx, gx contain the values of the step,
      //function, and derivative at the best step.
      //The variables sty, fy, gy contain the value of the step,
      //function, and derivative at sty.
      //The variables stp, f, g contain the values of the step,
      //function, and derivative at stp.

      stx[0]  = 0.0;
      fx[0]   = finit;
      gx[0]   = ginit;
      sty[0]  = 0.0;
      fy[0]   = finit;
      gy[0]   =  ginit;
      stmin   = 0.0;
      stmax   = stp[0] + xtrapu*stp[0];
      task[0] = "FG";

      //Save local variables.

      if (brackt[0])
        isave[1]  = 1;
      else
        isave[1]  = 0;

      isave[2]  = stage;
      dsave[1]  = ginit;
      dsave[2]  = gtest;
      dsave[3]  = gx[0];
      dsave[4]  = gy[0];
      dsave[5]  = finit;
      dsave[6]  = fx[0];
      dsave[7]  = fy[0];
      dsave[8]  = stx[0];
      dsave[9]  = sty[0];
      dsave[10] = stmin;
      dsave[11] = stmax;
      dsave[12] = width;
      dsave[13] = width1;

      return;
    }
    else
    {
      //Restore local variables.

      if (isave[1] == 1)
        brackt[0] = true;
      else
        brackt[0] = false;

      stage  = isave[2];
      ginit  = dsave[1];
      gtest  = dsave[2];
      gx[0]  = dsave[3];
      gy[0]  = dsave[4];
      finit  = dsave[5];
      fx[0]  = dsave[6];
      fy[0]  = dsave[7];
      stx[0] = dsave[8];
      sty[0] = dsave[9];
      stmin  = dsave[10];
      stmax  = dsave[11];
      width  = dsave[12];
      width1 = dsave[13];
    }

    //If psi(stp) <= 0 and f'(stp) >= 0 for some step, then the
    //algorithm enters the second stage.

    ftest = finit + stp[0]*gtest;
    if (stage == 1 && f <= ftest && g[0]>= 0.0) stage = 2;

    //Test for warnings.

    if (brackt[0] && (stp[0] <= stmin || stp[0] >= stmax))   task[0] = "WARNING: ROUNDING ERRORS PREVENT PROGRESS";
    if (brackt[0] && stmax - stmin <= xtol*stmax)            task[0] = "WARNING: XTOL TEST SATISFIED";
    if (stp[0] == stpmax && f <= ftest && g[0]<= gtest)   task[0] = "WARNING: STP = STPMAX";
    if (stp[0] == stpmin && (f > ftest || g[0] >= gtest)) task[0] = "WARNING: STP = STPMIN";

    //Test for convergence.

    if (f <= ftest && Math.abs(g[0]) <= gtol*(-ginit))
      task[0] = "CONVERGENCE";

    //Test for termination.

    if (matchPrefix(task[0],"WARN") || matchPrefix(task[0],"CONV"))
    {
      //Save local variables.

      if (brackt[0])
        isave[1]  = 1;
      else
        isave[1]  = 0;

      isave[2]  = stage;
      dsave[1]  = ginit;
      dsave[2]  = gtest;
      dsave[3]  = gx[0];
      dsave[4]  = gy[0];
      dsave[5]  = finit;
      dsave[6]  = fx[0];
      dsave[7]  = fy[0];
      dsave[8]  = stx[0];
      dsave[9]  = sty[0];
      dsave[10] = stmin;
      dsave[11] = stmax;
      dsave[12] = width;
      dsave[13] = width1;
      return;
    }

    //A modified function is used to predict the step during the
    //first stage if a lower function[0] has been obtained but
    //the decrease is not sufficient.

    if (stage == 1 && f <= fx[0] && f > ftest)
    {
      //Define the modified function and derivative[0]s.

      fm = f - stp[0]*gtest;
      fxm[0] = fx[0] - stx[0]*gtest;
      fym[0] = fy[0] - sty[0]*gtest;
      gm = g[0] - gtest;
      gxm[0] = gx[0] - gtest;
      gym[0] = gy[0] - gtest;

      //Call dcstep to update stx, sty, and to compute the new step.

      dcstep(stx,fxm,gxm,sty,fym,gym,stp,fm,gm,brackt,stmin,stmax);

      //Reset the function and derivative[0]s for f.

      fx[0] = fxm[0] + stx[0]*gtest;
      fy[0] = fym[0] + sty[0]*gtest;
      gx[0] = gxm[0] + gtest;
      gy[0] = gym[0] + gtest;
    }
    else
    {
      //Call dcstep to update stx, sty, and to compute the new step.

      dcstep(stx,fx,gx,sty,fy,gy,stp,f,g[0],brackt,stmin,stmax);
    }

    //Decide if a bisection step is needed.

    if (brackt[0])
    {
      if (Math.abs(sty[0]-stx[0]) >= 0.66*width1) stp[0] = stx[0] + 0.5*(sty[0] - stx[0]);
      width1 = width;
      width = Math.abs(sty[0]-stx[0]);
    }

    //Set the minimum and maximum steps allowed for stp.

    if (brackt[0])
    {
      stmin = Math.min(stx[0],sty[0]);
      stmax = Math.max(stx[0],sty[0]);
    }
    else
    {
      stmin = stp[0] + xtrapl*(stp[0] - stx[0]);
      stmax = stp[0] + xtrapu*(stp[0] - stx[0]);
    }

    //Force the step to be within the bounds stpmax and stpmin.

    stp[0] = Math.max(stp[0],stpmin);
    stp[0] = Math.min(stp[0],stpmax);

    //If further progress is not possible, let stp be the best
    //point obtained during the search.

    if (brackt[0] && (stp[0] <= stmin || stp[0] >= stmax) || (brackt[0] && stmax-stmin <= xtol*stmax))
      stp[0] = stx[0];

    //Obtain another function and derivative.
    task[0] = "FG";

    //Save local variables.

    if (brackt[0])
      isave[1]  = 1;
    else
      isave[1]  = 0;

    isave[2]  = stage;
    dsave[1]  = ginit;
    dsave[2]  = gtest;
    dsave[3]  = gx[0];
    dsave[4]  = gy[0];
    dsave[5]  = finit;
    dsave[6]  = fx[0];
    dsave[7]  = fy[0];
    dsave[8]  = stx[0];
    dsave[9]  = sty[0];
    dsave[10] = stmin;
    dsave[11] = stmax;
    dsave[12] = width;
    dsave[13] = width1;
  }

  private void dcstep(double[] stx, double[] fx, double[] dx, double[] sty, double[] fy, double[] dy, double[] stp,
                      final double fp, final double dp, boolean[] brackt, final double stpmin, final double stpmax)
  {
      double gamma,p,q,r,s,sgnd,stpc,stpf,stpq,theta;

      sgnd = dp*(dx[0]/Math.abs(dx[0]));

      //First case: A higher function[0]. The minimum is bracketed.
      //If the cubic step is closer to stx than the quadratic step, the
      //cubic step is taken, otherwise the average of the cubic and
      //quadratic steps is taken.

      if (fp > fx[0])
      {
        theta = 3.0*(fx[0] - fp)/(stp[0] - stx[0]) + dx[0] + dp;
        s = max(Math.abs(theta),Math.abs(dx[0]),Math.abs(dp));
        gamma = s*Math.sqrt( (theta/s)*(theta/s) - (dx[0]/s)*(dp/s) );
        if (stp[0] < stx[0]) gamma = -gamma;
        p = (gamma - dx[0]) + theta;
        q = ((gamma - dx[0]) + gamma) + dp;
        r = p/q;
        stpc = stx[0] + r*(stp[0] - stx[0]);
        stpq = stx[0] + ((dx[0]/((fx[0] - fp)/(stp[0] - stx[0]) + dx[0]))/2.0)*(stp[0] - stx[0]);
        if (Math.abs(stpc-stx[0]) < Math.abs(stpq-stx[0]))
          stpf = stpc;
        else
          stpf = stpc + (stpq - stpc)/2.0;
        brackt[0] = true;
      }

      //Second case: A lower function[0] and derivatives of opposite
      //sign. The minimum is bracketed. If the cubic step is farther from
      //stp than the secant step, the cubic step is taken, otherwise the
      //secant step is taken.

      else
      if (sgnd < 0.0)
      {
        theta = 3.0*(fx[0] - fp)/(stp[0] - stx[0]) + dx[0] + dp;
        s = max(Math.abs(theta),Math.abs(dx[0]),Math.abs(dp));
        gamma = s*Math.sqrt( (theta/s)*(theta/s) - (dx[0]/s)*(dp/s) );
        if (stp[0] > stx[0]) gamma = -gamma;
        p = (gamma - dp) + theta;
        q = ((gamma - dp) + gamma) + dx[0];
        r = p/q;
        stpc = stp[0] + r*(stx[0] - stp[0]);
        stpq = stp[0] + (dp/(dp - dx[0]))*(stx[0] - stp[0]);
        if (Math.abs(stpc-stp[0]) > Math.abs(stpq-stp[0]))
          stpf = stpc;
        else
          stpf = stpq;
        brackt[0] = true;
      }

      //Third case: A lower function[0], derivatives of the same sign,
      //and the magnitude of the derivative decreases.

      else
      if (Math.abs(dp) < Math.abs(dx[0]))
      {
        //The cubic step is computed only if the cubic tends to infinity
        //in the direction of the step or if the minimum of the cubic
        //is beyond stp. Otherwise the cubic step is defined to be the
        //secant step.

        theta = 3.0*(fx[0] - fp)/(stp[0] - stx[0]) + dx[0] + dp;
        s = max(Math.abs(theta),Math.abs(dx[0]),Math.abs(dp));

        //The case gamma = 0 only arises if the cubic does not tend
        //to infinity in the direction of the step.

        gamma = s*Math.sqrt(Math.max(0.0, (theta/s)*(theta/s)-(dx[0]/s)*(dp/s)));
        if (stp[0] > stx[0]) gamma = -gamma;
        p = (gamma - dp) + theta;
        q = (gamma + (dx[0] - dp)) + gamma;
        r = p/q;
        if (r < 0.0 && gamma != 0.0)
          stpc = stp[0] + r*(stx[0] - stp[0]);
        else
        if (stp[0] > stx[0])
          stpc = stpmax;
        else
          stpc = stpmin;

        stpq = stp[0] + (dp/(dp - dx[0]))*(stx[0] - stp[0]);

        if (brackt[0])
        {
          //A minimizer has been bracketed. If the cubic step is
          //closer to stp than the secant step, the cubic step is
          //taken, otherwise the secant step is taken.

          if (Math.abs(stpc-stp[0]) < Math.abs(stpq-stp[0]))
            stpf = stpc;
          else
            stpf = stpq;

          if (stp[0] > stx[0])
            stpf = Math.min(stp[0]+0.66*(sty[0]-stp[0]),stpf);
          else
            stpf = Math.max(stp[0]+0.66*(sty[0]-stp[0]),stpf);
        }
        else
        {
          //A minimizer has not been bracketed. If the cubic step is
          //farther from stp than the secant step, the cubic step is
          //taken, otherwise the secant step is taken.

          if (Math.abs(stpc-stp[0]) > Math.abs(stpq-stp[0]))
            stpf = stpc;
          else
            stpf = stpq;

          stpf = Math.min(stpmax,stpf);
          stpf = Math.max(stpmin,stpf);
        }
      }
      //Fourth case: A lower function[0], derivatives of the same sign,
      //and the magnitude of the derivative does not decrease. If the
      //minimum is not bracketed, the step is either stpmin or stpmax,
      //otherwise the cubic step is taken.
      else
      {
        if (brackt[0])
        {
          theta = 3.0*(fp - fy[0])/(sty[0] - stp[0]) + dy[0] + dp;
          s = max(Math.abs(theta),Math.abs(dy[0]),Math.abs(dp));
          gamma = s*Math.sqrt((theta/s)*(theta/s) - (dy[0]/s)*(dp/s));
          if (stp[0] > sty[0]) gamma = -gamma;
          p = (gamma - dp) + theta;
          q = ((gamma - dp) + gamma) + dy[0];
          r = p/q;
          stpc = stp[0] + r*(sty[0] - stp[0]);
          stpf = stpc;
        }
        else
        if (stp[0] > stx[0])
          stpf = stpmax;
        else
          stpf = stpmin;
      }

      //Update the interval which contains a minimizer.

      if (fp > fx[0])
      {
        sty[0] = stp[0];
        fy[0] = fp;
        dy[0] = dp;
      }
      else
      {
        if (sgnd < 0.0)
        {
          sty[0] = stx[0];
          fy[0] = fx[0];
          dy[0] = dx[0];
        }
        stx[0] = stp[0];
        fx[0] = fp;
        dx[0] = dp;
      }

      //Compute the new step.

      stp[0] = stpf;
    }

  private double dnrm2(final int n, final double[] x, final int incx)
  {
    double scale = 0.0;
    double ddnrm2 = 0.0;

    for (int i = 1; i <= n; i+=incx)
      scale = Math.max(scale, Math.abs(x[i]));

    if (scale == 0.0) return ddnrm2;

    for (int i = 1; i <= n; i+=incx)
      ddnrm2 += (x[i]/scale)*(x[i]/scale);

    ddnrm2 = scale*Math.sqrt(ddnrm2);
    return ddnrm2;
  }

  private double dpmeps()
  {
    long ibeta,irnd,it,itemp,negep;
    double a,b,beta,betain,betah,temp,tempa,temp1;
    double ddpmeps;

    //determine ibeta, beta ala malcolm.

    a = 1.0;
    b = 1.0;

    while(true)
    {
      a = a + a;
      temp = a + 1.0;
      temp1 = temp - a;
      if (temp1 - 1.0 != 0.0) break;
    }

    while(true)
    {
      b = b + b;
      temp = a + b;
      itemp = (long)(temp - a);
      if (itemp == 0) continue;
      ibeta = itemp;
      beta = (double)ibeta;

      //determine it, irnd.

      it = 0;
      b = 1.0;
      break;
    }

    while(true)
    {
      it += 1;
      b *= beta;
      temp = b + 1.0;
      temp1 = temp - b;
      if (temp1 - 1.0 == 0.0) continue;
      irnd = 0;
      betah = beta/2.0;
      temp = a + betah;
      if (temp - a != 0.0) irnd = 1;
      tempa = a + beta;
      temp = tempa + betah;
      if ((irnd == 0) && (temp - tempa != 0.0)) irnd = 2;

      //determine ddpmeps.

      negep = it + 3;
      betain = 1.0/beta;
      a = 1.0;
      for (int i = 1; i <= negep; i++)
        a *= betain;
      break;
    }

    while(true)
    {
      temp = 1.0 + a;
      if (temp - 1.0 != 0.0)
      {
        ddpmeps = a;
        if ((ibeta == 2) || (irnd == 0)) return ddpmeps;
        a = (a*(1.0 + a))/2.0;
        temp = 1.0 + a;
        if (temp - 1.0 != 0.0) ddpmeps = a;
        return ddpmeps;
      }
      a *= beta;
    }
  }

  private void daxpy(final int n, final double da, final double[] dx, final int dxOffset, final int incx,
                                                         double[] dy, final int dyOffset, final int incy)
  {
    //int i;
    int ix=0,iy=0,m=0,mp1=0;
    if (n <= 0) return;
    if (da == 0.0) return;

    int goId = 0;
    while (goId >= 0)
    {

    if (goId == 0)
    {
      if(incx==1 && incy==1) { goId = 20; continue; }

      //code for unequal increments or equal increments
      //not equal to 1

      ix = 1;
      iy = 1;
      if(incx < 0) ix = (-n+1)*incx + 1;
      if(incy < 0) iy = (-n+1)*incy + 1;

      for (int i = 1; i <= n; i++)
      {
        dy[iy+dyOffset] += da*dx[ix+dxOffset];
        ix += incx;
        iy += incy;
      }
      return;

      //code for both increments equal to 1
      //clean-up loop
      //goId = 20; continue;
    }
    else if (goId == 20)
    {
      m = n%4;
      if (m == 0) { goId = 40; continue; }

      for (int i = 1; i <= m; i++)
        dy[i+dyOffset] += da*dx[i+dxOffset];

      if (n < 4) return;
      goId = 40; continue;
    }
    else if (goId == 40)
    {
      mp1 = m + 1;

      for (int i = mp1; i <= n; i+=4)
      {
        dy[i+dyOffset] += da*dx[i+dxOffset];
        dy[i + 1+dyOffset] += da*dx[i + 1+dxOffset];
        dy[i + 2+dyOffset] += da*dx[i + 2+dxOffset];
        dy[i + 3+dyOffset] += da*dx[i + 3+dxOffset];
      }
      break;
    }
    else
    {
      System.out.println("ERROR: in daxpy(). Unknown goId " + goId);
      System.exit(-1);
    }
    }//while(true)
  }

  private void dcopy(final int n, final double[] dx, final int dxOffset, final int incx,
                                        double[] dy, final int dyOffset, final int incy)
  {
    int ix=0,iy=0,m=0,mp1=0;

    if (n <= 0) return;

    int goId = 0;
    while (goId >= 0)
    {

    if (goId == 0)
    {
      if(incx==1 && incy==1) { goId = 20; continue; }

      //code for unequal increments or equal increments
      //not equal to 1

      ix = 1;
      iy = 1;
      if (incx < 0) ix = (-n+1)*incx + 1;
      if (incy < 0) iy = (-n+1)*incy + 1;
      for (int i = 1; i <= n; i++)
      {
        dy[iy+dyOffset] = dx[ix+dxOffset];
        ix += incx;
        iy += incy;
      }
      return;

      //code for both increments equal to 1

      //clean-up loop
      //goId = 20; continue;
    }
    else if (goId == 20)
    {
      m = n%7;
      if (m == 0) { goId = 40; continue; }
      for (int i = 1; i <= m; i++)
        dy[i+dyOffset] = dx[i+dxOffset];

      if (n < 7) return;
      goId = 40; continue;
    }
    else if (goId == 40)
    {
      mp1 = m + 1;
      for (int i = mp1; i <= n; i+=7)
      {
        dy[i+dyOffset] = dx[i+dxOffset];
        dy[i + 1+dyOffset] = dx[i + 1+dxOffset];
        dy[i + 2+dyOffset] = dx[i + 2+dxOffset];
        dy[i + 3+dyOffset] = dx[i + 3+dxOffset];
        dy[i + 4+dyOffset] = dx[i + 4+dxOffset];
        dy[i + 5+dyOffset] = dx[i + 5+dxOffset];
        dy[i + 6+dyOffset] = dx[i + 6+dxOffset];
      }
      break;
    }
    }//while(goId>=0)
  }

  private double ddot(final int n, final double[] dx, final int dxOffset, final int incx,
                                   final double[] dy, final int dyOffset, final int incy)
  {
    double dtemp=0.0;
    int ix=0,iy=0,m=0,mp1=0;

    double dddot = 0.0;
    dtemp = 0.0;
    if (n <= 0) return dddot;

    int goId = 0;
    while (goId >= 0)
    {

    if (goId == 0)
    {
      if (incx==1 && incy==1) { goId = 20; continue; }

      //code for unequal increments or equal increments
      //not equal to 1

      ix = 1;
      iy = 1;
      if (incx < 0) ix = (-n+1)*incx + 1;
      if (incy < 0) iy = (-n+1)*incy + 1;

      for (int i = 1; i <= n; i++)
      {
        dtemp += dx[ix+dxOffset]*dy[iy+dyOffset];
        ix += incx;
        iy += incy;
      }
      dddot = dtemp;
      return dddot;

      //code for both increments equal to 1

      //clean-up loop
      //goId = 20; continue;
    }
    else if (goId == 20)
    {
      m = n%5;
      if (m == 0) { goId = 40; continue; }

      for (int i = 1; i <= m; i++)
        dtemp += dx[i+dxOffset]*dy[i+dyOffset];

      if( n < 5 ) { goId = 60; continue; }
      goId = 40; continue;
    }
    else if (goId == 40)
    {
      mp1 = m + 1;

      for (int i = mp1; i <= n; i+=5)
      {
        dtemp += dx[i+dxOffset]*dy[i+dyOffset] + dx[i + 1+dxOffset]*dy[i + 1+dyOffset] +
          dx[i + 2+dxOffset]*dy[i + 2+dyOffset] + dx[i + 3+dxOffset]*dy[i + 3+dyOffset] + dx[i + 4+dxOffset]*dy[i + 4+dyOffset];
      }
      goId = 60; continue;
    }
    else if (goId == 60)
    {
      dddot = dtemp;
      return dddot;
    }
    else
    {
      System.out.println("ERROR: in ddot(). Unknown goId " + goId);
      System.exit(-1);
    }
    }//while (goId >= 0)
    return -1;
  }

  private void dpofa(double[] a, final int aOffset, final int lda, final int n, int[] info, boolean smallerMatrix)
  {
    double t;
    double s;
    //int j,k
    int jm1;
    int idx;
    //begin block with ...exits to 40

    for (int j = 1; j <= n; j++)
    {
      info[0] = j;
      s = 0.0;
      jm1 = j - 1;
      if (jm1 < 1)
      {
        idx = getIdx(j,j,lda);
        s = a[idx+aOffset] - s;

        //......exit
        if (s <= 0.0) { t = 0; return; }
        a[idx+aOffset] = Math.sqrt(s);
        continue;
      }

      for (int k = 1; k <= jm1; k++)
      {
        idx = getIdx(k,j,lda);
        t = a[idx+aOffset] - ddot(k-1,a,getIdx(1,k,lda)-1+aOffset,1,
                                      a,getIdx(1,j,lda)-1+aOffset,1);
        t = t/a[getIdx(k,k,lda)+aOffset];
        a[idx+aOffset] = t;
        s += t*t;
      }

      idx = getIdx(j,j,lda);
      s = a[idx+aOffset] - s;

      //......exit
      if (s <= 0.0) { t = 0; return; }
      a[idx+aOffset] = Math.sqrt(s);
    }
    info[0] = 0;
    t = 0;
  }

  private void dscal(final int n, final double da, double[] dx, final int dxOffset, final int incx)
  {
    //int i;
    int m=0,mp1=0,nincx=0;

    if (n <= 0 || incx <= 0) return;

    int goId = 0;
    while (goId >= 0)
    {
    if (goId == 0)
    {
      if (incx == 1) { goId = 20; continue; }

      //code for increment not equal to 1

      nincx = n*incx;

      for (int i = 1; i <= nincx; i+=incx)
        dx[i+dxOffset] = da*dx[i+dxOffset];
      return;

      //code for increment equal to 1

      //clean-up loop
      //goId = 20; continue;
    }
    else if (goId == 20)
    {
      m = n%5;
      if (m == 0) { goId = 40; continue; }

      for (int i = 1; i <= m; i++)
        dx[i+dxOffset] = da*dx[i+dxOffset];

      if (n < 5) return;
      goId = 40; continue;
    }
    else if (goId == 40)
    {
      mp1 = m + 1;
      for (int i = mp1; i <= n; i+=5)
      {
        dx[i+dxOffset] = da*dx[i+dxOffset];
        dx[i + 1+dxOffset] = da*dx[i + 1+dxOffset];
        dx[i + 2+dxOffset] = da*dx[i + 2+dxOffset];
        dx[i + 3+dxOffset] = da*dx[i + 3+dxOffset];
        dx[i + 4+dxOffset] = da*dx[i + 4+dxOffset];
      }
      break;
    }
    else
    {
      System.out.println("ERROR: in dscal(). Unknown goId " + goId);
      System.exit(-1);
    }
    }//while(goId >= 0)
  }

  private void dtrsl(double[] t, final int ldt, final int n,  double[] b,  final int bOffset, final int job, int[] info)
  {
    double temp;
    int ccase,j;

    int goId = 0;
    while (goId >= 0)
    {

    if (goId == 0)
    {
      //begin block permitting ...exits to 150

      //check for zero diagonal elements.

      for (info[0] = 1; info[0] <= n; info[0]++)
      {
        //......exit
        if (t[getIdx(info[0],info[0],ldt)] == 0.0) { return; }
      }
      info[0] = 0;

      //determine the task and go to it.

      ccase = 1;
      if (job%10 != 0) ccase = 2;
      if ((job%100)/10 != 0) ccase += 2;
      if (ccase==1) { goId = 20;  continue; }
      if (ccase==2) { goId = 50;  continue; }
      if (ccase==3) { goId = 80;  continue; }
      if (ccase==4) { goId = 110; continue; }

      //solve t*x=b for t lower triangular
      goId = 20; continue;
    }
    else if (goId == 20)
    {
      b[1+bOffset] /= t[getIdx(1,1,ldt)];
      if (n < 2) { goId = 40; continue; }
      for (j = 2; j <= n; j++)
      {
        temp = -b[j-1+bOffset];
        daxpy(n-j+1,temp,t,getIdx(j,j-1,ldt)-1,1,b,j-1+bOffset,1);
        b[j+bOffset] /= t[getIdx(j,j,ldt)];
      }
      goId = 40; continue;
    }
    else if (goId == 40)
    {
      return;
    }
    else if (goId == 50)
    {
      //solve t*x=b for t upper triangular.

      b[n+bOffset] /= t[getIdx(n,n,ldt)];
      if (n < 2) { goId = 70; continue; }
      for (int jj = 2; jj <= n; jj++)
      {
        j = n - jj + 1;
        temp = -b[j+1+bOffset];
        daxpy(j,temp,t,getIdx(1,j+1,ldt)-1,1,b,1-1+bOffset,1);
        b[j+bOffset] /= t[getIdx(j,j,ldt)];
      }
      goId = 70; continue;
    }
    else if (goId == 70)
    {
      return;
    }
    else if (goId == 80)
    {
      //solve trans(t)*x=b for t lower triangular.
      b[n+bOffset] /= t[getIdx(n,n,ldt)];
      if (n < 2) { goId = 100; continue; }
      for (int jj = 2; jj <= n; jj++)
      {
        j = n - jj + 1;
        b[j+bOffset] = b[j+bOffset] - ddot(jj-1,t,getIdx(j+1,j,ldt)-1,1,b,j+1-1+bOffset,1);
        b[j+bOffset] /= t[getIdx(j,j,ldt)];
      }
      goId = 100; continue;
    }
    else if (goId == 100)
    {
      return;
    }
    else if (goId == 110)
    {
      //solve trans(t)*x=b for t upper triangular.

      b[1+bOffset] /= t[getIdx(1,1,ldt)];
      if (n < 2) { return; }
      for (j = 2; j <= n; j++)
      {
        b[j+bOffset] = b[j+bOffset] - ddot(j-1,t,getIdx(1,j,ldt)-1,1,b,1-1+bOffset,1);
        b[j+bOffset] /= t[getIdx(j,j,ldt)];
      }
      return;
    }
    else
    {
      System.out.println("ERROR: in dtrsl(). Unknown goId " + goId);
      System.exit(-1);
    }
    }//while (goId >= 0)
  }
}
