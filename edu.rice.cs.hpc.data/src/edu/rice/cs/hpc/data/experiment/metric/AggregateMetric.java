package edu.rice.cs.hpc.data.experiment.metric;

import com.graphbuilder.math.Expression;
import com.graphbuilder.math.ExpressionParseException;
import com.graphbuilder.math.ExpressionTree;

import edu.rice.cs.hpc.data.experiment.BaseExperimentWithMetrics;
import edu.rice.cs.hpc.data.experiment.Experiment;
import edu.rice.cs.hpc.data.experiment.scope.Scope;
import com.graphbuilder.math.FuncMap;


/***
 * Class for handling derived-incremental type metric
 * This metric is generated by hpcprof-mpi which contains two formula to compute the value:
 * 	combine formula: used for computing temporary value during caller and flat view creation
 *  finalize formula: used to finalizing value independent of the type of the view
 *  
 * @author laksonoadhianto
 *
 */
public class AggregateMetric extends BaseMetric {
 
	static final public char FORMULA_COMBINE = 'c';
	static final public char FORMULA_FINALIZE = 'f';
	
	// formula expression
	private Expression formulaCombine, formulaFinalize;
	//private Expression formulaCurrent = null;
	
	// map function
	private FuncMap fctMap;
	// map variable 
	private MetricVarMap finalizeVarMap;
	private CombineAggregateMetricVarMap combineVarMap;

	
	/**
	 * @see BaseMetric
	 */
	public AggregateMetric(String sID, String sDisplayName, boolean displayed, String format,
			AnnotationType annotationType, int index, int partner, MetricType type) {

		super( sID, sDisplayName, displayed, format, annotationType, index, partner, type);
		
		this.fctMap = new FuncMap();
		this.fctMap.loadDefaultFunctions();
		
		// set up the variables
		this.finalizeVarMap = new MetricVarMap();
		this.combineVarMap = new CombineAggregateMetricVarMap();
	}


	/****
	 * set the math expression
	 * @param type
	 * @param sFormula
	 *******/
	public void setFormula(char type, String sFormula) {
		assert (type == FORMULA_COMBINE || type == FORMULA_FINALIZE);
		
		try {
			if (type == FORMULA_COMBINE) {
				formulaCombine = ExpressionTree.parse(sFormula);				
			} else {
				formulaFinalize = ExpressionTree.parse(sFormula);
			}
		} catch (ExpressionParseException e) {
			e.printStackTrace();
		}
	}
	
	
	/*********
	 * initialize the metric.
	 * THIS METHOD HAS TO BE CALLED before asking the value
	 * @param type
	 * @param exp
	 *******/
	public void init(BaseExperimentWithMetrics exp) {
		this.finalizeVarMap.setExperiment((Experiment)exp);
		this.combineVarMap.setExperiment((Experiment)exp);
	}
	
	
	/********
	 * Assign the value of a scope based on the formula of a given type
	 * @param type
	 * @param scope
	 *******/
	public void finalize(Scope scope) {
		Expression exp = this.formulaFinalize;
		
		if (exp != null) {
			this.finalizeVarMap.setScope(scope);
			this.setScopeValue(exp, this.finalizeVarMap, scope);
		}
	}
	
	/******
	 * combining the metric from another view (typically cct) to this view
	 * if the target metric is not available (or empty) then we initialize it with
	 * 	the value of the source
	 * @param s_source
	 * @param s_target
	 ******/
	public void combine(Scope s_source, Scope s_target) {
		MetricValue value = s_target.getMetricValue(this); 
		if (MetricValue.isAvailable(value)) {
			//--------------------------------------------------------------------------
			// the target has the metric. we need to "combine" it with the source
			//--------------------------------------------------------------------------
			Expression expression = this.formulaCombine;
			if (expression != null) {
				this.combineVarMap.setScopes(s_source, s_target);
				this.setScopeValue(expression, this.combineVarMap, s_target);
			}
		} else {
			//--------------------------------------------------------------------------
			// the target doesn't have the metric. we need to copy from the source
			//--------------------------------------------------------------------------
			MetricValue v_source = s_source.getMetricValue(this);
			s_target.setMetricValue(index, v_source);
		}
	}
	
	
	
	/******
	 * 
	 * @param expression
	 * @param var_map
	 * @param scope
	 ******/
	private void setScopeValue(Expression expression, MetricVarMap var_map, Scope scope) {
		MetricValue mv;
		try {
			double dValue = expression.eval(var_map, this.fctMap);
			// ugly checking if the value is zero or not. There is no zero comparison in
			// Java double, so we assume we can compare it with 0.0d
			if (dValue == 0.0d)
				mv = MetricValue.NONE;
			else
				mv = new MetricValue(dValue);
		} catch(java.lang.Exception e) {
			mv = MetricValue.NONE;
			e.printStackTrace();
		}
		scope.setMetricValue(this.index, mv);
	}


	@Override
	/*
	 * (non-Javadoc)
	 * @see edu.rice.cs.hpc.data.experiment.metric.BaseMetric#getValue(edu.rice.cs.hpc.data.experiment.scope.Scope)
	 */
	public MetricValue getValue(Scope s) {
		MetricValue mv = s.getMetricValue(this.index);
		return mv;
	}

	@Override
	/*
	 * (non-Javadoc)
	 * @see edu.rice.cs.hpc.data.experiment.metric.BaseMetric#duplicate()
	 */
	public BaseMetric duplicate() {
		return new AggregateMetric(shortName, displayName, displayed, 
				null, annotationType, index, partner_index, metricType);
	}
}
