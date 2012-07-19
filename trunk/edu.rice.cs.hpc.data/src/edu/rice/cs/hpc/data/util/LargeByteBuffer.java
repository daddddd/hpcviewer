package edu.rice.cs.hpc.data.util;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**The idea for this class is credited to Stu Thompson.
 * stackoverflow.com/questions/736556/binary-search-in-a-sorted-memory-mapped-file-in-java
 * 
 * The implementation is credited to
 * @author Michael
 * @author Reed
 */
public class LargeByteBuffer
{
	
	/**The masterBuffer holds a vector of all bytebuffers*/
	private MappedByteBuffer[] masterBuffer;
	
	private long length;
	
	final private FileChannel fcInput;
	
	// The page size has to be the multiple of record size
	// originally: Integer.MAX_VALUE;
	private static final long PAGE_SIZE = 24 * (1 << 20); 
	
	public LargeByteBuffer(FileChannel in)
		throws IOException
	{
		fcInput = in;
		length = in.size();
		
		int numPages = 1+(int) (in.size() / PAGE_SIZE);
		masterBuffer = new MappedByteBuffer[numPages];		
	}
	
	private long getCurrentSize(long index) throws IOException 
	{
		long currentSize = PAGE_SIZE;
		if (length/PAGE_SIZE == index) {
			currentSize = length - (index * PAGE_SIZE);
		}
		return currentSize;
	}
	
	/****
	 * Set a buffer map to a specified page
	 *  
	 * @param page
	 * @return
	 * @throws IOException
	 */
	private MappedByteBuffer setBuffer(int page) throws IOException
	{
		long start = ((long)page) * PAGE_SIZE;
		MappedByteBuffer buffer = fcInput.map(FileChannel.MapMode.READ_ONLY, start, 
				getCurrentSize(page));
		masterBuffer[page] = buffer;
		
		return buffer;
	}
	
	private MappedByteBuffer getBuffer(int page) throws IOException
	{
		MappedByteBuffer buffer = masterBuffer[page];
		if (buffer == null) {
			buffer = setBuffer(page);
		}
		return buffer;
	}
	
	public byte get(long position) throws IOException
	{
		int page = (int) (position / PAGE_SIZE);
		int loc = (int) (position % PAGE_SIZE);
		
		return getBuffer(page).get(loc);
	}
	
	public int getInt(long position) throws IOException
	{
		int page = (int) (position / PAGE_SIZE);
		int loc = (int) (position % PAGE_SIZE);
		return getBuffer(page).getInt(loc);
	}
	
	public long getLong(long position) throws IOException
	{
		int page = (int) (position / PAGE_SIZE);
		int loc = (int) (position % PAGE_SIZE);
		return getBuffer(page).getLong(loc);
	}
	
	public double getDouble(long position) throws IOException
	{
		int page = (int) (position / PAGE_SIZE);
		int loc = (int) (position % PAGE_SIZE);
		return getBuffer(page).getDouble(loc);
	}
	
	public char getChar(long position) throws IOException
	{
		int page = (int) (position / PAGE_SIZE);
		int loc = (int) (position % PAGE_SIZE);
		return getBuffer(page).getChar(loc);
	}

	public String getString(long position, long length) throws IOException
	{
		String str = "";
		for (long i = 0; i < length; ++i) {
			char c = (char)get(position + i);
			str += c;
		}
		return str;
	}

	public float getFloat(long position) throws IOException
	{
		int page = (int) (position / PAGE_SIZE);
		int loc = (int) (position % PAGE_SIZE);
		return getBuffer(page).getFloat(loc);
	}
	
	public short getShort(long position) throws IOException
	{
		int page = (int) (position / PAGE_SIZE);
		int loc = (int) (position % PAGE_SIZE);
		return getBuffer(page).getShort(loc);
	}
	
	public long size()
	{
		return length;
	}
	
	/***
	 * Disposing resources manually to avoid memory leak
	 */
	public void dispose() 
	{
		this.masterBuffer = null;
		try {
			this.fcInput.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}