//////////////////////////////////////////////////////////////////////////
//																		//
//	ExperimentMerger.java												//
//																		//
//	ExperimentMerger -- class to merge two Experiments					//
//	Created: May 7, 2007 												//
//																		//
//	(c) Copyright 2007 Rice University. All rights reserved.			//
//																		//
//////////////////////////////////////////////////////////////////////////
package edu.rice.cs.hpc.data.experiment;

import java.util.*;

import edu.rice.cs.hpc.data.experiment.metric.*;
import edu.rice.cs.hpc.data.experiment.scope.*;
import edu.rice.cs.hpc.data.experiment.scope.filters.*;
import edu.rice.cs.hpc.data.experiment.scope.visitors.*;

public class ExperimentMerger {
	public Experiment merge(Experiment exp1, Experiment exp2) {
		// create new base Experiment
		Experiment merged = new Experiment(exp1);
		
		// union SourceFile lists,
		//  Laks 2009.01.06: get rid off unused methods and attributes
		// List files = unionSourceFiles(exp1.files, exp2.files);
		// merged.setSourceFiles(files);
		
		// append metricList
		List<BaseMetric> metrics = buildMetricList(merged, exp1.getMetrics(), exp2.getMetrics());
		merged.setMetrics(metrics);

		// union ScopeLists
		// Laks 2009.01.06: get rid off unused methods and attributes
		//List scopeList = unionScopeLists(exp1.getScopeList(), exp2.getScopeList());
		
		// Add tree1, walk tree2 & add; just CCT/Flat
		//RootScope rootScope = new RootScope(merged, "Merged Experiment","Invisible Outer Root Scope", RootScopeType.Invisible);
		//merged.setScopes(scopeList, rootScope);

		mergeScopeTrees(merged, exp1, 0);
		mergeScopeTrees(merged, exp2, exp1.getMetricCount());
		
		return merged;
	}
	
	
	private Vector<BaseMetric> buildMetricList(Experiment exp, BaseMetric[] m1, BaseMetric[] m2) {
		final Vector<BaseMetric> metricList = new Vector<BaseMetric>();
		
		for (int i=0; i<m1.length; i++) {
			metricList.add(m1[i]);
		}
		
		final int m1_last_index = m1[m1.length-1].getIndex();
		
		for (int i=0; i<m2.length; i++) {
			final BaseMetric m = m2[i].duplicate();
			
			// recompute the index of the metric
			m.setIndex( m1_last_index + m.getIndex() );
			
			metricList.add(m);
		}
		
		return metricList;
	}
	
	
	public void mergeScopeTrees(Experiment exp1, Experiment exp2, int offset) {
		EmptyMetricValuePropagationFilter emptyFilter = new EmptyMetricValuePropagationFilter();
		Scope root1 = exp1.getRootScope();
		Scope root2 = exp2.getRootScope();
		
		MergeScopeTreesVisitor mv = new MergeScopeTreesVisitor(root1, offset, emptyFilter);

		root2.dfsVisitScopeTree(mv);
	}	
}


