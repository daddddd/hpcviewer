/**
 * 
 */
package edu.rice.cs.hpc.viewer.scope;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.CoolBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;

import edu.rice.cs.hpc.data.experiment.scope.Scope;

/**
 * @author laksonoadhianto
 *
 */
public class BaseScopeViewActions extends ScopeViewActions {

	public BaseScopeViewActions(Shell shell, IWorkbenchWindow window,
			Composite parent, CoolBar coolbar) {
		super(shell, window, parent, coolbar);
		// TODO Auto-generated constructor stub
	}

	public void checkStates(Scope nodeSelected) {
    	boolean bCanZoomIn = objZoom.canZoomIn(nodeSelected);
		objActionsGUI.enableZoomIn( bCanZoomIn );
		objActionsGUI.enableZoomOut( objZoom.canZoomOut() );
		objActionsGUI.enableHotCallPath( bCanZoomIn );
	}

}
