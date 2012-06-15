package edu.rice.cs.hpc.traceviewer.db;

import java.util.Vector;

import edu.rice.cs.hpc.data.experiment.extdata.BaseDataFile;
import edu.rice.cs.hpc.data.util.Constants;
import edu.rice.cs.hpc.data.util.LargeByteBuffer;
import edu.rice.cs.hpc.traceviewer.util.Debugger;

public class TraceDataByRank {

	/**The size of one trace record in bytes (cpid (= 4 bytes) + timeStamp (= 8 bytes)).*/
	public final static byte SIZE_OF_TRACE_RECORD = 12;
	
	final private BaseDataFile data;
	final private int rank;
	final private long minloc;
	final private long maxloc;
	final private int numPixelH;
	
	private Vector<TimeCPID> listcpid;
	
	/***
	 * Create a new instance of trace data for a given rank of process or thread 
	 * 
	 * @param _data
	 * @param _rank
	 * @param _numPixelH
	 */
	public TraceDataByRank(BaseDataFile _data, int _rank, int _numPixelH, final int header_size)
	{
		data = _data;
		rank = _rank;
		
		final long offsets[] = data.getOffsets();
		minloc = offsets[rank] + header_size;
		maxloc = ( (rank+1<data.getNumberOfFiles())? offsets[rank+1] : data.getMasterBuffer().size()-1 )
				- SIZE_OF_TRACE_RECORD;
		
		numPixelH = _numPixelH;
		
		listcpid = new Vector<TimeCPID>(numPixelH);
	}
	
	/***
	 * reading data from file
	 * 
	 * @param timeStart
	 * @param timeRange
	 * @param pixelLength : number of records
	 */
	public void getData(double timeStart, double timeRange, double pixelLength)
	{
		Debugger.printDebug(1, "getData loc [" + minloc+","+ maxloc + "]");
		
		// get the start location
		final long startLoc = this.findTimeInInterval(timeStart, minloc, maxloc);
		
		// get the end location
		final double endTime = timeStart + timeRange;
		final long endLoc = Math.min(this.findTimeInInterval(endTime, minloc, maxloc)+SIZE_OF_TRACE_RECORD, maxloc );

		// get the number of records data to display
		final long numRec = 1+this.getNumberOfRecords(startLoc, endLoc);
		
		// --------------------------------------------------------------------------------------------------
		// if the data-to-display is fit in the display zone, we don't need to use recursive binary search
		//	we just simply display everything from the file
		// --------------------------------------------------------------------------------------------------
		if (numRec<=numPixelH) {
			
			// display all the records
			for(long i=startLoc;i<=endLoc; ) {
				listcpid.add(getData(i));
				// one record of data contains of an integer (cpid) and a long (time)
				i =  i + SIZE_OF_TRACE_RECORD;
			}
			
		} else {
			
			// the data is too big: try to fit the "big" data into the display
			
			//fills in the rest of the data for this process timeline
			this.sampleTimeLine(startLoc, endLoc, 0, numPixelH, 0, pixelLength, timeStart);
			
		}
		
		// --------------------------------------------------------------------------------------------------
		// get the last data if necessary: the rightmost time is still less then the upper limit
		// 	I think we can add the rightmost data into the list of samples
		// --------------------------------------------------------------------------------------------------
		if (endLoc < this.maxloc) {
			final TimeCPID dataLast = this.getData(endLoc);
			this.addSample(listcpid.size(), dataLast);
		}
		
		// --------------------------------------------------------------------------------------------------
		// get the first data if necessary: the leftmost time is still bigger than the lower limit
		//	similarly, we add to the list 
		// --------------------------------------------------------------------------------------------------
		if ( startLoc>minloc ) {
			final TimeCPID dataFirst = this.getData(startLoc-SIZE_OF_TRACE_RECORD);
			this.addSample(0, dataFirst);
		}

		postProcess();
		
	}
	
	
	/**Gets the time that corresponds to the index sample in times.*/
	public double getTime(int sample)
	{
		if(sample<0)
			return 0;

		final int last_index = listcpid.size();
		if(sample>=last_index) {
			return listcpid.get(last_index-1).timestamp;
		}
		return listcpid.get(sample).timestamp;
	}
	
	/**Gets the cpid that corresponds to the index sample in timeLine.*/
	public int getCpid(int sample)
	{
		return listcpid.get(sample).cpid;
	}
	

	
	/**Shifts all the times in the ProcessTimeline to the left by lowestStartingTime.*/
	public void shiftTimeBy(double lowestStartingTime)
	{
		for(int i = 0; i<listcpid.size(); i++)
		{
			TimeCPID timecpid = listcpid.get(i);
			timecpid.timestamp = timecpid.timestamp - lowestStartingTime;
			listcpid.set(i,timecpid);
		}
	}

	
	
	/**Returns the number of elements in this ProcessTimeline.*/
	public int size()
	{
		return listcpid.size();
	}

	
	/**Finds the sample to which 'time' most closely corresponds in the ProcessTimeline.
	 * @param time: the requested time
	 * @return the index of the sample if the time is within the range, -1 otherwise 
	 * */
	public int findMidpointBefore(double time)
	{
		int low = 0;
		int high = listcpid.size() - 1;
		
		// do not search the sample if the time is out of range
		if (time < listcpid.get(low).timestamp || time>listcpid.get(high).timestamp) 
			return -1;
		
		int mid = ( low + high ) / 2;
		
		while( low != mid )
		{
			final double time_current = getTimeMidPoint(mid,mid+1);
			
			if (time > time_current)
				low = mid;
			else
				high = mid;
			mid = ( low + high ) / 2;
			
		}
		if (time >= getTimeMidPoint(low,low+1))
			return low+1;
		else
			return low;
	}

	
	public BaseDataFile getTraceData()
	{
		return this.data;
	}
	
	
	private double getTimeMidPoint(int left, int right) {
		return (listcpid.get(left).timestamp + listcpid.get(right).timestamp) / 2.0;
	}
	
	/*******************************************************************************************
	 * Recursive method that fills in times and timeLine with the correct data from the file.
	 * Takes in two pixel locations as endpoints and finds the timestamp that owns the pixel
	 * in between these two. It then recursively calls itself twice - once with the beginning 
	 * location and the newfound location as endpoints and once with the newfound location 
	 * and the end location as endpoints. Effectively updates times and timeLine by calculating 
	 * the index in which to insert the next data. This way, it keeps times and timeLine sorted.
	 * @author Reed Landrum and Michael Franco
	 * @param minLoc The beginning location in the file to bound the search.
	 * @param maxLoc The end location in the file to bound the search.
	 * @param startPixel The beginning pixel in the image that corresponds to minLoc.
	 * @param endPixel The end pixel in the image that corresponds to maxLoc.
	 * @param minIndex An index used for calculating the index in which the data is to be inserted.
	 * @return Returns the index that shows the size of the recursive subtree that has been read.
	 * Used for calculating the index in which the data is to be inserted.
	 ******************************************************************************************/
	private int sampleTimeLine(long minLoc, long maxLoc, int startPixel, int endPixel, int minIndex, 
			double pixelLength, double startingTime)
	{
		int midPixel = (startPixel+endPixel)/2;
		if (midPixel == startPixel)
			return 0;
		
		long loc = findTimeInInterval(midPixel*pixelLength+startingTime, minLoc, maxLoc);
		
		final TimeCPID nextData = this.getData(loc);
		
		addSample(minIndex, nextData);
		
		int addedLeft = sampleTimeLine(minLoc, loc, startPixel, midPixel, minIndex, pixelLength, startingTime);
		int addedRight = sampleTimeLine(loc, maxLoc, midPixel, endPixel, minIndex+addedLeft+1, pixelLength, startingTime);
		
		return (addedLeft+addedRight+1);
	}
	
	/*********************************************************************************
	 *	Returns the location in the traceFile of the trace data (time stamp and cpid)
	 *	Precondition: the location of the trace data is between minLoc and maxLoc.
	 * @param time: the time to be found
	 * @param left_boundary_offset: the start location. 0 means the beginning of the data in a process
	 * @param right_boundary_offset: the end location.
	 ********************************************************************************/
	private long findTimeInInterval(double time, long left_boundary_offset, long right_boundary_offset)
	{
		if (left_boundary_offset == right_boundary_offset) return left_boundary_offset;

		final LargeByteBuffer masterBuff = data.getMasterBuffer();

		long left_index = getRelativeLocation(left_boundary_offset);
		long right_index = getRelativeLocation(right_boundary_offset);
		
		double left_time = masterBuff.getLong(left_boundary_offset);
		double right_time = masterBuff.getLong(right_boundary_offset);
		
		// apply "Newton's method" to find target time
		while (right_index - left_index > 1) {
			long predicted_index;
			double rate = (right_time - left_time) / (right_index - left_index);
			double mtime = (right_time - left_time) / 2;
			if (time <= mtime) {
				predicted_index = Math.max((long) ((time - left_time) / rate) + left_index, left_index);
			} else {
				predicted_index = Math.min((right_index - (long) ((right_time - time) / rate)), right_index); 
/*				if (tmp_index<0) {
					predicted_index = Math.max(tmp_index, left_index);
				} else {
					// original code: predicted_index = Math.min((right_index - (long) ((right_time - time) / rate)), right_index);
					predicted_index = Math.min(tmp_index, right_index);
				}*/
				
			}
			
			// adjust so that the predicted index differs from both ends
			// except in the case where the interval is of length only 1
			// this helps us achieve the convergence condition
			if (predicted_index <= left_index) 
				predicted_index = left_index + 1;
			if (predicted_index >= right_index)
				predicted_index = right_index - 1;

			double temp = masterBuff.getLong(getAbsoluteLocation(predicted_index));
			if (time >= temp) {
				left_index = predicted_index;
				left_time = temp;
			} else {
				right_index = predicted_index;
				right_time = temp;
			}
		}
		long left_offset = getAbsoluteLocation(left_index);
		long right_offset = getAbsoluteLocation(right_index);

		left_time = masterBuff.getLong(left_offset);
		right_time = masterBuff.getLong(right_offset);

		// return the closer sample or the maximum sample if the 
		// time is at or beyond the right boundary of the interval
		final boolean is_left_closer = Math.abs(time - left_time) < Math.abs(right_time - time);
		if ( is_left_closer ) return left_offset;
		else if (right_offset < this.maxloc) return right_offset;
		else return this.maxloc;
	}
	
	private long getAbsoluteLocation(long relativePosition)
	{
		return minloc + (relativePosition*SIZE_OF_TRACE_RECORD);
	}
	
	private long getRelativeLocation(long absolutePosition)
	{
		return (absolutePosition-minloc)/SIZE_OF_TRACE_RECORD;
	}
	
	
	/**Adds a sample to times and timeLine.*/
	public void addSample( int index, TimeCPID datacpid)
	{		
		if (index == listcpid.size())
		{
			this.listcpid.add(datacpid);
		}
		else
		{
			this.listcpid.add(index, datacpid);
		}
	}

	
	public Vector<TimeCPID> getListOfData()
	{
		return this.listcpid;
	}
	
	
	public void setListOfData(Vector<TimeCPID> anotherList)
	{
		this.listcpid = anotherList;
	}
	
	
	private TimeCPID getData(long location) {
		
		final LargeByteBuffer masterBuff = data.getMasterBuffer();
		final double time = masterBuff.getLong(location);
		final int cpid = masterBuff.getInt(location+Constants.SIZEOF_LONG);

		return new TimeCPID(time,cpid);
	}
	
	private long getNumberOfRecords(long start, long end) {
		return (end-start)/(SIZE_OF_TRACE_RECORD);
	}

	/*********************************************************************************************
	 * Removes unnecessary samples:
	 * i.e. if timeLine had three of the same cpid's in a row, the middle one would be superfluous,
	 * as we would know when painting that it should be the same color all the way through.
	 ********************************************************************************************/
	private void postProcess()
	{
		int len = listcpid.size();
		for(int i = 0; i < len-2; i++)
		{
			while(i < len-1 && listcpid.get(i).timestamp==(listcpid.get(i+1).timestamp))
			{
				listcpid.remove(i+1);
				len--;
			}
		}
	}
		
	/***
	 * struct object of time and CPID pair
	 * 
	 * @author laksonoadhianto
	 *
	 */
	private class TimeCPID 
	{
		public double timestamp;
		public int cpid;
		
		public TimeCPID(double _timestamp, int _cpid) {
			this.timestamp = _timestamp;
			this.cpid = _cpid;
		}
	}

}
