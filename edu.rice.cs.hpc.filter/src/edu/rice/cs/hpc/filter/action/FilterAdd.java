package edu.rice.cs.hpc.filter.action;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.services.ISourceProviderService;

import edu.rice.cs.hpc.data.filter.FilterAttribute;
import edu.rice.cs.hpc.filter.service.FilterMap;
import edu.rice.cs.hpc.filter.service.FilterStateProvider;

/**********************************************
 * 
 * Command handler class to add a new filter
 * Currently it only asks for a new pattern with enabled pattern and 
 *  inclusive pattern type by default. 
 * User needs to change the default attributes if needed.
 *
 **********************************************/
public class FilterAdd extends AbstractHandler 
{

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException 
	{
		// Eclipse bug ? we need to keep this statement in the beginning of this routine because
		// HandlerUtil will remove all existing contexts when a new dialog show up
		
		IWorkbenchWindow winObj = HandlerUtil.getActiveWorkbenchWindow(event);

		// show a dialog to retrieve a new pattern
		
		final Shell shell = HandlerUtil.getActiveShell(event);
		final FilterInputDialog dialog = new FilterInputDialog(shell, "Add a pattern", "", null);
		
		if (dialog.open() == Window.OK)
		{
			// User clicks ok button: get the attribute and the filter pattern
			FilterAttribute attribute = dialog.getAttribute();
			final FilterMap filterMap = FilterMap.getInstance();
			
			// save the new pattern to the registry
			filterMap.put(dialog.getValue(), attribute);
			
			// notify changes
			ISourceProviderService sourceProviderService = (ISourceProviderService) winObj.getService(
					ISourceProviderService.class);

			final FilterStateProvider provider = (FilterStateProvider) sourceProviderService.getSourceProvider(
					FilterStateProvider.FILTER_REFRESH_PROVIDER);

			provider.refresh();
		}
		return null;
	}
}
