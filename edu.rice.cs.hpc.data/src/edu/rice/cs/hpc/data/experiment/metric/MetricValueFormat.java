/////////////////////////////////////////////////////////////////////////////
//									   //
//	MetricValueFormat.java						   //
//									   //
//	experiment.MetricValueFormat -- a value of a metric at some scope  //
//	Last edited: January 15, 2002 at 12:37 am			   //
//									   //
//	(c) Copyright 2002 Rice University. All rights reserved.	   //
//									   //
/////////////////////////////////////////////////////////////////////////////




package edu.rice.cs.hpc.data.experiment.metric;


import edu.rice.cs.hpc.data.util.*;

import java.text.DecimalFormat;



//////////////////////////////////////////////////////////////////////////
//	CLASS METRIC-VALUE-FORMAT					//
//////////////////////////////////////////////////////////////////////////

 /**
 *
 * The format used to display values of a metric.
 *
 */


public class MetricValueFormat implements IMetricValueFormat
{

	protected static class Style
	{
		/** The kind of numeric display to be used, either FIXED or FLOAT. */
		public int kind;

		/** The number of characters to be ysed for the number. */
		public int fieldWidth;

		/** The number of digits to be used for the fractional part. */
		public int fractionDigits;
	};


/** Whether to show the actual value. */
protected boolean showValue;

/** Whether to show the percentage value. */
protected boolean showPercent;

/** The number format to be used for the actual value. */
protected Style valueStyle;

/** The number format to be used for the percentage value. */
protected Style percentStyle;

/** How many space characters separate the actual and percentage values. */
protected int separatorWidth;

/** A Java formatter implementing the format specified for actual values. */
protected DecimalFormat valueFormatter;

/** A Java formatter implementing the format specified for percent values. */
protected DecimalFormat percentFormatter;

/** A sequence of spaces used to separate the actual and percent values. */
protected String separator;




//////////////////////////////////////////////////////////////////////////
//	PUBLIC CONSTANTS						//
//////////////////////////////////////////////////////////////////////////




/** Indicates that a number should be displayed in fixed point format. */
public static int FIXED = 1;

/** Indicates that a number should be displayed in floating point ("scientific") format. */
public static int FLOAT = 2;

/** The default metric value format. */
public static MetricValueFormat DEFAULT_PERCENT   = new MetricValueFormat(true);
public static MetricValueFormat DEFAULT_NOPERCENT = new MetricValueFormat(false);




//////////////////////////////////////////////////////////////////////////
//	INITIALIZATION														//
//////////////////////////////////////////////////////////////////////////




/*************************************************************************
 *	Creates a default format.
 ************************************************************************/
	
public MetricValueFormat(boolean percent)
{
	this(true, MetricValueFormat.FLOAT, 8, 2, percent, MetricValueFormat.FIXED, 5, 1, 1);
}




/*************************************************************************
 *	Creates a fully specified format.
 ************************************************************************/
	
public MetricValueFormat(boolean showValue,
						 int valueKind,
						 int valueFieldWidth,
						 int valueFractionDigits,
						 boolean showPercent,
						 int percentKind,
						 int percentFieldWidth,
						 int percentFractionDigits,
						 int separatorWidth)
{
	// creation arguments
	this.showValue = showValue;
	this.valueStyle = new MetricValueFormat.Style();
	this.valueStyle.kind = valueKind;
	this.valueStyle.fieldWidth = valueFieldWidth;
	this.valueStyle.fractionDigits = valueFractionDigits;
	
	this.showPercent = showPercent;
	this.percentStyle = new MetricValueFormat.Style();
	this.percentStyle.kind = percentKind;
	this.percentStyle.fieldWidth = percentFieldWidth;
	this.percentStyle.fractionDigits = percentFractionDigits;
	
	this.separatorWidth = separatorWidth;
	
	// Java formatters are initialized lazily
	this.clearFormatters();
}




//////////////////////////////////////////////////////////////////////////
//	ACCESS TO FORMAT													//
//////////////////////////////////////////////////////////////////////////




/*************************************************************************
 *	Sets whether to show the actual value.
 ************************************************************************/
	
public void setShowValue(boolean showValue)
{
	this.showValue = showValue;
	this.clearFormatters();
}




/*************************************************************************
 *	Returns whether to show the actual value.
 ************************************************************************/
	
public boolean getShowValue()
{
	return this.showValue;
}




/*************************************************************************
 *	Sets the kind of numeric display to be used for the actual value.
 *	
 *	@param kind		either <code>MetricValueFormat.FIXED</code> or
 *					<code>MetricValueFormat.FLOAT</code>
 *
 ************************************************************************/
	
public void setValueKind(int kind)
{
	this.valueStyle.kind = kind;
	this.clearFormatters();
}




/*************************************************************************
 *	Returns the kind of numeric display to be used for the actual value.
 *	
 *	@return		either <code>MetricValueFormat.FIXED</code> or
 *				<code>MetricValueFormat.FLOAT</code>
 *
 ************************************************************************/
	
public int getValueKind()
{
	return this.valueStyle.kind;
}




/*************************************************************************
 *	Sets the total number of characters to be used for the actual value.
 ************************************************************************/
	
public void setValueFieldWidth(int fieldWidth)
{
	this.valueStyle.fieldWidth = fieldWidth;
	this.clearFormatters();
}




/*************************************************************************
 *	Returns the total number of characters to be used for the actual value.
 ************************************************************************/
	
public int getValueFieldWidth()
{
	return this.valueStyle.fieldWidth;
}




/*************************************************************************
 *	Sets the number of digits to be used for the fractional part of the
 *	actual value.
 ************************************************************************/
	
public void setValueFractionDigits(int fractionDigits)
{
	this.valueStyle.fractionDigits = fractionDigits;
	this.clearFormatters();
}




/*************************************************************************
 *	Returns the number of digits to be used for the fractional part of the
 *	actual value.
 ************************************************************************/
	
public int getValueFractionDigits()
{
	return this.valueStyle.fractionDigits;
}




/*************************************************************************
 *	Sets whether to show the percentage value.
 ************************************************************************/
	
public void setShowPercent(boolean showPercent)
{
	this.showPercent = showPercent;
	this.clearFormatters();
}




/*************************************************************************
 *	Returns whether to show the percentage value.
 ************************************************************************/
	
public boolean getShowPercent()
{
	return this.showPercent;
}




/*************************************************************************
 *	Sets the kind of numeric display to be used for the percentage value.
 *	
 *	@param kind		either <code>MetricValueFormat.FIXED</code> or
 *					<code>MetricValueFormat.FLOAT</code>
 *
 ************************************************************************/
	
public void setPercentKind(int kind)
{
	this.percentStyle.kind = kind;
	this.clearFormatters();
}




/*************************************************************************
 *	Returns the kind of numeric display to be used for the percentage value.
 *	
 *	@return		either <code>MetricValueFormat.FIXED</code> or
 *				<code>MetricValueFormat.FLOAT</code>
 *
 ************************************************************************/
	
public int getPercentKind()
{
	return this.percentStyle.kind;
}




/*************************************************************************
 *	Sets the total number of characters to be used for the percentage value.
 ************************************************************************/
	
public void setPercentFieldWidth(int fieldWidth)
{
	this.percentStyle.fieldWidth = fieldWidth;
	this.clearFormatters();
}




/*************************************************************************
 *	Returns the total number of characters to be used for the percentage value.
 ************************************************************************/
	
public int getPercentFieldWidth()
{
	return this.percentStyle.fieldWidth;
}




/*************************************************************************
 *	Sets the number of digits to be used for the fractional part of the
 *	percentage value.
 ************************************************************************/
	
public void setPercentFractionDigits(int fractionDigits)
{
	this.percentStyle.fractionDigits = fractionDigits;
	this.clearFormatters();
}




/*************************************************************************
 *	Returns the number of digits to be used for the fractional part of the
 *	percentage value.
 ************************************************************************/
	
public int getPercentFractionDigits()
{
	return this.percentStyle.fractionDigits;
}




/*************************************************************************
 *	Sets the number of space characters to separate the actual and
 *	percentage values.
 ************************************************************************/
	
public void setSeparatorWidth(int separatorWidth)
{
	this.separatorWidth = separatorWidth;
	this.clearFormatters();
}




/*************************************************************************
 *	Returns the number of space characters to separate the actual and
 *	percentage values.
 ************************************************************************/
	
public int getSeparatorWidth()
{
	return this.separatorWidth;
}




//////////////////////////////////////////////////////////////////////////
//	FORMATTING															//
//////////////////////////////////////////////////////////////////////////




/*************************************************************************
 *	Returns the number of characters in a metric value formatted by this format.
 ************************************************************************/
	
public int getFormattedLength()
{
	int width1 = (this.showValue ? this.valueStyle.fieldWidth : 0);
	int width2 = (this.showPercent ? this.percentStyle.fieldWidth : 0);
	int width3 = (this.showValue && this.showPercent ? this.separatorWidth : 0);
	return width1 + width2 + width3 + 1;	// +1 for trailing space
}


/**
 * format the value without the information from MetricValue. This
 * method is need to compute the derived metrics on the fly without
 * instantiating or creating new class which will consume more memory
 * (I guess ---laks).
 * @param value
 * @return <code>String</code> the text format.
 *//*
public String format(double value) {
	StringBuffer formatted = new StringBuffer();
	String string = this.formatDouble(value, this.valueFormatter, this.valueStyle);
	formatted.append(string);
	formatted.append(Util.spaces(this.percentStyle.fieldWidth));
	return formatted.toString();
}*/
/*************************************************************************
 *	Returns a <code>String</code> showing a given <code>MetricValue</code>
 *	according to this format.
 ************************************************************************/
	
public String format(MetricValue value)
{
	this.ensureFormatters();
	StringBuffer formatted = new StringBuffer();
	
	// append formatted actual value if wanted
	if( this.showValue )
	{
		double number = value.getValue();
		String string = this.formatDouble(number, this.valueFormatter, this.valueStyle);
		formatted.append(string);
	}
	
	// append separating spaces if needed
	if( this.showValue && this.showPercent )
		formatted.append(this.separator);
	
	// append formatted percentage value if wanted
	if( this.showPercent )
	{
		if( value.isPercentAvailable() )
		{
			double number = value.getPercentValue();
			if (number == 1.0) {    // johnmc
				formatted.append("100 %");
				// Laks 2009.02.12: dirty hack to solve the problem when a small negative percentage occurs
				// instead of displaying -0.0% we force to display 0.0%
				// a better solution is by defining the proper pattern. But so far I don't see any good solution
				// 	this hack should be a temporary fix !
			} else if ( (number > -0.0001) && (number < 0.0) ) {
				formatted.append(" 0.0%");
			} else {
				String string = this.formatDouble(number, this.percentFormatter, this.percentStyle);
				formatted.append(string);
			}
		}
		else
			formatted.append(Util.spaces(this.percentStyle.fieldWidth));
	}
	
	return formatted.toString();
}




/*************************************************************************
 *	Returns a <code>String</code> showing a given <code>MetricValue</code>
 *	according to this format.
 ************************************************************************/
	
protected String formatDouble(double d, DecimalFormat formatter, Style style)
{
	int kind = style.kind;
	int fieldWidth = style.fieldWidth;
	String s;
	
	if( kind == FLOAT )
	{
		int exponent = 0;
		// laks: if d = 9.999, the formatter will force to round it to 10.00
		// 	since I don't know how to prevent the rounding, let make a dirty solution here
		// Laks 2009.02.12: turn it back to the original format. Previously: > 9.5
		// Laks 2009.02.13: bug fix for displaying 9.9 into 1.0e+01 instead of 10.0
		while ( Math.abs(d) > 9.5 )//Laks 2008.09.03 fix previously ( Math.abs(d) >= 10.0 )
		{
			d /= 10.0;
			exponent += 1;
		}
		if (d != 0.0) {
			// Laks 2009.02.12: turn it back to the original format. Previously: < 9.5
			// Laks 2009.02.13: bug fix for displaying .999x into 1.0e00 and .99x into 9.9x
			// FIXME this is an ugly bug fix, but since the formatter is handled by jvm, we have to hack from here
			while( Math.abs(d) <= 0.999 )//laks 2008.09.03 fix, previously ( Math.abs(d) < 1.0 )
			{
				d *= 10.0;
				exponent -= 1;
			}
		}
		String e = Integer.toString(Math.abs(exponent));
		if( e.length() == 1 ) e = "0" + e;
		s = formatter.format(d) + "e";
		s = s + ((exponent < 0) ? "-" : "+") + e;
	}
	else
		s = Util.formatDouble(d, formatter, fieldWidth);

	return s;
}




/*************************************************************************
 *	Removes outdated Java <code>DecimalFormat</code> objects.
 *
 *	New ones will be created when needed.
 ************************************************************************/
	
protected void clearFormatters()
{
	this.valueFormatter   = null;
	this.percentFormatter = null;
	this.separator        = null;
}




/*************************************************************************
 *	Creates Java <code>DecimalFormat</code> objects if necessary.
 ************************************************************************/
	
protected void ensureFormatters()
{
	// actual value
	if( this.valueFormatter == null )
	{
		String pattern = "0.";
                
                // use the number of fractional digits to craft the pattern
                int decdigits = getValueFractionDigits();
                while (decdigits-- > 0) {
                    pattern = pattern + "0";
                }
                
		this.valueFormatter = Util.makeDecimalFormatter(pattern);
	}
	
	// percentage value
	if( this.percentFormatter == null )
	{
		String pattern = "#0.0%";
		this.percentFormatter = Util.makeDecimalFormatter(pattern);
	}
	
	// separation between values
	this.separator = Util.spaces(this.separatorWidth);
}




}








