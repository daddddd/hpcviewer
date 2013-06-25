package edu.rice.cs.hpc.data.experiment.extdata;

public interface IBaseData {
	
	/***
	 * retrieve trace data header and record size
	 * @return
	 */
	public int getHeaderSize();
	
	/***
	 * retrieve the list of rank names ( usual format: process.thread )
	 * @return
	 */
	public String []getListOfRanks();
	
	/****
	 * retrieve the number of ranks 
	 * @return
	 */
	public int getNumberOfRanks();
	
	/**
	 * Get the index of the first included rank. Provided to give a
	 * window through the filtering abstraction
	 */
	public int getFirstIncluded();
	
	
	/****
	 * Disposing native resources
	 */
	public void dispose();
}
