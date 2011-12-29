/**
 * 
 */
package edu.rice.cs.hpc.viewer.scope;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.CoolBar;
import org.eclipse.swt.widgets.CoolItem;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Label;

import org.eclipse.ui.IWorkbenchWindow;

import edu.rice.cs.hpc.data.experiment.Experiment;
import edu.rice.cs.hpc.data.experiment.scope.Scope;
import edu.rice.cs.hpc.viewer.resources.Icons;
import edu.rice.cs.hpc.viewer.util.ColumnProperties;
import edu.rice.cs.hpc.data.experiment.scope.RootScope;
import edu.rice.cs.hpc.viewer.util.Utilities;
import edu.rice.cs.hpc.viewer.window.Database;
import edu.rice.cs.hpc.viewer.window.ViewerWindow;
import edu.rice.cs.hpc.viewer.window.ViewerWindowManager;
import edu.rice.cs.hpc.data.experiment.metric.BaseMetric;

/**
 * General actions GUI for basic scope views like caller view and calling context view
 * This GUI includes tool bar for zooms, add derived metrics, show/hide columns, and hot call path 
 *
 */
public class ScopeViewActionsGUI implements IScopeActionsGUI {

	final static private String COLUMN_DATA_WIDTH = "w"; 
    //======================================================
	// ------ DATA ----------------------------------------
    //======================================================
	// GUI STUFFs
    protected ScopeTreeViewer 	treeViewer;		  	// tree for the caller and callees
    private ScopeViewActions objViewActions;
    private TreeViewerColumn []colMetrics;	// metric columns
    private Shell shell;
    private IWorkbenchWindow objWindow;

    // variable declaration uniquely for coolbar
	private ToolItem tiZoomin;		// zoom-in button
	private ToolItem tiZoomout ;	// zoom-out button
	private ToolItem tiColumns ;	// show/hide button
	private ToolItem tiHotCallPath;
	private ToolItem tiAddExtMetric;
	private Label lblMessage;
	
	//------------------------------------DATA
	protected Scope nodeTopParent; // the current node which is on the top of the table (used as the aggregate node)
	protected Experiment 	myExperiment;		// experiment data	
	protected RootScope 		myRootScope;		// the root scope of this view

    // ----------------------------------- CONSTANTS
    private Color clrGREEN, clrYELLOW, clrRED, clrNORMAL;
    
    /**
     * Constructor initializing the data
     * @param shellGUI
     * @param objViewer
     * @param fontMetricColumn
     * @param objActions
     */
	public ScopeViewActionsGUI(Shell objShell, IWorkbenchWindow window, Composite parent, 
			ScopeViewActions objActions) {

		this.objViewActions = objActions;
		this.shell = objShell;
		this.objWindow = window;
		
		this.clrNORMAL = shell.getBackground();
		this.clrYELLOW = new Color(shell.getDisplay(),255,255,0);
		this.clrRED = new Color(shell.getDisplay(), 250,128,114);
		this.clrGREEN = new Color(shell.getDisplay(), 153,255,153);
	}

	/**
	 * Method to start to build the GUI for the actions
	 * @param parent
	 * @return
	 */
	public Composite buildGUI(Composite parent, CoolBar coolbar) {
		Composite newParent = this.addToolBarAction(coolbar);
		this.finalizeToolBar(parent, coolbar);
		return newParent;
	}

	/**
	 * IMPORTANT: need to call this method once the content of tree is changed !
	 * Warning: call only this method when the tree has been populated !
	 * @param exp
	 * @param scope
	 * @param columns
	 */
	public void updateContent(Experiment exp, RootScope scope, TreeViewerColumn []columns) {
		// save the new data and properties
		this.myExperiment = exp;
		this.myRootScope = scope;
		this.colMetrics = columns;
		//this.setLevelText(scope.getTreeNode().iLevel);	// @TODO: initialized with root level
		
		// actions needed when a new experiment is loaded
		this.resizeTableColumns();	// we assume the data has been populated
        this.enableActions();
        // since we have a new content of experiment, we need to display 
        // the aggregate metrics
        this.displayRootExperiment();
	}
	
    //======================================================
    public void setTreeViewer(ScopeTreeViewer tree) {
    	this.treeViewer = tree;
    }

    /**
     * Inserting a "node header" on the top of the table to display
     * either aggregate metrics or "parent" node (due to zoom-in)
     * TODO: we need to shift to the left a little bit
     * @param nodeParent
     */
    public void insertParentNode(Scope nodeParent) {
    	Scope scope = nodeParent;
    	// Bug fix: avoid using list of columns from the experiment
    	// formerly: .. = this.myExperiment.getMetricCount() + 1;
    	int nbColumns = this.colMetrics.length; 	// columns in base metrics
    	String []sText = new String[nbColumns+1];
    	sText[0] = new String(scope.getName());
    	// --- prepare text for base metrics
    	// get the metrics for all columns
    	for (int i=0; i< nbColumns; i++) {
    		// we assume the column is not null
    		Object o = this.colMetrics[i].getColumn().getData();
    		if(o instanceof BaseMetric) {
    			BaseMetric metric = (BaseMetric) o;//this.myExperiment.getMetric(i);
    			sText[i+1] = metric.getMetricTextValue(scope.getMetricValue(metric));
    			//sText[i+1] = metric.getMetricTextValue(scope);
    		}
    	}
    	
    	// draw the root node item
    	Utilities.insertTopRow(treeViewer, Utilities.getScopeNavButton(scope), sText);
    	this.nodeTopParent = nodeParent;
    }
    
    /**
     * Restoring the "node header" in case of refresh method in the viewer
     */
    private void restoreParentNode() {
    	if(this.nodeTopParent != null) {
    		this.insertParentNode(this.nodeTopParent);
    	}
    }
	/**
	 * Add the aggregate metrics item on the top of the tree
	 */
    protected void displayRootExperiment() {
    	Scope  node = (Scope) this.myRootScope;
    	this.insertParentNode(node);
    }
	
	/**
	 * Resize the columns automatically
	 * ATT: Please call this method once the data has been populated
	 */
	public void resizeTableColumns() {
        // resize the column according to the data size
		int nbCols = this.colMetrics.length;
        for (int i=0; i<nbCols; i++) {
        	TreeColumn column = this.colMetrics[i].getColumn();
        	// do NOT resize if the column is hidden
        	if(column.getWidth()>1)
        		column.pack();
        }
	}

	//======================================================
    // ................ GUI and LAYOUT ....................
    //======================================================
	
	/**
	 * Show a message with information style (with green background)
	 */
	public void showInfoMessage(String sMsg) {
		this.lblMessage.setBackground(this.clrGREEN);
		this.lblMessage.setText(sMsg);
	}
	
	/**
	 * Show a warning message (with yellow background).
	 * The caller has to remove the message and restore it to the original state
	 * by calling restoreMessage() method
	 */
	public void showWarningMessagge(String sMsg) {
		this.lblMessage.setBackground(this.clrYELLOW);
		this.lblMessage.setText(sMsg);
	}
	
	/**
	 * Show an error message on the message bar. It is the caller responsibility to 
	 * remove the message
	 * @param sMsg
	 */
	public void showErrorMessage(String sMsg) {
		this.lblMessage.setBackground(this.clrRED);
		this.lblMessage.setText(" " + sMsg);
	}

	/**
	 * Restore the message bar into the original state
	 */
	public void restoreMessage() {
		if(this.lblMessage != null) {
			this.lblMessage.setBackground(this.clrNORMAL);
			this.lblMessage.setText("");
		}
	}
	/**
	 * Reset the button and actions into disabled state
	 */
	public void resetActions() {
		this.tiColumns.setEnabled(false);
		this.tiAddExtMetric.setEnabled(false);
		// disable zooms and hot-path buttons
		this.disableNodeButtons();
	}
	
	/**
	 * Enable the some actions (resize and column properties) actions for this view
	 */
	public void enableActions() {
		this.tiColumns.setEnabled(true);
		this.tiAddExtMetric.setEnabled(true);
	}
	    
	/**
	 * Hiding a metric column
	 * @param iColumnPosition: the index of the metric
	 */
	public void hideMetricColumn(int iColumnPosition) {
			int iWidth = this.colMetrics[iColumnPosition].getColumn().getWidth();
   			if(iWidth > 0) {
       			Integer objWidth = Integer.valueOf(iWidth); 
       			// Laks: bug no 131: we need to have special key for storing the column width
       			this.colMetrics[iColumnPosition].getColumn().setData(COLUMN_DATA_WIDTH,objWidth);
       			this.colMetrics[iColumnPosition].getColumn().setWidth(0);
   			}
	}
	
    /**
     * Show column properties (hidden, visible ...)
     */
    private void showColumnsProperties() {
    	ColumnProperties objProp = new ColumnProperties(this.objWindow.getShell(), this.colMetrics);
    	objProp.open();
    	if(objProp.getReturnCode() == org.eclipse.jface.dialogs.IDialogConstants.OK_ID) {
        	boolean result[] = objProp.getResult();
        	boolean isAppliedToAllViews = objProp.getStatusApplication();
        	// apply to all views ?
        	if(isAppliedToAllViews) {
        		// apply the changes for all views
        		this.showHideColumnsAllViews(result);
        	} else {
        		// apply the changes only in this view
        		this.setColumnsStatus(result);
        	}
   		}
    }
    
    /**
     * Apply the show/hidden columns on all views
     * @param status
     */
    private void showHideColumnsAllViews(boolean []status) {
		// get our database file and the and the class that contains its information
		String sFilename = myExperiment.getXMLExperimentFile().getPath();
		ViewerWindow vWin = ViewerWindowManager.getViewerWindow(this.objWindow);
		if (vWin == null) {
			System.out.printf("ScopeViewActionsGUI.showHideColumnsAllViews: ViewerWindow class not found\n");
			return;
		}
		Database db = vWin.getDb(sFilename);
		if (db == null) {
			System.out.printf("ScopeViewActionsGUI.showHideColumnsAllViews: Database class not found\n");
			return;
		}

		// get the views created for our database
		BaseScopeView arrScopeViews[] = db.getExperimentView().getViews();
		for(int i=0; i<arrScopeViews.length; i++) {
			arrScopeViews[i].getViewActions().setColumnStatus(status);
		}
    }
    
    /**
     * Change the column status (hide/show) in this view only
     */
    public void setColumnsStatus(boolean []status) {
       	for(int i=0;i<status.length;i++) {
       		// hide this column
       		if(!status[i]) {
       			this.hideMetricColumn(i);
       		} else {
       			// display the hidden column
       			// Laks: bug no 131: we need to have special key for storing the column width
        		Object o = this.colMetrics[i].getColumn().getData(COLUMN_DATA_WIDTH);
       			if((o != null) && (o instanceof Integer) ) {
       				int iWidth = ((Integer)o).intValue();
           			this.colMetrics[i].getColumn().setWidth(iWidth);
       			}
       		}
       	}
    }
    /**
     * Add a new metric column
     * @param colMetric
     */
    public void addMetricColumns(TreeViewerColumn colMetric) {
    	int nbCols = this.colMetrics.length + 1;
    	TreeViewerColumn arrColumns[] = new TreeViewerColumn[nbCols];
    	for(int i=0;i<nbCols-1;i++)
    		arrColumns[i] = this.colMetrics[i];
    	arrColumns[nbCols-1] = colMetric;
    	this.colMetrics = arrColumns;
    	// when adding a new column, we have to refresh the viewer
    	// and this means we have to recompute again the top row of the table
    	this.restoreParentNode();
    }
    
    public TreeViewerColumn[] getMetricColumns() {
    	return this.colMetrics;
    }
    //======================================================
    // ................ BUTTON ............................
    //======================================================

    /**
     * Disable actions that need a selected node
     */
    public void disableNodeButtons() {
    	this.tiZoomin.setEnabled(false);
    	this.tiZoomout.setEnabled(false);
    	this.tiHotCallPath.setEnabled(false);
    }

    /*
     * (non-Javadoc)
     * @see edu.rice.cs.hpc.viewer.scope.IScopeActionsGUI#enableHotCallPath(boolean)
     */
	public void enableHotCallPath(boolean enabled) {
		this.tiHotCallPath.setEnabled(enabled);
	}

	/*
	 * (non-Javadoc)
	 * @see edu.rice.cs.hpc.viewer.scope.IScopeActionsGUI#enableZoomIn(boolean)
	 */
	public void enableZoomIn(boolean enabled) {
		this.tiZoomin.setEnabled(enabled);
	}

	/*
	 * (non-Javadoc)
	 * @see edu.rice.cs.hpc.viewer.scope.IScopeActionsGUI#enableZoomOut(boolean)
	 */
	public void enableZoomOut(boolean enabled) {
		this.tiZoomout.setEnabled(enabled);
	}
    

    //======================================================
    // ................ CREATION ............................
    //======================================================
    /**
     * Creating an item for the existing coolbar
     * @param coolBar
     * @param toolBar
     */
    protected void createCoolItem(CoolBar coolBar, Control toolBar) {
    	CoolItem coolItem = new CoolItem(coolBar, SWT.NULL);
    	coolItem.setControl(toolBar);
    	org.eclipse.swt.graphics.Point size =
    		toolBar.computeSize( SWT.DEFAULT,
    	                           SWT.DEFAULT);
    	org.eclipse.swt.graphics.Point coolSize = coolItem.computeSize (size.x, size.y);
    	coolItem.setSize(coolSize);    	
    }
    
    /*
     * 
     */
    protected void finalizeToolBar(Composite parent, CoolBar coolBar) {
    	// message text
    	lblMessage = new Label(parent, SWT.NONE);
    	lblMessage.setText("");

    	// but the message label yes
    	GridDataFactory.fillDefaults().grab(true, false).applyTo(lblMessage);
    	// the coolbar part shouldn't be expanded 
    	GridDataFactory.fillDefaults().grab(false, false).applyTo(coolBar);
    	// now the toolbar area should be able to be expanded automatically
    	GridDataFactory.fillDefaults().grab(true, false).applyTo(parent);
    	// two kids for toolbar area: coolbar and message label
    	GridLayoutFactory.fillDefaults().numColumns(2).generateLayout(parent);

    }
	/**
     * Create a toolbar region on the top of the view. This toolbar will be used to host some buttons
     * to make actions on the treeview.
     * @param aParent
     * @return Composite of the view. The tree should be based on this composite.
     */
    public Composite addToolBarAction(CoolBar coolbar) {
    	// prepare the toolbar
    	ToolBar toolbar = new ToolBar(coolbar, SWT.FLAT);
    	Icons iconsCollection = Icons.getInstance();
    	    	
    	// zoom in
    	tiZoomin = new ToolItem(toolbar, SWT.PUSH);
    	tiZoomin.setToolTipText("Zoom-in the selected node");
    	tiZoomin.setImage(iconsCollection.imgZoomIn);
    	tiZoomin.addSelectionListener(new SelectionAdapter() {
      	  	public void widgetSelected(SelectionEvent e) {
      	  	objViewActions.zoomIn();
      	  	}
      	});
    	
    	// zoom out
    	tiZoomout = new ToolItem(toolbar, SWT.PUSH);
    	tiZoomout.setToolTipText("Zoom-out the selected node");
    	tiZoomout.setImage(iconsCollection.imgZoomOut);
    	tiZoomout.addSelectionListener(new SelectionAdapter() {
    	  public void widgetSelected(SelectionEvent e) {
    		  objViewActions.zoomOut();
    	  }
    	});
    	
    	new ToolItem(toolbar, SWT.SEPARATOR);
    	// hot call path
    	this.tiHotCallPath= new ToolItem(toolbar, SWT.PUSH);
    	tiHotCallPath.setToolTipText("Expand the hot path below the selected node");
    	tiHotCallPath.setImage(iconsCollection.imgFlame);
    	tiHotCallPath.addSelectionListener(new SelectionAdapter() {
    	  public void widgetSelected(SelectionEvent e) {
    		  objViewActions.showHotCallPath();
    	  }
    	});
    	
    	this.tiAddExtMetric = new ToolItem(toolbar, SWT.PUSH);
    	tiAddExtMetric.setImage(iconsCollection.imgExtAddMetric);
    	tiAddExtMetric.setToolTipText("Add a new derived metric");
    	tiAddExtMetric.addSelectionListener(new SelectionAdapter(){
    		public void widgetSelected(SelectionEvent e) {
    			objViewActions.addExtNewMetric();
    		}
    	});

    	new ToolItem(toolbar, SWT.SEPARATOR);
    	
    	this.tiColumns = new ToolItem(toolbar, SWT.PUSH);
    	tiColumns.setImage(iconsCollection.imgColumns);
    	tiColumns.setToolTipText("Hide/show columns");
    	tiColumns.addSelectionListener(new SelectionAdapter() {
        	  public void widgetSelected(SelectionEvent e) {
        		  showColumnsProperties();
        	  }
        	});
    	new ToolItem(toolbar, SWT.SEPARATOR);
    	
    	// ------------------------------- export CSV ------
    	ToolItem tiCSV = new ToolItem(toolbar, SWT.PUSH);
    	tiCSV.setImage( iconsCollection.imgExportCSV );
    	tiCSV.setToolTipText( "Export the current view into a comma separated value file" );
    	tiCSV.addSelectionListener( new SelectionAdapter() {
    		public void widgetSelected(SelectionEvent e) {
    			exportCSV();
    		}
    	});
    	
    	// ------------ Text fonts
    	// bigger font
    	ToolItem tiFontBigger = new ToolItem (toolbar, SWT.PUSH);
    	tiFontBigger.setImage(iconsCollection.imgFontBigger);
    	tiFontBigger.setToolTipText("Increase font size");
    	tiFontBigger.addSelectionListener( new SelectionAdapter() {
      	  public void widgetSelected(SelectionEvent e) {
      		  Utilities.increaseFont(objWindow);
    	  }
    	});

    	// smaller font
    	ToolItem tiFontSmaller = new ToolItem (toolbar, SWT.PUSH);
    	tiFontSmaller.setImage(iconsCollection.imgFontSmaller);
    	tiFontSmaller.setToolTipText("Decrease font size");
    	tiFontSmaller.addSelectionListener( new SelectionAdapter() {
      	  public void widgetSelected(SelectionEvent e) {
      		  Utilities.DecreaseFont(objWindow);
    	  }
    	});
    	
    	// set the coolitem
    	this.createCoolItem(coolbar, toolbar);
    	

    	return toolbar;
    }

    /**
     * Constant comma separator
     */
    final private String COMMA_SEPARATOR = ",";
    
    /**
     * Method to export the displayed items in the current view into a CSV format file
     */
	private void exportCSV() {
		FileDialog fileDlg = new FileDialog(this.shell, SWT.SAVE);
		fileDlg.setFileName(this.myExperiment.getName() + ".csv");
		fileDlg.setFilterExtensions(new String [] {"*.csv", "*.*"});
		fileDlg.setText("Save the data in the table to a file (CSV format)");
		final String sFilename = fileDlg.open();
		if ( (sFilename != null) && (sFilename.length()>0) ) {
			try {
				this.shell.getDisplay().asyncExec( new Runnable() {

					public void run() {
						try {
							// -----------------------------------------------------------------------
							// Check if the status of the file
							// -----------------------------------------------------------------------
							File objFile = new File( sFilename );
							if ( objFile.exists() ) {
								if ( !MessageDialog.openConfirm( shell, "File already exists" , 
									sFilename + ": file already exist. Do you want to replace it ?") )
									return;
							}
							// WARNING: java.io.File seems always fail to verify writable status on Linux !
							/*
							if ( !objFile.canWrite() ) {
								MessageDialog.openError( shell, "Error: Unable to write the file", 
										sFilename + ": File is not writable ! Please check if you have right to write in the directory." );
								return;
							} */

							// -----------------------------------------------------------------------
							// prepare the file
							// -----------------------------------------------------------------------
							showInfoMessage( "Writing to file: "+sFilename);
							FileWriter objWriter = new FileWriter( objFile );
							BufferedWriter objBuffer = new BufferedWriter (objWriter);
							
							// -----------------------------------------------------------------------
							// writing to the file
							// -----------------------------------------------------------------------
							
							// write the title
							String sTitle = treeViewer.getColumnTitle(0, COMMA_SEPARATOR);
							objBuffer.write(sTitle + Utilities.NEW_LINE);

							// write the top row items
							String sTopRow[] = Utilities.getTopRowItems(treeViewer);
							// tricky: add '"' for uniting the text in the spreadsheet
							sTopRow[0] = "\"" + sTopRow[0] + "\"";	
							sTitle = treeViewer.getTextBasedOnColumnStatus(sTopRow, COMMA_SEPARATOR, 0, 0);
							objBuffer.write(sTitle + Utilities.NEW_LINE);

							// write the content text
							ArrayList<TreeItem> items = new ArrayList<TreeItem>();
							internalCollectExpandedItems(items, treeViewer.getTree().getItems());
							String sText = objViewActions.getContent( items.toArray(new TreeItem[items.size()]), 
									COMMA_SEPARATOR);
							objBuffer.write(sText);
							
							// -----------------------------------------------------------------------
							// End of the process
							// -----------------------------------------------------------------------							
							objBuffer.close();
							restoreMessage();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					
				});
			} catch ( SWTException e ) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * This method is a modified version of AbstractViewer.internalCollectExpandedItems()
	 * @param result
	 * @param items
	 */
	private void internalCollectExpandedItems(List<TreeItem> result, TreeItem []items) {
		if (items != null)
			for (int i = 0; i < items.length; i++) {
				TreeItem itemChild = items[i];
				if (itemChild.getData() instanceof Scope)
					result.add(itemChild);
				if (itemChild.getExpanded())
					internalCollectExpandedItems(result, itemChild.getItems());
			}
	}

}
