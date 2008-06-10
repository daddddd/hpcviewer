package edu.rice.cs.hpc.viewer.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.IWorkbenchWindow;

//import org.eclipse.ui.IWorkbenchPage;
//import org.eclipse.ui.PlatformUI;
//import org.eclipse.ui.IViewPart;
import edu.rice.cs.hpc.viewer.util.*;
import edu.rice.cs.hpc.viewer.experiment.ExperimentData;
import edu.rice.cs.hpc.viewer.experiment.ExperimentManager;

//import edu.rice.cs.HPCVision.data.Experiment.*;
/**
 * Our sample action implements workbench action delegate.
 * The action proxy will be created by the workbench and
 * shown in the UI. When the user tries to use the action,
 * this delegate will be created and execution will be 
 * delegated to it. 
 * @see IWorkbenchWindowActionDelegate
 */
public class LoadExperiment implements IWorkbenchWindowActionDelegate {
	private ExperimentManager expFile;
	private IWorkbenchWindow window;
	/**
	 * The constructor.
	 */
	public LoadExperiment() {
		super();
	}

	/**
	 * The action has been activated. The argument of the
	 * method represents the 'real' action sitting
	 * in the workbench UI.
	 * @see IWorkbenchWindowActionDelegate#run
	 */
	public void run(IAction action) {
    	// open a file dialog
		expFile.openFileExperiment(this.window.getShell());
	}

	/**
	 * Selection in the workbench has been changed. We 
	 * can change the state of the 'real' action here
	 * if we want, but this can only happen after 
	 * the delegate has been created.
	 * @see IWorkbenchWindowActionDelegate#selectionChanged
	 */
	public void selectionChanged(IAction action, ISelection selection) {
	}

	/**
	 * We can use this method to dispose of any system
	 * resources we previously allocated.
	 * @see IWorkbenchWindowActionDelegate#dispose
	 */
	public void dispose() {
	}

	/**
	 * We will cache window object in order to
	 * be able to provide parent shell for the message dialog.
	 * @see IWorkbenchWindowActionDelegate#init
	 */
	public void init(IWorkbenchWindow window) {
		expFile = ExperimentData.getInstance().getExperimentManager();
		this.window = window;
	}
	
} 