package edu.rice.cs.hpc.traceviewer.ui;

import java.util.ArrayList;
import java.util.Vector;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;

import edu.rice.cs.hpc.traceviewer.painter.Position;
import edu.rice.cs.hpc.traceviewer.spaceTimeData.SpaceTimeData;
import edu.rice.cs.hpc.traceviewer.timeline.ProcessTimeline;
import edu.rice.cs.hpc.traceviewer.util.Constants;
import edu.rice.cs.hpc.traceviewer.util.Debugger;

/**************************************************
 * A viewer for DataViewer.
 *************************************************/
public class DataViewer extends TableViewer
{
	private SpaceTimeData stData;
	
	private final TableViewerColumn viewerColumn;
		
	public DataViewer(Composite parent, final HPCDataView dataview)
	{
		super(parent, SWT.SINGLE | SWT.NO_SCROLL);
		
        final Table dataTbl = this.getTable();
        
        GridData data = new GridData(GridData.FILL_BOTH);
        dataTbl.setLayoutData(data);
        
        //------------------------------------------------
        // add content provider
        //------------------------------------------------
        this.setContentProvider( new IStructuredContentProvider(){

			public void dispose() {}

			public void inputChanged(Viewer viewer, Object oldInput, Object newInput) { }

			public Object[] getElements(Object inputElement) {
				if (inputElement instanceof ArrayList<?>) {
					Object o[] = ((ArrayList<?>) inputElement).toArray();
					return o;
				}
				return null;
			}
        	
        });
        
        dataTbl.setVisible(false);
        final DataViewer dataviewer = this;
		dataTbl.addListener(SWT.Selection, new Listener(){
			public void handleEvent(Event event)
			{
				int dataIdx = dataTbl.getSelectionIndex();
				if (dataIdx != Constants.dataIdxNULL && dataIdx != stData.getData()) {
					stData.updateData(dataIdx, dataviewer);
				}
			}
		});
		
        //------------------------------------------------
        // add label provider
        //------------------------------------------------

		final ColumnLabelProvider myLableProvider = new ColumnLabelProvider() {
        	public Image getImage(Object element) {
        		if (element instanceof String) {
        			Image img = null;
        			if (stData != null)
        				img = stData.getColorTable().getImage((String)element);
        			return img;
        		}
        		
				return null;        		
        	}
        	
        	public String getText(Object element)
        	{
        		if (element instanceof String)
        			return (String) element;
        		return null;
        	}
        	
        	public String getToolTipText(Object element)
        	{
        		return this.getText(element);
        	}
        	
        	public int getToolTipDisplayDelayTime(Object object)
        	{
        		return 200;
        	}
		};
		viewerColumn = new TableViewerColumn(this, SWT.NONE);
		viewerColumn.setLabelProvider(myLableProvider);
		viewerColumn.getColumn().setWidth(100);
		ColumnViewerToolTipSupport.enableFor(dataviewer, ToolTip.NO_RECREATE);
	}
	
	
	/***
	 * set new database
	 * @param _stData
	 */
	public void updateView(SpaceTimeData _stData) 
	{
		this.stData = _stData;
		this.setSample(stData.getPosition(), this.stData.getDepth(), this.stData.getData());
		this.getTable().setVisible(true);
	}
	
	/**********************************************************************
	 * Sets the sample displayed on the data viewer to be the one
	 * that most closely corresponds to (closeTime, process). Additionally,
	 * sets the depth to _depth.
	 *********************************************************************/
	public void setSample(Position position, int depth, int dataIdx)
	{
		if (position.time == -20)
			return;
		
		//-------------------------------------------------------------------------------------------
		// dirty hack: cf. edu.rice.cs.hpc.traceviewer.ui.CallStackViewer.setSample()
		//-------------------------------------------------------------------------------------------
		int adjustedPosition = position.process; 

		ProcessTimeline ptl = stData.getProcess(adjustedPosition);
		if (ptl != null) {
			int sample = ptl.findMidpointBefore(position.time);
			
			Vector<String> dataVec = new Vector<String>();
			if (sample >= 0) {
				dataVec = ptl.getCallPath(sample, depth).getDataNames();
			}
			
			this.setInput(new ArrayList<String>(dataVec));
			this.selectData(dataIdx);
			
			viewerColumn.getColumn().pack();
		}
		else {
			System.err.println("Internal error: unable to get process " + adjustedPosition+"\tProcess range: " +
					stData.getBegProcess() + "-" + stData.getEndProcess() + " \tNum Proc: " + stData.getNumberOfDisplayedProcesses());
			Debugger.printTrace("CSV traces: ");
		}
	}
	
	/**Sets the viewer to data object 'dataIdx'.*/
	public void setData(int dataIdx)
	{		
		this.selectData(dataIdx);
	}
	
	/*****
	 * Select a specified data object
	 * @param dataIdx
	 */
	private void selectData(final int dataIdx)
	{
		this.getTable().select(dataIdx);
		this.getTable().redraw();
	}
}
