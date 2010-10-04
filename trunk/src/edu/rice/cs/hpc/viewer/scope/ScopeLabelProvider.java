package edu.rice.cs.hpc.viewer.scope;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Color;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.swt.SWT;

import edu.rice.cs.hpc.data.experiment.scope.*;
import edu.rice.cs.hpc.viewer.resources.Icons;
import edu.rice.cs.hpc.viewer.util.Utilities;

public class ScopeLabelProvider extends ColumnLabelProvider {
	final static protected Icons iconCollection = Icons.getInstance();
	final static private boolean debug = true;
	final private Color DARK_BLUE;
	
	/**
	 * Default constructor
	 */
	public ScopeLabelProvider(IWorkbenchWindow window) {
		// TODO Auto-generated constructor stub
		super();
		this.DARK_BLUE = window.getWorkbench().getDisplay().getSystemColor(SWT.COLOR_DARK_BLUE);
	}

	/**
	 * Return the image of the column. By default no image
	 */
	public Image getImage(Object element) {
		if(element instanceof Scope) {
			Scope node = (Scope) element;
			return Utilities.getScopeNavButton(node);
		}
		return null;
	}
	
	/**
	 * Return the text of the scope tree. By default is the scope name.
	 */
	public String getText(Object element) {
		String text = null;
		if (element instanceof Scope){
			Scope node = (Scope) element;
			if (debug)  {
				if (node instanceof CallSiteScopeCallerView) {
					Scope cct = ((CallSiteScopeCallerView) node).getScopeCCT();
					text = "[" + cct.getCCTIndex()  + "]" + node.getName();
				} else
					text = "[" + node.getCCTIndex() + "] " + node.getName();
			} else
				text = node.getName();			
		} else
			text = element.getClass().toString();
		return text;
	}
	
	/**
	 * Mark blue for node that has source code file name information.
	 * Attention: we do not verify if the source code exist or not !!
	 */
	public Color getForeground(Object element) {
		if(element instanceof Scope) {
			Scope node = (Scope) element;
			if(Utilities.isFileReadable(node)) {
				node.hasSourceCodeFile = true; //update the indicator flag in the node
				// put the color blue
				return this.DARK_BLUE;
			}
		}
		return null;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ColumnLabelProvider#getFont(java.lang.Object)
	 */
	public Font getFont(Object element) {
		return Utilities.fontGeneral;
	}
}
