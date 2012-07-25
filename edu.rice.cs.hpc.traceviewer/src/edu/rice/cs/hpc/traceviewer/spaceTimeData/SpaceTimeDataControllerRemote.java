package edu.rice.cs.hpc.traceviewer.spaceTimeData;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IWorkbenchWindow;

import edu.rice.cs.hpc.common.ui.TimelineProgressMonitor;
import edu.rice.cs.hpc.common.util.ProcedureAliasMap;
import edu.rice.cs.hpc.data.experiment.BaseExperiment;
import edu.rice.cs.hpc.data.experiment.ExperimentWithoutMetrics;
import edu.rice.cs.hpc.data.experiment.InvalExperimentException;
import edu.rice.cs.hpc.data.experiment.extdata.BaseDataFile;
import edu.rice.cs.hpc.data.experiment.extdata.TraceAttribute;
import edu.rice.cs.hpc.traceviewer.db.RemoteDataRetriever;
import edu.rice.cs.hpc.traceviewer.db.TraceDataByRankRemote;
import edu.rice.cs.hpc.traceviewer.painter.ImageTraceAttributes;
import edu.rice.cs.hpc.traceviewer.painter.SpaceTimeCanvas;
import edu.rice.cs.hpc.traceviewer.painter.SpaceTimeDetailCanvas;
import edu.rice.cs.hpc.traceviewer.painter.SpaceTimeSamplePainter;
import edu.rice.cs.hpc.traceviewer.timeline.ProcessTimeline;

public class SpaceTimeDataControllerRemote extends SpaceTimeDataController {

	// Set dataRetreiver before using any methods besides the constructor!
	final RemoteDataRetriever dataRetriever;
	HashMap<Integer, CallPath> scopeMap;
	public final int HEADER_SIZE;
	/**
	 * This is part of the code from TimelineThread. The separator lines are the
	 * thin white lines in between traces that show up when the view is
	 * sufficiently zoomed (when their heights are greater than this value).
	 */
	private final int maxDepth;
	

	public SpaceTimeDataControllerRemote(RemoteDataRetriever _dataRet, IWorkbenchWindow _window,
			IStatusLineManager _statusMgr, InputStream expStream, String Name, int _numTraces) {

		MethodCounts[0]++;

		attributes = new ImageTraceAttributes();
		ImageTraceAttributes oldAtributes = new ImageTraceAttributes();

		BaseExperiment exp = new ExperimentWithoutMetrics();
		try {
			//Without metrics, so param 3 is false
			exp.open(expStream, new ProcedureAliasMap(), false, Name);
		} catch (InvalExperimentException e) {
			System.out.println("Parse error in Experiment XML at line "
					+ e.getLineNumber());
			e.printStackTrace();
			// return;
		} catch (Exception e) {
			e.printStackTrace();
		}
		// Height = dataTrace.getNumberOfFiles();

		scopeMap = new HashMap<Integer, CallPath>();
		TraceDataVisitor visitor = new TraceDataVisitor(scopeMap);

		// This probably isn't the best way. It seems like ColorTable should be
		// created and initialized by the PaintManager, however initializing the
		// ColorTable requires the experiment file, which the PaintManager
		// should not have because it might be done differently when the data is
		// fetched remotely. Additionally the STDController needs MaxDepth along
		// with PaintManager.
		ColorTable colorTable = new ColorTable(_window.getShell().getDisplay());
		// Initializes the CSS that represents time values outside of the
		// time-line.
		colorTable.addProcedure(CallPath.NULL_FUNCTION);
		maxDepth = exp.getRootScope().dfsSetup(visitor, colorTable, 1);

		TraceAttribute attribute = exp.getTraceAttribute();
		minBegTime = attribute.dbTimeMin;
		maxEndTime = attribute.dbTimeMax;
		HEADER_SIZE = attribute.dbHeaderSize;

		dbName = exp.getName();
		
		dataRetriever = _dataRet;
		Height = _numTraces;

		super.painter = new PaintManager(attributes, oldAtributes, _window,
				_statusMgr, colorTable, maxDepth, minBegTime);

	}


	/**
	 * dtProcess scaled to be the index in traces[] that corresponds to this
	 * process. dtProcess is in the range [0, number of files in data trace]
	 * while scaledDTProcess is in the range [0, number of vertical pixels in
	 * SpaceTimeDetailView]. If it returns 0, chances are the index it should
	 * return would be outside the array, so the 0 is a sort of safeguard.
	 */
	private int computeScaledProcess() {
		if ((getCurrentlySelectedProcess() <= attributes.endProcess) && getCurrentlySelectedProcess() >= attributes.begProcess) {
			int scaledDTProcess = (int) (((double) traces.length - 1)
					/ ((double) attributes.endProcess - attributes.begProcess - 1) * (getCurrentlySelectedProcess() - attributes.begProcess));// -atr.begPro-1??
			return scaledDTProcess;
		} else// So this means that it's in that weird state where the length of
				// traces and attributes has been updated, but the position of
				// the crosshair has not. For now, we have a bad bug fix and
				// just return 0. This may cause it to render depth trace 0
				// first before switching to 0.
		{
			System.out
					.println("Mapping skipped because of state. Returning 0.");
			return 0;
		}

	}

	@Override
	public void prepareViewportPainting(boolean changedBounds) {
		System.out.println("Calling the unimplemented prep Viewp Painting");

	}

	@Override
	void prepareDepthViewportPainting() {
		depthTrace = new ProcessTimeline(null, scopeMap, 0,
				attributes.numPixelsH, attributes.numPixelsV, minBegTime
						+ attributes.begTime);
		int scaledDTProcess = computeScaledProcess();
		depthTrace.copyDataFrom(traces[scaledDTProcess]);

	}

	@Override
	public String[] getTraceDataValuesX() {
		// TEMPORARY FIX!! What do we want to do about this???
		// I had hoped that we would just be able to loop through the
		// ProcessTimelines or something and add +1 to the total if it wasn't
		// null and +.1 to the total if it was. Unfortunately, unless the
		// vertical resolution of the window is larger than the total number of
		// processes, we may never have data about each process timeline. It
		// appears we may have to send this data over the wire in the DBOK
		// message.
		int size = Height;
		String[] names = new String[size];
		for (int i = 0; i < names.length; i++) {
			names[i] = String.valueOf(i) + ".0";
		}
		return names;
	}

	@Override
	public void fillTraces(SpaceTimeCanvas canvas, int linesToPaint,
			double xscale, double yscale, boolean changedBounds) {

		// This relies on the fact that fillTraces will always be called with
		// the DetailCanvas before the DepthViewCanvas. Can I guarantee this?
		if (canvas instanceof SpaceTimeDetailCanvas) {
			if (changedBounds) {
				try {
					traces = dataRetriever.getData(attributes.begProcess,
							attributes.endProcess, attributes.begTime, attributes.endTime, //minBegTime, maxEndTime,
							attributes.numPixelsV, attributes.numPixelsH,
							scopeMap);
				} catch (IOException e) {
					// UI Notify user...
					e.printStackTrace();
				}
			}
			painter.renderTraces(traces, canvas, changedBounds, xscale, yscale);
		} else// Depth view
		{
			renderDepthTraces(canvas, changedBounds, xscale, yscale);
		}

	}

	private void renderDepthTraces(SpaceTimeCanvas canvas,
			boolean changedBounds, double scaleX, double scaleY) {
		System.out.println("Rendering Depth Trace");
		ProcessTimeline nextTrace = getNextDepthTrace();
		int width = attributes.numPixelsH;

		while (nextTrace != null) {
			painter.renderDepthTrace(canvas, scaleX, scaleY, nextTrace, width, getDepthTrace());
			nextTrace = getNextDepthTrace();
		}
	}

	

	private ProcessTimeline getNextDepthTrace() {

		if (attributes.lineNum < Math.min(attributes.numPixelsDepthV, maxDepth)) {
			if (attributes.lineNum == 0) {
				attributes.lineNum++;
				return depthTrace;
			}
			// I can't get the data from the ProcessTimeline directly, so create
			// a ProcessTimeline with data=null and then copy the actual data to
			// it.
			ProcessTimeline toDonate = new ProcessTimeline(null, scopeMap,
					attributes.lineNum, attributes.numPixelsH,
					attributes.numPixelsV, minBegTime + attributes.begTime);
			int scaledDTProcess = computeScaledProcess();
			toDonate.copyDataFrom(traces[scaledDTProcess]);
			// toDonate.copyDataFrom(depthTrace);

			attributes.lineNum++;
			return toDonate;
		} else
			return null;
	}
}
