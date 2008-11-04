package edu.rice.cs.hpc.data.experiment.scope.visitors;

import java.util.Hashtable;
import java.util.LinkedList;

import edu.rice.cs.hpc.data.experiment.Experiment;
import edu.rice.cs.hpc.data.experiment.metric.*;
import edu.rice.cs.hpc.data.experiment.scope.*;
import edu.rice.cs.hpc.data.experiment.scope.filters.EmptyMetricValuePropagationFilter;
import edu.rice.cs.hpc.data.experiment.scope.filters.ExclusiveOnlyMetricPropagationFilter;
import edu.rice.cs.hpc.data.experiment.scope.filters.InclusiveOnlyMetricPropagationFilter;
import edu.rice.cs.hpc.data.experiment.scope.filters.MetricValuePropagationFilter;

public class CallersViewScopeVisitor implements ScopeVisitor {


	//----------------------------------------------------
	// private data
	//----------------------------------------------------
	protected boolean isdebug = false; // true;
	protected Hashtable/*<int, Scope>*/ calleeht = new Hashtable/*<int, Scope>*/();
	protected Experiment exp;
	protected Scope callersViewRootScope;
	protected MetricValuePropagationFilter filter;
	protected int numberOfPrimaryMetrics;
	private final ExclusiveOnlyMetricPropagationFilter exclusiveOnly;
	private final InclusiveOnlyMetricPropagationFilter inclusiveOnly;

	//private Scope scopeCurrentCall;
	//private boolean isParentScopeCallProcedureScope = false;
	
	//----------------------------------------------------
	// constructor for CallerViewScopeVisitor
	//----------------------------------------------------

	public CallersViewScopeVisitor(Experiment experiment, Scope cvrs, 
			int nMetrics, boolean dodebug, MetricValuePropagationFilter filter) {
		this.exp = experiment;
		this.callersViewRootScope = cvrs;
		this.numberOfPrimaryMetrics = nMetrics;
		this.isdebug = dodebug;
		this.filter = filter;
		BaseMetric []metrics = experiment.getMetrics();
		exclusiveOnly = new ExclusiveOnlyMetricPropagationFilter(metrics);
		inclusiveOnly = new InclusiveOnlyMetricPropagationFilter(metrics);
	}

	//----------------------------------------------------
	// visitor pattern instantiations for each Scope type
	//----------------------------------------------------

	public void visit(CallSiteScope scope, ScopeVisitType vt) {
		ProcedureScope mycallee  = scope.getProcedureScope();
		Integer objCode = new Integer(mycallee.hashCode());
		if (vt == ScopeVisitType.PreVisit) { // && !mycallee.isAlien()) {
			String procedureName = mycallee.getName();

			trace("handling scope " + procedureName);

			CallSiteScope scopeCall = (CallSiteScope)scope.duplicate(); // create a temporary scope to accumulate metrics to
			scopeCall.accumulateMetrics(scope, new EmptyMetricValuePropagationFilter(), numberOfPrimaryMetrics);
			// Remove linescope-normalization from CS-scope
			for (int i=0; i<numberOfPrimaryMetrics; i++) {
				double lsval = scope.getLineScope().getMetricValue(i).getValue();
				// LA: This statement means removing the cost of line scope from the call site.
				scopeCall.accumulateMetricValue(i, (lsval<0.0) ? 0.0 : (-1*lsval) );
			}

			// if there are no exclusive costs to attribute from this context, we are done here
			if (!scopeCall.hasNonzeroMetrics()) return; 

			// Find (or add) callee in top-level hashtable
			// TODO: we should use a fully qualified procedure name (including file, module)
			ProcedureScope callee = (ProcedureScope) calleeht.get(objCode);
			if (callee == null) {
				callee  = (ProcedureScope)mycallee.duplicate();
				callee.iCounter = 0;
				//calleeht.put(objCode, callee);
				callersViewRootScope.addSubscope(callee);
				callee.setParentScope(this.callersViewRootScope);
				exp.getScopeList().addScope(callee);
 				trace("added top level entry in bottom up tree");
			} else {
					// debugging purpose
					// to be here, it must be a recursive routine
			}
			callee.iCounter++;
			if(callee.iCounter == 1) {
				// add the cost into the procedure "root" if necessary
				callee.accumulateMetrics(scope, inclusiveOnly, numberOfPrimaryMetrics);
			}
			callee.accumulateMetrics(scope, exclusiveOnly, numberOfPrimaryMetrics);
			calleeht.put(objCode, callee);

			//-----------------------------------------------------------------------
			// compute callPath: a chain of my callers
			//
			// we build a chain of callers  by tracing the path from a procedure
			// up to the root of the calling context tree. notice that 
			// this isn't simply a reversal of the path. in the chain of callers,
			// we associate the call site with the caller. in the calling context
			// tree, the call site is paired with the callee. that's why we
			// work with a pair of CallSiteScopes at a time (innerCS and enclosingCS)
			//-----------------------------------------------------------------------
			trace("determine call path for " + procedureName);
			CallSiteScope innerCS = scope;
			LinkedList callPathList = new LinkedList();
			Scope next = scope.getParentScope();
			while (next instanceof CallSiteScope || next instanceof LoopScope ||
					next instanceof ProcedureScope)
			{
				if (!(next instanceof LoopScope) && innerCS != null) {
					CallSiteScope enclosingCS = null;
					ProcedureScope mycaller = null;
					if (next instanceof ProcedureScope) {
						mycaller = (ProcedureScope) next.duplicate();
					}
					else if (next instanceof CallSiteScope) {
						enclosingCS = (CallSiteScope) next;
						mycaller = (ProcedureScope) enclosingCS.getProcedureScope().duplicate();
					}
					LineScope lineScope = innerCS.getLineScope();
					if(lineScope != null) {
						CallSiteScope callerScope =
							new CallSiteScope((LineScope) lineScope.duplicate(), 
									mycaller,
									CallSiteScopeType.CALL_FROM_PROCEDURE);
						callerScope.accumulateMetrics(scopeCall, new EmptyMetricValuePropagationFilter(), numberOfPrimaryMetrics);
						callPathList.addLast(callerScope);
						innerCS = enclosingCS;
					}
				}
				next = next.getParentScope();
			}

			//-------------------------------------------------------
			// ensure my call path is represented among my children.
			//-------------------------------------------------------
			mergeCallerPath(callee, callPathList);
			
		} else if (vt == ScopeVisitType.PostVisit)  {
			ProcedureScope callee = (ProcedureScope) calleeht.get(objCode);
			// it is nearly impossible that the callee is null but I prefer to do this in case we encounter
			//		a bug or a very strange call path
			if(callee != null) {
				// BUG ! For unknown reason, it is possible to have multiple post-visit for the same callee hash code !
				// It seems the hashcode is not suitable in our case. 
				// Here we make a temporary fix by not decrementing to negative value.
				if(callee.iCounter>0)
					callee.iCounter--;
				else
					System.err.println("CVSV: "+callee.getName()+" from "+scope.getName()+"\t"+callee.iCounter);
			}
		}
	}

	public void visit(Scope scope, ScopeVisitType vt) { }
	public void visit(RootScope scope, ScopeVisitType vt) { }
	public void visit(LoadModuleScope scope, ScopeVisitType vt) { }
	public void visit(FileScope scope, ScopeVisitType vt) { }
	
	public void visit(ProcedureScope scope, ScopeVisitType vt) { 
		ProcedureScope mycallee  = scope;
		if (vt == ScopeVisitType.PreVisit) {
			if(!mycallee.isAlien()) {
				String procedureName = mycallee.getName();
				trace("handling scope " + procedureName);

				ProcedureScope tmp = (ProcedureScope)scope.duplicate(); // create a temporary scope to accumulate metrics to
				tmp.accumulateMetrics(scope, new EmptyMetricValuePropagationFilter(), numberOfPrimaryMetrics);

				// if there are no exclusive costs to attribute from this context, we are done here
				if (!tmp.hasNonzeroMetrics()) return; 

				// Find (or add) callee in top-level hashtable
				// TODO: we should use a fully qualified procedure name (including file, module)
				ProcedureScope callee = (ProcedureScope) calleeht.get(new Integer(mycallee.hashCode()));
				if (callee == null) {
					callee  = (ProcedureScope)mycallee.duplicate();
					callee.iCounter = 1;
					calleeht.put(new Integer(callee.hashCode()), callee);
					callersViewRootScope.addSubscope(callee);
					callee.setParentScope(this.callersViewRootScope);
					exp.getScopeList().addScope(callee);
					trace("added top level entry in bottom up tree");
				}
				callee.accumulateMetrics(tmp, this.inclusiveOnly, 
						numberOfPrimaryMetrics);
			} else {
				
			}
		} else if (vt == ScopeVisitType.PostVisit){
			ProcedureScope callee = (ProcedureScope) calleeht.get(new Integer(mycallee.hashCode()));
			if  (callee != null) {
				callee.iCounter--;
			}
		}
	
	}
	
	public void visit(AlienScope scope, ScopeVisitType vt) { }
	public void visit(LoopScope scope, ScopeVisitType vt) { 
		//this.accumulateExclusiveProcedureCost(scope, vt);	
		}
	public void visit(StatementRangeScope scope, ScopeVisitType vt) { 	}
	public void visit(LineScope scope, ScopeVisitType vt) { 
		if(vt == ScopeVisitType.PreVisit) {
			if(scope.getParentScope() instanceof LoopScope) {
				
			} else {
			//	this.accumulateExclusiveProcedureCost(scope, vt);
			}
		}
	}
	public void visit(GroupScope scope, ScopeVisitType vt) { }

	//----------------------------------------------------
	// helper functions  
	//----------------------------------------------------
	
	/**
	 * Method to accumulate the cost of a loop and attribute it to the enclosed procedure scope
	 * Rationale:
	 *  - A procedure scope in CT can be call sites in CCT. However, the exclusive cost of call sites in CCT 
	 *  	does not include the exclusive cost of its loops/statements 
	 *  - The exclusive cost of a loop (in CCT) is uniquely the cost of the loop without taking into account
	 *  	its inner loops.
	 *  Therefore, it is imperative for all loops to attribute its cost to the procedure scope in CT
	 *  
	 */
	/*
	private void accumulateExclusiveProcedureCost(Scope scope, ScopeVisitType vt) {
		if(vt == ScopeVisitType.PreVisit) {
			// get the procedure or callsite scope
			Scope ancestor = scope.getParentScope();
			while( (ancestor != null) && !(ancestor instanceof ProcedureScope && !((ProcedureScope)ancestor).isAlien()) && 
					!(ancestor instanceof RootScope) && !(ancestor instanceof CallSiteScope) ) {
				ancestor = ancestor.getParentScope();
			}
			// find the corresponding CV's scope of the enclosed procedure
			if ( (ancestor != null) && !(ancestor instanceof RootScope) ) {
				ProcedureScope scopeProc = null;
				if(ancestor instanceof ProcedureScope)
					scopeProc = (ProcedureScope) ancestor;
				else if(ancestor instanceof CallSiteScope) {
					CallSiteScope callee = (CallSiteScope) ancestor;
					scopeProc = callee.getProcedureScope();
				} else
					// should be never happens to be in this path
					return;
				if( (scopeProc != null) && (!scopeProc.isAlien()) ) {
					Integer objCode = new Integer(scopeProc.hashCode());
					ProcedureScope scopeEnclosedProc = (ProcedureScope) calleeht.get(objCode);
					// once we have the enclosed procedure, accumulate the exclusive cost of this loop
					if(scopeEnclosedProc != null) {
						//if(scopeEnclosedProc.iCounter == 1)
							scopeEnclosedProc.accumulateMetrics(scope, this.exclusiveOnly, this.numberOfPrimaryMetrics);
					} 
				}
			}
		} 
	} */

	protected void mergeCallerPath(Scope callee, LinkedList callerPathList) 
	{
		if (callerPathList.size() == 0) return; // merging an empty path is trivial

		CallSiteScope first = (CallSiteScope) callerPathList.removeFirst();

		// -------------------------------------------------------------------------
		// attempt to merge first node on caller path with existing caller of callee  
		//--------------------------------------------------------------------------
		int nCallers = callee.getSubscopeCount();
		for (int i = 0; i < nCallers; i++) {
			CallSiteScope existingCaller = (CallSiteScope) callee.getSubscope(i);
			// if first matches an existing caller
			if (existingCaller.getLineScope().isequal(first.getLineScope()) &&
					(existingCaller.getName()).equals(first.getName())) {

				// add metric values for first to those of existingCaller.
				// Laks 2008.09.09: a tricky bugfix on setting the cost only if the child has a bigger cost
				existingCaller.mergeMetric(first, this.inclusiveOnly);
				//existingCaller.accumulateMetrics(first, filter, numberOfPrimaryMetrics);
				
				// merge rest of call path as a child of existingCaller.
				mergeCallerPath(existingCaller, callerPathList);

				return; // merged with existing child. nothing left to do.
			}
		}

		//----------------------------------------------
		// no merge possible. add new path into tree.
		//----------------------------------------------
		callee.addSubscope(first);
		first.setParentScope(callee);
		for (Scope prev = first; callerPathList.size() > 0; ) {
			Scope next = (Scope) callerPathList.removeFirst();
			prev.addSubscope(next);
			next.setParentScope(prev);
			prev = next;
		}
	}
	//----------------------------------------------------
	// debugging support 
	//----------------------------------------------------

	void trace(String msg) {
		if (isdebug) System.out.println(msg);
	}
}
