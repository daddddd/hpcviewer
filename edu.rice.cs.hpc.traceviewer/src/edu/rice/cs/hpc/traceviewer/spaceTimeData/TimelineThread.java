package edu.rice.cs.hpc.traceviewer.spaceTimeData;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Canvas;

import edu.rice.cs.hpc.traceviewer.painter.SpaceTimeDetailCanvas;
import edu.rice.cs.hpc.traceviewer.painter.SpaceTimeSamplePainter;

/***********************************************************
 * A thread that reads in the data for one line, 
 * draws that line to its own image and adds the data
 * to the SpaceTimeData object that created them, and then
 * gets the next line that it needs to do this for if there
 * are any left (synchronized methods ftw!).
 * @author Michael Franco
 **********************************************************/
public class TimelineThread extends Thread
{
	/**The SpaceTimeData that this thread gets its files from and adds it data and images to.*/
	private SpaceTimeData stData;
	
	/**The master trace file that holds all the data to be read in from*/
	private File traceFile;
	
	/**Stores whether or not the bounds have been changed*/
	private boolean changedBounds;
	
	/**Stores whether a SpaceTimeDetailCanvas or a DepthTimeCanvas is being painted*/
	private boolean detailPaint;
	
	/**The canvas on which to paint.*/
	private Canvas canvas;
	
	/**The width that the images that this thread draws should be.*/
	private int width;
	
	/**The scale in the x-direction of pixels to time (for the drawing of the images).*/
	private double scaleX;
	
	/**The scale in the y-direction of pixels to processors (for the drawing of the images).*/
	private double scaleY;

	/**The minimum height the samples need to be in order to paint the white separator lines.*/
	private final static byte MIN_HEIGHT_FOR_SEPARATOR_LINES = 15;
	
	/***********************************************************************************************************
	 * Creates a TimelineThread with SpaceTimeData _stData; the rest of the parameters are things for drawing
	 * @param changedBounds - whether or not the thread needs to go get the data for its ProcessTimelines.
	 ***********************************************************************************************************/
	//The reason so many things are passed is because of ThreadInvalidAccessExceptions that
	//get thrown when it tries to access them from SpaceTimeData
	public TimelineThread(SpaceTimeData _stData, File _traceFile, boolean _changedBounds, Canvas _canvas, int _width, double _scaleX, double _scaleY)
	{
		super();
		stData = _stData;
		traceFile = _traceFile;
		changedBounds = _changedBounds;
		canvas = _canvas;
		width = _width;
		scaleX = _scaleX;
		scaleY = _scaleY;
		detailPaint = canvas instanceof SpaceTimeDetailCanvas;
	}
	
	/***************************************************************
	 * Reads in data for one line if the bounds have changed, 
	 * then paints the data to an image, then adds the data and the
	 * image to the stData that created it, and then gets the next
	 * line that it needs to do all this for if there are any left.
	 ***************************************************************/
	public void run()
	{
		RandomAccessFile inFile = null;
		FileChannel f = null;
		try
		{
			inFile = new RandomAccessFile(traceFile, "r");
			f = inFile.getChannel();
		}
		catch (IOException e)
		{
			System.err.println("Something is up...");
		}
		try
		{
			if (detailPaint)
			{
				ProcessTimeline nextTrace = stData.getNextTrace(changedBounds);
				while(nextTrace != null)
				{
					if(changedBounds)
					{
						nextTrace.readInData(inFile, f, stData.getHeight());
						stData.addNextTrace(nextTrace);
					}
					
					int imageHeight = (int)(Math.round(scaleY*(nextTrace.line()+1)) - Math.round(scaleY*nextTrace.line()));
					if (scaleY > MIN_HEIGHT_FOR_SEPARATOR_LINES)
						imageHeight--;
					else
						imageHeight++;
					
					Image line = new Image(canvas.getDisplay(), width, imageHeight);
					GC gc = new GC(line);
					SpaceTimeSamplePainter spp = new SpaceTimeSamplePainter(gc, stData.getColorTable(), scaleX, scaleY);
					stData.paintDetailLine(spp, stData.getDepth(), nextTrace.line(), imageHeight, changedBounds);
					gc.dispose();
					
					stData.addNextImage(line, nextTrace.line());
					nextTrace = stData.getNextTrace(changedBounds);
				}
			}
			else
			{
				ProcessTimeline nextTrace = stData.getNextDepthTrace();
				while (nextTrace != null)
				{
					int imageHeight = (int)(Math.round(scaleY*(nextTrace.line()+1)) - Math.round(scaleY*nextTrace.line()));
					if (scaleY > MIN_HEIGHT_FOR_SEPARATOR_LINES)
						imageHeight--;
					else
						imageHeight++;
					
					Image line = new Image(canvas.getDisplay(), width, imageHeight);
					GC gc = new GC(line);
					SpaceTimeSamplePainter spp = new SpaceTimeSamplePainter(gc, stData.getColorTable(), scaleX, scaleY);
					stData.paintDepthLine(spp, nextTrace.line(), imageHeight);
					gc.dispose();
					
					stData.addNextImage(line, nextTrace.line());
					nextTrace = stData.getNextDepthTrace();
				}
			}
		}
		finally
		{
			try
			{
				f.close();
				inFile.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}
}