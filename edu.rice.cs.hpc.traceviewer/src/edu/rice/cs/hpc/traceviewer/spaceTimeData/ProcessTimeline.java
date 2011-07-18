package edu.rice.cs.hpc.traceviewer.spaceTimeData;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Vector;

import edu.rice.cs.hpc.data.experiment.scope.CallSiteScope;
import edu.rice.cs.hpc.data.experiment.scope.ProcedureScope;
import edu.rice.cs.hpc.data.experiment.scope.RootScope;
import edu.rice.cs.hpc.data.experiment.scope.Scope;
/**A data structure that stores one line of timestamp-cpid data.*/
//cpid stands for Call Path ID and it's the id used to identify the call path at that cpid's timestamp
public class ProcessTimeline
{
	/**The list of cpids that correspond to this ProcessTimeline's timestamps.*/
	public Vector<Integer> timeLine;
	
	/**The timestamps needed to draw this ProcessTimeline.*/
	public Vector<Double> times;
	
	/**The mapping between the cpid's and the actual scopes.*/
	private HashMap<Integer, Scope> scopeMap;
	
	/**A CallStackSample to represent null.*/
	private CallStackSample outsideTimeline;
	
	/**The file that everything will get read from.*/
	private File traceFile;
	
	/**The process belongs to this timeline*/
	private int processNumber;
	
	/**The minimum location in the file where data we are looking for can be found*/
	private long minimumLocation;
	
	/**The maximum location in the file where data we are looking for can be found*/
	private long maximumLocation;
	
	/**This ProcessTimeline's line number.*/
	private int lineNum;
	
	/**The initial time in view.*/
	private double startingTime;
	
	/**The range of time in view.*/
	private double timeRange;
	
	/**The amount of time that each pixel on the screen correlates to.*/
	private double pixelLength;
	
	/**The average time between timestamps.*/
	private double averageTimestampLength;
	
	/**The number of horizontal pixels in the viewer.*/
	private int numPixelH;
	
	/**The size of one trace record in bytes (cpid (= 4 bytes) + timeStamp (= 8 bytes)).*/
	public final static byte SIZE_OF_TRACE_RECORD = 12;
	
	/**The new format for trace files has a 24-byte header (if 0, we were most recently testing old data).*/
	public final static byte SIZE_OF_HEADER = 24;
	
	/**The size of the header for the entire .megatrace file*/
	public final static byte SIZE_OF_MASTER_HEADER = 4;
	
	/**debugger output flag*/
	final boolean PTL_DEBUG = false;
	
	/*************************************************************************
	 *	Reads in the call-stack trace data from the binary traceFile in the form:
	 *	double time-stamp
	 *	int Call-Path ID
	 *	double time-stamp
	 *	int Call-Path ID
	 *	...
	 ************************************************************************/
	
	/**Creates a new ProcessTimeline with the given parameters.*/
	public ProcessTimeline(int _lineNum, HashMap<Integer, Scope> _scopeMap, File _traceFile, int _processNumber, int _numPixelH, double _timeRange, double _startingTime)
	{
		lineNum = _lineNum;
		scopeMap = _scopeMap;
		traceFile = _traceFile;
		processNumber = _processNumber;
		numPixelH = _numPixelH;
		timeRange = _timeRange;
		startingTime = _startingTime;
		outsideTimeline = new CallStackSample();
		outsideTimeline.addFunction(CallStackSample.NULL_FUNCTION);
		
		times = new Vector<Double>(numPixelH);
		timeLine = new Vector<Integer>(numPixelH);
		
		pixelLength = timeRange/(double)numPixelH;
		averageTimestampLength = timeRange/(getElementCount());
	}
	
	/**Fills the ProcessTimeline with data from the file.*/
	public void readInData(RandomAccessFile inFile, FileChannel f, int totalProcesses)
	{
		ByteBuffer b = ByteBuffer.allocateDirect(SIZE_OF_TRACE_RECORD);
		ByteBuffer cacheBuffer = ByteBuffer.allocate(SIZE_OF_TRACE_RECORD*129 - 4);
		ByteBuffer indexBuffer = ByteBuffer.allocate(8);
		try
		{
			f.read(indexBuffer, SIZE_OF_MASTER_HEADER+processNumber*8);
			indexBuffer.flip();
			minimumLocation = indexBuffer.getLong();
			indexBuffer.clear();
			if (processNumber == totalProcesses)
				maximumLocation = inFile.length();
			else
			{
				f.read(indexBuffer, SIZE_OF_MASTER_HEADER+(processNumber+1)*8);
				indexBuffer.flip();
				maximumLocation = indexBuffer.getLong()-SIZE_OF_TRACE_RECORD;
			}
			
			/*TODO: remove this
			if (minimumLocation==1305108)
			{
				long location = minimumLocation;
				while (location != 1492368)
				{
					f.read(b, location);
					b.flip();
					System.out.println(b.getLong());
					b.clear();
					location+=SIZE_OF_TRACE_RECORD;
				}
			}*/
			
			long maxLoc = Math.min(findLocBeforeRAF(timeRange+startingTime, f, b)+SIZE_OF_TRACE_RECORD, traceFile.length()-SIZE_OF_TRACE_RECORD);
			long minLoc = findLocBeforeRAF(startingTime, f, b);
			
			binaryFill(minLoc, maxLoc, 0, numPixelH, 0, f, b, cacheBuffer);
			
			b.clear();
			f.read(b, maxLoc);
			b.flip();

			//-----------------------------------------------------------------------------
			// Hack: if the trace has no data at all, we give up and quit
			//-----------------------------------------------------------------------------
			if (b.remaining()>=8)
			{
				double nextTime = b.getLong();
				int cpid = b.getInt();
				addSample(cpid, nextTime, times.size());
				
				b = (ByteBuffer)b.clear();
				f.read(b, minLoc);
				b.flip();
				
				//-----------------------------------------------------------------------------
				// Hack: if the trace has no pair data, we give up, and do not add sample
				//-----------------------------------------------------------------------------
				if (b.remaining()>=8)
				{
					nextTime = b.getLong();
					if (!times.firstElement().equals(nextTime))
					{
						cpid = b.getInt();
						addSample(cpid, nextTime, 0);
					}
				}
				else
				{
					this.debug("Warning: no pair data value for file: " + this.traceFile );
				}
				
				postProcess();
			}
			else
			{
				this.debug("Warning: no data value for file " + this.traceFile);
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	/**Adds a sample to times and timeLine.*/
	public void addSample(int cpid, double timestamp, int index)
	{
		if (cpid == 1918985061)
		{
			System.out.println("ADDING THIS ONE RANDOM FAILBLOG ENTRY "+cpid);
		}
		else
		{
			if (index == times.size())
			{
				times.add(timestamp);
				timeLine.add(cpid);
			}
			else
			{
				times.add(index, timestamp);
				timeLine.add(index, cpid);
			}
		}
		this.debug("add_sample " + index + ": " + cpid + "\ttime: " + timestamp);
	}
	
	/**Shifts all the times in the ProcessTimeline to the left by lowestStartingTime.*/
	public void shiftTimeBy(double lowestStartingTime)
	{
		for(int i = 0; i<times.size(); i++)
		{
			times.set(i, times.get(i)-lowestStartingTime);
		}
	}
	
	/*************************************************************************************
	 * Fills the CallStackSample c with the function names you get as you go up the
	 * scope tree starting at s.
	 ************************************************************************************/
	public void getPath(CallStackSample c, Scope s)
	{
		Scope parent = s.getParentScope();
		if (parent != null && !(parent instanceof RootScope))
			getPath(c, parent);
		
		if ((s instanceof CallSiteScope) || (s instanceof ProcedureScope))
		{
			c.addFunction(s.getName());
		}
	}
	
	/**Gets the time that corresponds to the index sample in times.*/
	public double getTime(int sample)
	{
		if(sample<0)
			return 0;
		if(sample>=times.size())
			return width();
		return times.get(sample);
	}
	
	/**Gets the cpid that corresponds to the index sample in timeLine.*/
	public int getCpid(int sample)
	{
		return timeLine.elementAt(sample);
	}
	
	/**Gets the CallStackSample that the cpid at index sample corresponds to.*/
	public CallStackSample getCallStackSample(int sample)
	{
		if(sample == -1)
			return outsideTimeline;
		else
		{
			int cpid = getCpid(sample);
			Scope cpscope = scopeMap.get(cpid);
			if (cpscope == null)
			{
				//this is a quick fix for a specific 1024-processor set of data used as of July 15, 2011
				cpscope = scopeMap.get(25);
				System.err.println("No scope found for cpid " + cpid);
			}
			CallStackSample css = new CallStackSample();
			getPath(css, cpscope);
			return css;
		}
	}
	
	/**Gets the name of the function at depth and the cpid at index sample corresponds to.*/
	//TODO: make this method work s.t. it returns single string of sample
	public String getSample(int sample, int depth)
	{
		if(sample == -1)
			return CallStackSample.NULL_FUNCTION;
		else
		{
			int cpid = getCpid(sample);
			//System.out.println(cpid);
			CallStackSample css = new CallStackSample();
			Scope cpscope = scopeMap.get(cpid);
			if (cpscope == null)
			{
				//this is a quick fix for a specific 256-processor set of data used as of June 30, 2010
				cpscope = scopeMap.get(29);
				System.out.println("No scope found for cpid " + cpid);
			}
			Scope parent = cpscope.getParentScope();
			while (parent != null && !(parent instanceof RootScope))
			{
				depth--;
				parent = cpscope.getParentScope();
			}
			if (depth == 0)
				return cpscope.getName();
			else
				return "DERP";
		}
	}
	
	/**Returns the last timestamp in the ProcessTimeline.*/
	public double width()
	{
		return times.lastElement();
	}
	
	/**Returns the number of elements in this ProcessTimeline.*/
	public int size()
	{
		return timeLine.size();
	}
	
	/**Returns the first timestamp in this ProcessTimeline.*/
	public double getStartingTime()
	{
		return times.firstElement();
	}
	
	/**Returns this ProcessTimeline's line number.*/
	public int line()
	{
		return lineNum;
	}
	
	/*************************************************************************
	 *	Returns the number of elements in the trace data.
	 ************************************************************************/
	public long getElementCount()
	{
		return (traceFile.length() - SIZE_OF_HEADER)/SIZE_OF_TRACE_RECORD;
	}
	
	/****************************************************************************
	 *	Returns the index in "times" of the closest sample to the specified time.
	 ***************************************************************************/
	public int getClosestSample(double time)
	{
		int low = 0;
        int high = times.size() - 1;
        int mid = ( low + high ) / 2;
        while( low != mid )
        {
            if(time > times.get(mid))
            	low = mid;
            else
            	high = mid;
            mid = ( low + high ) / 2;
        }
        
        if(Math.abs(time-times.get(high))<Math.abs(time-times.get(low)))
        	return high;
        else
        	return low;
	}
	
	/**Finds the sample to which 'time' most closely corresponds in the ProcessTimeline.
	 * @param time: the requested time
	 * @return the index of the sample if the time is within the range, -1 otherwise 
	 * */
	public int findMidpointBefore(double time)
	{
		int low = 0;
		int high = times.size() - 1;
		
		// do not search the sample if the time is out of range
		if (time < times.get(low) || time>times.get(high)) 
			return -1;
		
		int mid = ( low + high ) / 2;
		while( low != mid )
		{
			if (time > (times.get(mid)+times.get(mid+1))/2.0)
				low = mid;
			else
				high = mid;
			mid = ( low + high ) / 2;
		}
		if (time >= (times.get(low)+times.get(low+1))/2.0)
			return low+1;
		else
			return low;
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
	 * @param f The channel of the file that it's searching in.
	 * @param b The ByteBuffer the channel will read into.
	 * @param cacheBuffer The ByteBuffer that information will be cached into when the binary 
	 * search range gets small enough.
	 * @return Returns the index that shows the size of the recursive subtree that has been read.
	 * Used for calculating the index in which the data is to be inserted.
	 ******************************************************************************************/
	public int binaryFill(long minLoc, long maxLoc, int startPixel, int endPixel, int minIndex, FileChannel f, ByteBuffer b, ByteBuffer cacheBuffer)
	{
		int midPixel = (startPixel + endPixel)/2;
		if (midPixel == startPixel)
			return 0;
		try
		{
			cacheBuffer = (ByteBuffer)cacheBuffer.clear();
			b.clear();
			
			long loc = findBoundedLocRAF(midPixel*pixelLength + startingTime, f, b, cacheBuffer, minLoc, maxLoc);
			f.read(b, loc);
			b.flip();
			
			double nextTime = b.getLong();
			int cpid = b.getInt();
			
			if (cpid==1918985061)
				System.out.println("fml");
			
			addSample(cpid, nextTime, minIndex);
			int addedLeft = binaryFill(minLoc, loc, startPixel, midPixel, minIndex, f, b, cacheBuffer);
			int addedRight = binaryFill(loc, maxLoc, midPixel, endPixel, minIndex+addedLeft+1, f, b, cacheBuffer);
			return (addedLeft+addedRight+1);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return -1;
		}
	}
	
	/***********************************************************************************
	 * Searches through the data cached in a ByteBuffer 'b' with 'size' number of bytes
	 * of data in it for the entry 'time'. 
	 * @param time
	 * @param b
	 * @param size
	 **********************************************************************************/
	public int findBoundedLocCached(double time, ByteBuffer b)
	{
		b.flip();
		int low = 0;
		int high = b.remaining()/SIZE_OF_TRACE_RECORD - 1;
		int mid = (low + high)/2;
		//int size = high;
		double temp = 0;
		while(low < mid)
		{
			b.position(mid*SIZE_OF_TRACE_RECORD);

			temp = b.getLong();
			if(time > temp)
				low = mid;
			else
				high = mid;
			mid = (low + high)/2;
		}
		return low*SIZE_OF_TRACE_RECORD;
		/*if(high*SIZE_OF_TRACE_RECORD == size)
			return low*SIZE_OF_TRACE_RECORD;
		else
		{
			b.position(low*SIZE_OF_TRACE_RECORD);
			double tempLower = b.getLong();
			if(Math.abs(temp-time) < Math.abs(time-tempLower))
				return low*SIZE_OF_TRACE_RECORD;
			else
				return (low+1)*SIZE_OF_TRACE_RECORD;
		}*/
	}
	
	/*********************************************************************************
	 *	Returns the location in the traceFile of the trace data (time stamp and cpid)
	 *	Precondition: the location of the trace data is between minLoc and maxLoc.
	 ********************************************************************************/	
	public long findBoundedLocRAF(double time, FileChannel f, ByteBuffer b, ByteBuffer cacheBuffer, long minLoc, long maxLoc)
		throws Exception
	{
		if (minLoc == maxLoc)
			return minLoc;
		long low = (minLoc-minimumLocation-SIZE_OF_HEADER)/SIZE_OF_TRACE_RECORD;
		long high = (maxLoc-minimumLocation-SIZE_OF_HEADER)/SIZE_OF_TRACE_RECORD;
		long mid = (low + high)/2;
		try
		{
			//optimum cache size found by trial and error - the minus four
			//at the end is because we don't need that last cpid for this
			//(we don't need any cpid's, actually, but it's faster to read
			//everything in rather than pick out the timestamps)
			int cacheSize = SIZE_OF_TRACE_RECORD*129-4;
			long guessLoc = (long)((time-startingTime)/(averageTimestampLength)) + SIZE_OF_HEADER/SIZE_OF_TRACE_RECORD;
			double temp = 0;
			while((high - low + 1)*SIZE_OF_TRACE_RECORD > cacheSize)
			{
				b.clear();
				f.read(b, mid*SIZE_OF_TRACE_RECORD + SIZE_OF_HEADER+minimumLocation);
				b.flip();
				temp = b.getLong();
				if( time > temp ) 
					low = mid;
	            else 
	            	high = mid;
				if(low <= guessLoc && guessLoc <= high)
				{
					mid = closestFraction(guessLoc, 8, low, high);
					if(mid - low < high - mid)
						mid += 60;
					else
						mid -= 60;
				}
				else
					mid = ( low + high ) / 2;
			}
			b.clear();
			//changing the size of the bytebuffer is slow
			f.read(cacheBuffer, low*SIZE_OF_TRACE_RECORD + SIZE_OF_HEADER+minimumLocation);
			int localLoc = findBoundedLocCached(time, cacheBuffer);
			long loc = Math.min(low*SIZE_OF_TRACE_RECORD + localLoc+minimumLocation+SIZE_OF_HEADER, maxLoc);
			return loc;
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		return -1;
	}
	
	/*************************************************************************
	 *	Returns the location in the traceFile of the trace data (time stamp and cpid).
	 ************************************************************************/	
	public long findLocBeforeRAF(double time, FileChannel f, ByteBuffer b)
	{
		long low = 0;
		long high = (maximumLocation-minimumLocation-SIZE_OF_HEADER)/SIZE_OF_TRACE_RECORD;
		long mid = (low + high)/2;
		try
		{
			//int cacheSize = SIZE_OF_TRACE_RECORD*129-4;
			long temp = 0;
			while(low < mid)
			{
				b.clear();
				f.read(b, mid*SIZE_OF_TRACE_RECORD+minimumLocation+SIZE_OF_HEADER);
				b.flip();
				temp = b.getLong();
				if( time > temp )
					low = mid;
	            else
	            	high = mid;
	            
				mid = ( low + high ) / 2;
			}
			b.clear();
			return low*SIZE_OF_TRACE_RECORD+minimumLocation+SIZE_OF_HEADER;
			/*
			//cacheSize = ((int)(high - low + 1))*SIZE_OF_TRACE_RECORD;
			b = (ByteBuffer)b.clear();
			//changing the size of the bytebuffer is slow
			//ByteBuffer b2 = ByteBuffer.allocate(cacheSize);
			f.read(cacheBuffer,low*SIZE_OF_TRACE_RECORD);
			int localLoc = findLocBeforeCached(time, cacheBuffer, cacheSize);
			long loc = localLoc + low*SIZE_OF_TRACE_RECORD;
			return loc;*/
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		return -1;
	}
	
	/**Returns the closest frac-th of the range specified by min and max to guessLoc.*/
	public static long closestFraction(long guessLoc, int frac, long min, long max)
	{
		int closest = 0;
		long closestDist = Long.MAX_VALUE;
		long range = max - min;
		for(int i = 1; i < frac; i++)
		{
			if(Math.abs(min + i*range/frac - guessLoc) < closestDist)
			{
				closestDist = Math.abs(min + i*range/frac - guessLoc);
				closest = i;
			}
		}
		return min + closest*range/frac;
	}
	
	/**Converts a byte array to a long.*/
	public static long longFromBArray(byte[] barray, int index)
	{
		long sum = 0;
		for(int i = 0; i < 8; i++)
		{
			sum <<= 8;
			sum+=barray[index+i];
		}
		return sum;
	}
	
	/**Converts a byte array to an int.*/
	public static int intFromBArray(byte[] barray, int index)
	{
		int sum = 0;
		for(int i = 0; i < 4; i++)
		{
			sum <<= 8;
			sum+=barray[index+i];
		}
		return sum;
	}
	
	/*********************************************************************************************
	 * Removes unnecessary samples:
	 * i.e. if timeLine had three of the same cpid's in a row, the middle one would be superfluous,
	 * as we would know when painting that it should be the same color all the way through.
	 ********************************************************************************************/
	public void postProcess()
	{
		for(int i = 0; i < times.size()-2; i+=2)
		{
			while(i < times.size()-1 && times.get(i).equals(times.get(i+1)))
			{
				times.remove(i+1);
				timeLine.remove(i+1);
			}
			while(i < times.size()-2 && timeLine.get(i).equals(timeLine.get(i+1)) && timeLine.get(i+1).equals(timeLine.get(i+2)))
			{
				times.remove(i+1);
				timeLine.remove(i+1);
			}
		}
	}
	
	/****
	 * debugger output
	 * @param str
	 */
	private void debug(String str)
	{
		if (this.PTL_DEBUG)
		{
			System.out.println("PTL: " + str);
		}
	}
}