package net.benjaminurquhart.diannex.ui;

import javax.swing.SwingWorker;

public class GenericWorker extends SwingWorker<Void, Void> {

	private final String msg;
	private final Runnable task;
	
	public GenericWorker(String msg, Runnable task) {
		this.task = task;
		this.msg = msg;
	}
	
	@Override
	protected Void doInBackground() throws Exception {
		UI ui = UI.getInstance();
		try {
			ui.progressBar.setIndeterminate(true);
			ui.progressBar.setString(msg);
			this.task.run();
			ui.onFinish(null);
		}
		catch(Throwable e) {
			ui.onFinish(e);
		}
		return null;
	}
}
