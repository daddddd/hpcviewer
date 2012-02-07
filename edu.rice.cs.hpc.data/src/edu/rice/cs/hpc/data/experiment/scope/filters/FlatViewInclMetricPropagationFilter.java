package edu.rice.cs.hpc.data.experiment.scope.filters;

import edu.rice.cs.hpc.data.experiment.metric.BaseMetric;
import edu.rice.cs.hpc.data.experiment.metric.MetricType;
import edu.rice.cs.hpc.data.experiment.scope.FileScope;
import edu.rice.cs.hpc.data.experiment.scope.LoadModuleScope;
import edu.rice.cs.hpc.data.experiment.scope.ProcedureScope;
import edu.rice.cs.hpc.data.experiment.scope.RootScope;
import edu.rice.cs.hpc.data.experiment.scope.Scope;

//prevent inclusive metrics from being propagated to the FileScope level in the flat view
public class FlatViewInclMetricPropagationFilter implements MetricValuePropagationFilter {
	/** The parsed metric objects. */
	protected BaseMetric[] metrics;

	public FlatViewInclMetricPropagationFilter(BaseMetric[] metricArray) {
		this.metrics = metricArray;
	}
	
	public boolean doPropagation(Scope source, Scope target, int src_idx, int targ_idx) {
		// ----------------------------------------
		// For file scope: we don't need the inclusive cost (this is debatable)
		// For procedure scope: the cost is already computed by FlatViewScopeVisitor class
		// ----------------------------------------
		if ( ((target instanceof ProcedureScope) && !((ProcedureScope)target).isAlien()) 
				|| (target instanceof FileScope) || (target instanceof LoadModuleScope) ) { //target instanceof FileScope || 
			return ( metrics[src_idx].getMetricType() == MetricType.EXCLUSIVE);
		}
		// Laks 2008.10.21: since inclusive cost of file scope has been computed in the 
		//		flat tree creation, we have to avoid to include it in aggregate value
		if ( target instanceof RootScope )
			return false;
		return true;
	}
}