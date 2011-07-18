package edu.rice.cs.hpc.traceviewer.framework;

import org.eclipse.jface.action.IStatusLineManager;
//import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.application.ActionBarAdvisor;
import org.eclipse.ui.application.IActionBarConfigurer;
import org.eclipse.ui.application.IWorkbenchWindowConfigurer;
import org.eclipse.ui.application.WorkbenchWindowAdvisor;

import edu.rice.cs.hpc.traceviewer.db.TraceDatabase;

public class ApplicationWorkbenchWindowAdvisor extends WorkbenchWindowAdvisor {

	final private String args[];
	
	public ApplicationWorkbenchWindowAdvisor(IWorkbenchWindowConfigurer configurer, String []_args) {
		super(configurer);
		args = _args;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.application.WorkbenchWindowAdvisor#createActionBarAdvisor(org.eclipse.ui.application.IActionBarConfigurer)
	 */
	public ActionBarAdvisor createActionBarAdvisor(
			IActionBarConfigurer configurer) {
		return new ApplicationActionBarAdvisor(configurer);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.application.WorkbenchWindowAdvisor#preWindowOpen()
	 */
	public void preWindowOpen() {
		IWorkbenchWindowConfigurer configurer = getWindowConfigurer();
		//configurer.setInitialSize(new Point(1200, 800));
		configurer.setShowCoolBar(false);
		configurer.setShowStatusLine(true);
	}
	
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.application.WorkbenchWindowAdvisor#postWindowOpen()
	 */
	public void postWindowOpen() {
		
		//---------------------------------------------------------------------
		// once the widgets have been created, we ask user a database to open
		// ---------------------------------------------------------------------
		
		IWorkbenchWindowConfigurer configurer = getWindowConfigurer();
		IStatusLineManager status = configurer.getActionBarConfigurer().getStatusLineManager();
		
		TraceDatabase trace_db = new TraceDatabase(this.args);
		trace_db.openDatabase(configurer.getWindow().getShell(), status);
	}

}
