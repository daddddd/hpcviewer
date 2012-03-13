package edu.rice.cs.hpc.viewer.experiment;

import org.eclipse.jface.dialogs.MessageDialog;

import edu.rice.cs.hpc.data.experiment.*; 
import edu.rice.cs.hpc.viewer.framework.Activator;
import edu.rice.cs.hpc.viewer.scope.BaseScopeView;
import edu.rice.cs.hpc.viewer.scope.ScopeView;
import edu.rice.cs.hpc.viewer.scope.CallerScopeView;
import edu.rice.cs.hpc.viewer.scope.FlatScopeView;
import edu.rice.cs.hpc.viewer.util.PreferenceConstants;
import edu.rice.cs.hpc.viewer.util.WindowTitle;
import edu.rice.cs.hpc.viewer.window.Database;
import edu.rice.cs.hpc.viewer.window.ViewerWindow;
import edu.rice.cs.hpc.viewer.window.ViewerWindowManager;

import edu.rice.cs.hpc.data.experiment.scope.RootScope;
import edu.rice.cs.hpc.data.experiment.scope.RootScopeType;
import edu.rice.cs.hpc.data.experiment.scope.TreeNode;

import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
/**
 * Class to be used as an interface between the GUI and the data experiment
 * This class should be called from an eclipse view !
 * @author laksono
 *
 */
public class ExperimentView {
	//private ExperimentData dataExperiment;
	private org.eclipse.ui.IWorkbenchPage objPage;		// workbench current page
	/**
	 * List of registered views in the current experiment
	 */
	protected BaseScopeView []arrScopeViews;
	
	private void init() {
/*		if(this.dataExperiment == null) {
			this.dataExperiment = ExperimentData.getInstance(this.objPage.getWorkbenchWindow());
		}*/
	}
	/**
	 * Constructor for Data experiment. Needed to link with the view
	 * @param objTarget: the scope view to link with
	 */
	public ExperimentView(org.eclipse.ui.IWorkbenchPage objTarget) {
		if(objTarget != null) {
			this.objPage = objTarget;
			this.init();
		} else {
			System.err.println("EV Error: active page is null !");
		}
	}
	

	
	/**
	 * A wrapper of loadExperiment() by adding some processing and generate the views
	 * @param sFilename
	 * @param bCallerView: flag to indicate if the caller view can be displayed
	 */
	public boolean loadExperimentAndProcess(String sFilename, boolean bCallerView) {
		Experiment experiment = this.loadExperiment(sFilename);

		if(experiment != null) {
			try {
				experiment.postprocess(bCallerView);
			} catch (java.lang.OutOfMemoryError e) {
				MessageDialog.openError(this.objPage.getWorkbenchWindow().getShell(), "Out of memory", 
						"hpcviewer requires more heap memory allocation.\nJava heap size can be increased by modifying \"-Xmx\" parameter in hpcivewer.ini file.");
			}
	        this.generateView(experiment);
	        return true;
		}
		return false;
	}
	
	/**
	 * A wrapper of loadExperiment() by adding some processing and generate the views
	 * The routine will first look at the user preference for displaying caller view 
	 * Then call the normal loadExperimentAndProcess routine.
	 * @param sFilename
	 */
	public boolean loadExperimentAndProcess(String sFilename) {
		ScopedPreferenceStore objPref = (ScopedPreferenceStore)Activator.getDefault().getPreferenceStore();
		boolean bCallerView = objPref.getBoolean(PreferenceConstants.P_CALLER_VIEW);
		return this.loadExperimentAndProcess(sFilename, bCallerView);
	}
	
	/**
	 * Load an XML experiment file based on the filename (uncheck for its inexistence)
	 * This method will display errors whenever encountered.
	 * This method does not include post-processing and generating scope views
	 * @param sFilename: the xml experiment file
	 */
	public Experiment loadExperiment(String sFilename) {
		Experiment experiment = null;
		// first view: usually already created by default by the perspective
		org.eclipse.swt.widgets.Shell objShell = this.objPage.getWorkbenchWindow().getShell();
		try
		{
			experiment = new Experiment(new java.io.File(sFilename));
			experiment.open();

		} catch(java.io.FileNotFoundException fnf)
		{
			System.err.println("File not found:" + sFilename + "\tException:"+fnf.getMessage());
			MessageDialog.openError(objShell, "Error:File not found", "Cannot find the file "+sFilename);
			experiment = null;
		}
		catch(java.io.IOException io)
		{
			System.err.println("IO error:" +  sFilename + "\tIO msg: " + io.getMessage());
			MessageDialog.openError(objShell, "Error: Unable to read", "Cannot read the file "+sFilename);
			experiment = null;
		}
		catch(InvalExperimentException ex)
		{
			String where = sFilename + " " + " " + ex.getLineNumber();
			System.err.println("$" +  where);
			MessageDialog.openError(objShell, "Incorrect Experiment File", "File "+sFilename 
					+ " has incorrect tag at line:"+ex.getLineNumber());
			experiment = null;
		} 
		catch(NullPointerException npe)
		{
			System.err.println("$" + npe.getMessage() + sFilename);
			MessageDialog.openError(objShell, "File is invalid", "File has null pointer:"
					+sFilename + ":"+npe.getMessage());
			experiment = null;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return experiment;
	}
	
	/**
	 * Retrieve the list of all used views
	 * @return list of views
	 */
	public BaseScopeView[] getViews() {
		return this.arrScopeViews;
	}
	
	/**
	 * Generate multiple views for an experiment depending on the number of root scopes
	 * @param experiment Experiment data
	 */
	public void generateView(Experiment experiment) {
		
        IWorkbenchWindow window = this.objPage.getWorkbenchWindow();
		// register this new database with our viewer window
		ViewerWindow vWin = ViewerWindowManager.getViewerWindow(window);
		if (vWin == null) {
			System.out.printf("ExperimentManager.setExperiment: ViewerWindow class not found\n");
		}

		// Create a database object to record information about this particular database 
		// being opened.  This information is needed to be able to close and clean up 
		// resources from this database.
		Database db = new Database();
		db.setExperimentView(this);
		// add the database to this viewer window
		if (vWin.addDatabase(db) < 0) {
			return;     // we already issued a dialog message to notify user the open failed.
		}

		db.setExperiment(experiment);		// set the experiment class used for the database
        
		// the database index has values from 1-5 and is used in view titles
		final int dbIdx = vWin.getDbNum(experiment);
		// the view index has values from 0-4 and is used to index arrays (layout folders and possibly others)
		final String viewIdx = Integer.toString(dbIdx);

		// next, we retrieve all children of the scope and display them in separate views
		TreeNode []rootChildren = experiment.getRootScopeChildren();
		int nbChildren = rootChildren.length;
		arrScopeViews = new BaseScopeView[nbChildren];

		for(int k=0;nbChildren>k;k++)
		{
			RootScope child = (RootScope) rootChildren[k];
			try {
				BaseScopeView objView; 

				// every root scope type has its own view
					if(child.getType() == RootScopeType.Flat) {
						objView = (BaseScopeView) this.objPage.showView(FlatScopeView.ID, viewIdx, IWorkbenchPage.VIEW_VISIBLE); 
					} else if(child.getType() == RootScopeType.CallerTree) {
						objView = (BaseScopeView) this.objPage.showView(CallerScopeView.ID , viewIdx, IWorkbenchPage.VIEW_VISIBLE); 
					} else {
						// using VIEW_ACTIVATE will cause this one to end up with focus (on top).
						objView = (BaseScopeView)this.objPage.showView(ScopeView.ID , viewIdx, IWorkbenchPage.VIEW_ACTIVATE); 
					}
				objView.setInput(db, child);
				arrScopeViews[k] = objView;
			} catch (PartInitException e) {
				e.printStackTrace();
			}
		}
		
		// update the window title if necessary
		WindowTitle.refreshAllTitle(window, experiment);		
	}
}
