package edu.rice.cs.hpc.traceviewer.framework;

import org.eclipse.jface.action.IStatusLineManager;
//import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.application.ActionBarAdvisor;
import org.eclipse.ui.application.IActionBarConfigurer;
import org.eclipse.ui.application.IWorkbenchWindowConfigurer;
import org.eclipse.ui.application.WorkbenchWindowAdvisor;

import edu.rice.cs.hpc.traceviewer.actions.OpenDatabase;
import edu.rice.cs.hpc.traceviewer.db.AbstractDBOpener;
import edu.rice.cs.hpc.traceviewer.db.LocalDBOpener;
import edu.rice.cs.hpc.traceviewer.db.RemoteDBOpener;
import edu.rice.cs.hpc.traceviewer.db.TraceDatabase;
import edu.rice.cs.hpc.traceviewer.spaceTimeData.SpaceTimeDataControllerRemote;

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
		
		final IWorkbenchWindowConfigurer configurer = getWindowConfigurer();
		final IStatusLineManager status = configurer.getActionBarConfigurer().getStatusLineManager();
		
		//I'm duplicating this code from OpenDatabase, but I think it needs to be
		AbstractDBOpener DBOpener;
		//if (AbstractDBOpener.Local)//TODO: figure out how we want to switch between Remote and Local storage
			DBOpener = new LocalDBOpener();
		//else
		//	DBOpener = new RemoteDBOpener();
		
		TraceDatabase.openDatabase(configurer.getWindow(), args, status, DBOpener);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.application.WorkbenchWindowAdvisor#postWindowClose()
	 */
	public void postWindowClose() {
TraceDatabase DB = TraceDatabase.getInstance(this.getWindowConfigurer().getWindow());
if (DB.dataTraces instanceof SpaceTimeDataControllerRemote)
{
	SpaceTimeDataControllerRemote STDCR = (SpaceTimeDataControllerRemote)DB.dataTraces;
	STDCR.CloseConnection();
}
		// remove all the allocated resources of this window
		TraceDatabase.removeInstance(this.getWindowConfigurer().getWindow());
	}
}
