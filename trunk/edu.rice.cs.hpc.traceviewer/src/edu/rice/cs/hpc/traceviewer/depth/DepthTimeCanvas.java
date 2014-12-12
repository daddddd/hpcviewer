package edu.rice.cs.hpc.traceviewer.depth;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IOperationHistoryListener;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.commands.operations.OperationHistoryEvent;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import edu.rice.cs.hpc.common.ui.Util;
import edu.rice.cs.hpc.traceviewer.operation.BufferRefreshOperation;
import edu.rice.cs.hpc.traceviewer.operation.PositionOperation;
import edu.rice.cs.hpc.traceviewer.operation.TraceOperation;
import edu.rice.cs.hpc.traceviewer.operation.ZoomOperation;
import edu.rice.cs.hpc.traceviewer.painter.AbstractTimeCanvas;
import edu.rice.cs.hpc.traceviewer.painter.ISpaceTimeCanvas;
import edu.rice.cs.hpc.traceviewer.painter.ImageTraceAttributes;
import edu.rice.cs.hpc.traceviewer.spaceTimeData.Frame;
import edu.rice.cs.hpc.traceviewer.spaceTimeData.Position;
import edu.rice.cs.hpc.traceviewer.spaceTimeData.SpaceTimeDataController;
import edu.rice.cs.hpc.traceviewer.util.Utility;
import edu.rice.cs.hpc.traceviewer.data.util.Constants;
import edu.rice.cs.hpc.traceviewer.data.util.Debugger;

/**A view for displaying the depthview.*/
public class DepthTimeCanvas extends AbstractTimeCanvas 
implements IOperationHistoryListener, ISpaceTimeCanvas
{	
	final private ExecutorService threadExecutor;

	private SpaceTimeDataController stData;
	private int currentProcess = Integer.MIN_VALUE;
	
	/****
	 * this variable will store the visible canvas everytime we resize or
	 * we need to refresh the paint.
	 * This is needed to avoid access to getClientArea() which require UI thread
	 * to access.
	 */
	private Rectangle clientArea;
	
	final private DepthViewPaint depthPaint;

	/********************
	 * constructor to create this canvas
	 * 
	 * @param composite : the parent composite
	 */
	public DepthTimeCanvas(Composite composite)
    {
		super(composite, SWT.NONE);
		
		threadExecutor = Executors.newFixedThreadPool( Utility.getNumThreads(0) ); 
		
		depthPaint = new DepthViewPaint(Util.getActiveWindow(), threadExecutor, this);		
		
		addDisposeListener( new DisposeListener() {
			
			@Override
			public void widgetDisposed(DisposeEvent e) {
				dispose();				
			}
		});
		addControlListener( new ControlListener() {
			
			@Override
			public void controlResized(ControlEvent e) {
				clientArea = getClientArea();
			}
			
			@Override
			public void controlMoved(ControlEvent e) {}
		});
	}
	
	/****
	 * new data update
	 * @param _stData
	 */
	public void updateView(SpaceTimeDataController stData)
	{
		super.init();
		setVisible(true);
		
		if (this.stData == null) {
			// just initialize once
			TraceOperation.getOperationHistory().addOperationHistoryListener(this);
		}
		this.stData = stData; 		
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.swt.events.PaintListener#paintControl(org.eclipse.swt.events.PaintEvent)
	 */
	public void paintControl(PaintEvent event)
	{
		if (this.stData == null || imageBuffer == null)
			return;
		
		super.paintControl(event);
		
		final long topLeftPixelX = Math.round(stData.getAttributes().getTimeBegin()*getScalePixelsPerTime());
		
		final int viewHeight = clientArea.height;

		//--------------------
		//draws cross hairs
		//--------------------
		
		event.gc.setBackground(Constants.COLOR_WHITE);
		event.gc.setAlpha(240);
		
		long selectedTime = stData.getAttributes().getFrame().position.time;
		
		int topPixelCrossHairX = (int)(Math.round(selectedTime*getScalePixelsPerTime())-2-topLeftPixelX);
		event.gc.fillRectangle(topPixelCrossHairX,0,4,viewHeight);
		
		final int maxDepth = stData.getMaxDepth();
		final int depth    = stData.getAttributes().getDepth();
		
		final int width    = depth*viewHeight/maxDepth+viewHeight/(2*maxDepth);
		event.gc.fillRectangle(topPixelCrossHairX-8,width-1,20,4);
	}
	
    /***
     * force to refresh the content of the canvas. 
     */
    public void refresh() {
    	rebuffer();
    }
    
    /*
     * (non-Javadoc)
     * @see edu.rice.cs.hpc.traceviewer.painter.ISpaceTimeCanvas#getScalePixelsPerTime()
     */
    @Override
	public double getScalePixelsPerTime()
	{
		final int viewWidth = clientArea.width;

		return (double)viewWidth / (double)getNumTimeDisplayed();
	}

    /*
     * (non-Javadoc)
     * @see edu.rice.cs.hpc.traceviewer.painter.ISpaceTimeCanvas#getScalePixelsPerRank()
     */
    @Override
	public double getScalePixelsPerRank() {
		return Math.max(clientArea.height/(double)stData.getMaxDepth(), 1);
	}


	//---------------------------------------------------------------------------------------
	// PRIVATE METHODS and classes
	//---------------------------------------------------------------------------------------

	private long getNumTimeDisplayed()
	{
		return (stData.getAttributes().getTimeInterval());
	}
	
	
    /************
     * method to repaint the canvas
     * this method can be costly, please do not call this unless the data has changed.
     * 
     * This method has to be synchronized since a depth view can request to recompute
     * 	the buffer while the current painting is NOT done yet (thanks to job concurrency).
     *  The sync can slow down a bit the painting but not greatly, depending on the 
     *  resolution of the depth view. We need to address the issue in the future if needed. 
     */
	synchronized private void rebuffer()
	{
		if (stData == null)
			return;

		if (depthFinalize == null)
			depthFinalize = new DepthPaintFinalize(getDisplay());
		
		final ImageTraceAttributes attributes = stData.getAttributes();
		final Frame frame = attributes.getFrame();

		// store the current process so that we don't need to rebuffer every time
		// we change the position within the same process
		currentProcess = frame.position.process;

		final int viewWidth = clientArea.width;
		final int viewHeight = clientArea.height;

		if (viewWidth>0 && viewHeight>0) {
			if (imageBuffer != null) {
				imageBuffer.dispose();
			}
			//paints the current screen
			imageBuffer = new Image(getDisplay(), viewWidth, viewHeight);
		}
		final GC bufferGC = new GC(imageBuffer);
		bufferGC.setBackground(getDisplay().getSystemColor(SWT.COLOR_WHITE));
		bufferGC.fillRectangle(0,0,viewWidth,viewHeight);
		
		attributes.numPixelsDepthV = viewHeight;
		
		Debugger.printDebug(1, "DTC rebuffering " + attributes);

		// -------------------------------------------------
		// schedule, compute the data and repaint the canvas
		// -------------------------------------------------
		
		depthPaint.cancel();
		
		depthPaint.setData(bufferGC, stData, true);
		
		// warning: the depthFinalize will dispose bufferGC resource.
		// 			need to make sure the caller doesn't call rebuffer while we 
		//			are still using bufferGC

		depthFinalize.setGC(bufferGC);
		
		// once the job is done, we'll dispose the resource and redraw the canvas
		
		depthPaint.addJobChangeListener( depthFinalize );

		//System.out.println("[" + depthPaint.hashCode() + "] start job ");
		depthPaint.schedule();
	}

	
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.swt.widgets.Widget#dispose()
	 */
	public void dispose()
	{
		if (imageBuffer != null) {
			imageBuffer.dispose();
		}
		threadExecutor.shutdown();
		super.dispose();
	}
	

	//---------------------------------------------------------------------------------------
	// Classes and variables for finalizing the paint
	//---------------------------------------------------------------------------------------


	/*******
	 * 
	 * Notifier class when the paint has been finalized
	 *
	 */
	private class DepthPaintFinalize extends JobChangeAdapter
	{
		private GC buffer;
		final private Display display;
		
		public DepthPaintFinalize(Display display)
		{
			this.display = display;
		}
		
		public void setGC(GC buffer)
		{
			this.buffer = buffer;
		}
		
		public void done(IJobChangeEvent event) {
			event.getJob().removeJobChangeListener(this);
			//System.out.println("[" + depthPaint.hashCode() + "] done job ");
			
			buffer.dispose();
			display.syncExec( new Runnable() {

				@Override
				public void run() {
					DepthTimeCanvas.this.redraw();
				}
			});
		}
	}

	/****
	 * Variable 
	 */
	private DepthPaintFinalize depthFinalize = null;


	//---------------------------------------------------------------------------------------
	// Override methods for user actions and operations 
	//---------------------------------------------------------------------------------------

	@Override
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.core.commands.operations.IOperationHistoryListener#historyNotification(org.eclipse.core.commands.operations.OperationHistoryEvent)
	 */
	public void historyNotification(final OperationHistoryEvent event) {
		
		if (event.getEventType() == OperationHistoryEvent.DONE) 
		{
			final IUndoableOperation operation = event.getOperation();

			if (operation.hasContext(BufferRefreshOperation.context)) {
				// this event includes if there's a change of colors definition, so everyone needs
				// to refresh the content
				rebuffer();
				
			} else if (operation.hasContext(PositionOperation.context)) {
				PositionOperation opPos = (PositionOperation) operation;
				Position position = opPos.getPosition();
				if (position.process == currentProcess)
				{
					// changing cursor position within the same process
					redraw();
				} else {
					// different process, we need to repaint every thing
					rebuffer();
				}
			}
		}
	}

	@Override
	protected void changePosition(Point point) {
    	long closeTime = stData.getAttributes().getTimeBegin() + (long)(point.x / getScalePixelsPerTime());
    	
    	Position currentPosition = stData.getAttributes().getPosition();
    	Position newPosition = new Position(closeTime, currentPosition.process);
    		
    	try {
			TraceOperation.getOperationHistory().execute(
					new PositionOperation(newPosition), 
					null, null);
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		
	}

	@Override
	protected void changeRegion(Rectangle region) 
	{
		final ImageTraceAttributes attributes = stData.getAttributes();

		long topLeftTime 	 = attributes.getTimeBegin() + (long)(region.x / getScalePixelsPerTime());
		long bottomRightTime = attributes.getTimeBegin() + (long)((region.width+region.x) / getScalePixelsPerTime());
		
		final Frame oldFrame 	= attributes.getFrame();
		final Position position = oldFrame.position;
		
		Frame frame = new Frame(topLeftTime, bottomRightTime,
				attributes.getProcessBegin(), attributes.getProcessEnd(),
				attributes.getDepth(), position.time, position.process);
		try {
			TraceOperation.getOperationHistory().execute(
					new ZoomOperation("Time zoom out", frame), 
					null, null);
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	}
}
