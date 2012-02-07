package edu.rice.cs.hpc.viewer.framework;

import org.eclipse.ui.application.IWorkbenchWindowConfigurer;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.application.WorkbenchWindowAdvisor;
import org.eclipse.ui.application.IWorkbenchConfigurer;

public class ApplicationWorkbenchAdvisor extends WorkbenchAdvisor {

	private static final String PERSPECTIVE_ID = "edu.rice.cs.hpc.perspective";
	private String[] args;

	// laks: we need to save and restore the configuration
	public void initialize(IWorkbenchConfigurer configurer) {
		super.initialize(configurer);
		// enable the workbench state save mechanism
		configurer.setSaveAndRestore(true);
	}
	
	public WorkbenchWindowAdvisor createWorkbenchWindowAdvisor(
			IWorkbenchWindowConfigurer configurer) {
		return new ApplicationWorkbenchWindowAdvisor(configurer, this.args);
	}

	public String getInitialWindowPerspectiveId() {
		return PERSPECTIVE_ID;
	}

	public ApplicationWorkbenchAdvisor() {
		super();
	}
	
	public ApplicationWorkbenchAdvisor(String []arguments) {
		super();
		this.args = arguments;
	}
	
	public boolean preShutdown() {
		//this.getWorkbenchConfigurer().getWorkbench().getWorkbenchWindows()[0].getActivePage().closeAllEditors(false);
		return super.preShutdown();
	}
	
	public void postStartup() {
		super.postStartup();
	}
}
