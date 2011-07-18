package edu.rice.cs.hpc.traceviewer.db;

import java.io.File;
import java.io.IOException;

import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import edu.rice.cs.hpc.traceviewer.spaceTimeData.ProcessTimeline;
import edu.rice.cs.hpc.traceviewer.spaceTimeData.SpaceTimeData;
import edu.rice.cs.hpc.traceviewer.ui.HPCCallStackView;
import edu.rice.cs.hpc.traceviewer.ui.HPCDepthView;
import edu.rice.cs.hpc.traceviewer.ui.HPCTraceView;


/*************
 * 
 * Class to manage trace database: opening and detecting the *.hpctrace files
 *
 */
public class TraceDatabase
{
	
	/**the minimum size a trace file must be in order to be correctly formatted*/
	final private static int MIN_TRACE_SIZE = 4+8+ProcessTimeline.SIZE_OF_HEADER+ProcessTimeline.SIZE_OF_TRACE_RECORD*2;
	//ProcessTimeline.SIZE_OF_HEADER + (ProcessTimeline.SIZE_OF_TRACE_RECORD * 2);
	
	/**a file holding an concatenated collection of all the trace files*/
	private File traceFiles = null;
	
	/**a file holding the experiment data - currently .xml format*/
	private File experimentFile = null;
	
	final private String []args;
	
	public TraceDatabase(String []_args)
	{
		args = _args;
	}
	
	public boolean openDatabase(Shell shell, final IStatusLineManager statusMgr)
	{
		
		boolean hasDatabase = false;
		
		statusMgr.setMessage("Opening database...");
		
		//---------------------------------------------------------------
		// processing the command line argument
		//---------------------------------------------------------------
		if (args != null && args.length>0)
		{
			for(String arg: args)
			{
				if (arg != null && arg.charAt(0)!='-')
				{
					// this must be the name of the database to open
					hasDatabase = this.isCorrectDatabase(arg);
				}
			}
		}
		
		if (!hasDatabase)
		{
			// use dialog box to find the database
			hasDatabase = this.open(shell);
		}
		
		if (hasDatabase)
		{
			
			//---------------------------------------------------------------------
			// Try to open the database and refresh the data
			// ---------------------------------------------------------------------
			
			File experimentFile = this.getExperimentFile();
			File traceFiles = this.getTraceFiles();
			
			SpaceTimeData stData = new SpaceTimeData(shell.getDisplay(), experimentFile, traceFiles, statusMgr.getProgressMonitor());

			try {
				//---------------------------------------------------------------------
				// Tell all views that we have the data, and they need to refresh their content
				// ---------------------------------------------------------------------				

				HPCTraceView tview = (HPCTraceView)PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(HPCTraceView.ID);
				tview.updateData(stData);

				HPCDepthView dview = (HPCDepthView)PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(HPCDepthView.ID);
				dview.updateData(stData);
				
				HPCCallStackView cview = (HPCCallStackView)PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(HPCCallStackView.ID);
				cview.updateData(stData);

				//---------------------------------------------------------------------
				// Update the title of the application
				//---------------------------------------------------------------------
				shell.setText("hpctraceviewer: " + stData.getName());
				return true;
				
			} catch (PartInitException e) {
				e.printStackTrace();
			}
		}
		statusMgr.setMessage("");
		
		return false;

	}
	

	/***
	 * Open a database by displaying a directory dialog box
	 * return true if the database is correct, false otherwise
	 * 
	 * @param shell
	 * @return
	 */
	private boolean open(Shell shell)
	{
		DirectoryDialog dialog;

		boolean validDatabaseFound = false;
		dialog = new DirectoryDialog(shell);
		dialog.setMessage("Please select the directory which holds the trace databases.");
		dialog.setText("Select Data Directory");
		String directory;
		while(!validDatabaseFound)
		{
			//traceFiles = new File();
			
			directory = dialog.open();
			
			if (directory == null) 
				// user click cancel
				return false;
			
			validDatabaseFound = this.isCorrectDatabase(directory);
						
			if (!validDatabaseFound)
				this.msgNoDatabase(dialog, directory);
		}
		
		return validDatabaseFound;
	}
	
	
	public File getTraceFiles()
	{
		return this.traceFiles;
	}
	
	public File getExperimentFile()
	{
		return this.experimentFile;
	}
	
	
	private boolean isCorrectDatabase(String directory)
	{
		File dirFile = new File(directory);
		String[] databases = dirFile.list();
		
		if (databases != null)
		{
			experimentFile = new File(directory+File.separatorChar+"experiment.xml");
			
			if (experimentFile.canRead())
			{
				//used for when there is only one megatrace file which contains all trace files*/
				File traceFile = new File(directory + File.separatorChar + TraceCompactor.MASTER_FILE_NAME);
				try 
				{
					if(!traceFile.canRead())
					{
						TraceCompactor.compact(directory);
						traceFile = new File(directory + File.separatorChar + TraceCompactor.MASTER_FILE_NAME);
					}
				} 
				catch (IOException e) 
				{
						e.printStackTrace();
				}
				
				if (traceFile.length()>MIN_TRACE_SIZE)
				{
					traceFiles = traceFile;
					return true;
				}
				else
				{
					System.err.println("Warning! Trace file size " + traceFile.length() +" is too small: " + traceFile.getName());
					return false;
				}
				/*used for when there were individual .hpctrace files
				//--------------------------------------------------------------
				// Forcing to sort alphabetically the file name. Here we do not
				//	parse the string to grab the process rank, but instead we
				//	assume the order of file is equivalent with the order of line
				//	in the trace view.
				// On Linux, the files are not sorted alphabetically
				//--------------------------------------------------------------
				java.util.Arrays.sort(databases);
				
				ArrayList<File> listOfFiles = new ArrayList<File>();
				for(String db: databases)
				{
					String traceFile = directory + File.separatorChar + db;
					if (traceFile.contains(".hpctrace"))
					{
						File file = new File(traceFile);
						
						//-----------------------------------------------------------------------------
						// check if the trace file size is correct.
						// 	the min size of trace file is HEADER + TWO_SAMPLE
						//	Where TWO_SAMPLE = RECORD_SIZE x 2
						//-----------------------------------------------------------------------------
						if (file.length()>MIN_TRACE_SIZE)
							listOfFiles.add(file);
						else
							System.err.println("Warning! Trace file size " + file.length() +" is too small: " + file.getName());
					}
				}
				if (listOfFiles.size()>0)
				{
					this.traceFiles = listOfFiles;
					return true;
				}
				*/
			}
		}
		return false;
	}
	
	private void msgNoDatabase(DirectoryDialog dialog, String str) {
		
		dialog.setMessage("The directory selected contains no trace databases:\n\t" + str + 
		"\nPlease select the directory which holds the trace databases.");
		
	}
}
