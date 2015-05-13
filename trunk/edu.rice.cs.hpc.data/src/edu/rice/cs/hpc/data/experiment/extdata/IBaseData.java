package edu.rice.cs.hpc.data.experiment.extdata;

public interface IBaseData {
	
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
	public int getLastIncluded();
	/** Is every rank included between the first and the last as provided above?*/
	public boolean isDenseBetweenFirstAndLast();
	
	/** Return true if the application is a hybrid app (such as MPI+OpenMP). 
	 *  False otherwise
	 *  @return boolean **/
	public boolean isHybridRank();
	/****
	 * Disposing native resources
	 */
	public void dispose();

	long getLong(long position);

	int getInt(long position);
	
	int getRecordSize();

	public long getMinLoc(int rank);

	public long getMaxLoc(int rank);
}
