package edu.rice.cs.hpc.traceviewer.operation;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;

/***********************************************************************
 * 
 * Class to manage undo operations such as region change, depth change
 * 	or processes pattern change
 * 
 * $Id: UndoOperationAction.java 1556 2013-01-02 21:11:12Z laksono $
 *
 ***********************************************************************/
public class UndoOperationAction extends OperationHistoryAction {

	
	public UndoOperationAction(ImageDescriptor img) {
		super(img);
	}

	@Override
	protected IUndoableOperation[] getHistory() {
		return TraceOperation.getUndoHistory();
	}

	@Override
	protected void execute() {
		IUndoableOperation[] operations = getHistory();
		if (operations.length<1)
			return;
		
		IUndoableOperation[] redos = TraceOperation.getRedoHistory();
		
		if (redos.length == 0) {
			// hack: when there's no redo, we need to remove the current
			// history into the redo's stack. To do this properly, we
			// should perform an extra undo before the real undo

			doUndo();
		}
		doUndo();
		//execute(operations[operations.length-1]);
	}

	/***
	 * helper method to perform the default undo
	 */
	private void doUndo() {
		try {
			IStatus status = TraceOperation.getOperationHistory().
					undo(TraceOperation.undoableContext, null, null);
			if (!status.isOK()) {
				System.err.println("Cannot undo: " + status.getMessage());
			}
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	protected void execute(IUndoableOperation operation) {
		try {
			TraceOperation.getOperationHistory().undoOperation(operation, null, null);
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Menu getMenu(Control parent) {
		Menu menu = getMenu();
		if (menu != null) 
			menu.dispose();
		
		menu = new Menu(parent);
		
		IUndoableOperation[] operations = getHistory();
		
		// create a list of menus of undoable operations
		for (int i=operations.length-1; i>=0; i--) {
			final IUndoableOperation op = operations[i];
			Action action = new Action(op.getLabel()) {
				public void run() {
					execute(op);
				}
			};
			addActionToMenu(menu, action);
		} 
		return menu;
	}

	@Override
	protected IUndoableOperation[] setStatus() {
		final IUndoableOperation []ops = getHistory(); 
		//debug("undo", ops);
		
		boolean status = (ops != null) && (ops.length>0);
		setEnabled(status);
		return ops;
	}
}
