package nz.ac.lconz.irr.mediafilter;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Utility methods for media filters
 * @author Andrea Schweer schweer@waikato.ac.nz for LCoNZ
 */
public class MediaFilterUtils {
	/**
	 * Execute a command on the command line
	 * @param commandLine The
	 * @param timeoutMilliseconds
	 * @return
	 * @throws IOException
	 * @throws TimeoutException
	 * @throws InterruptedException
	 */
	public static int executeCommand(final String commandLine, final long timeoutMilliseconds) throws IOException, TimeoutException, InterruptedException {
		Runtime runtime = Runtime.getRuntime();
		Process process = runtime.exec(commandLine);

		Worker worker = new Worker(process);
		worker.start();
		try {
			worker.join(timeoutMilliseconds);
			if (worker.exit != null) {
				return worker.exit;
			} else {

				throw new TimeoutException("The command [" + commandLine + "] timed out.");
			}
		} catch (InterruptedException ex) {
			worker.interrupt();
			Thread.currentThread().interrupt();
			throw ex;
		} finally {
			process.destroy();
		}
	}

	private static class Worker extends Thread {
		private final Process process;
		private Integer exit;
		private Worker(Process process) {
			this.process = process;
		}
		public void run() {
			try {
				exit = process.waitFor();
			} catch (InterruptedException ignore) {
				return;
			}
		}
	}
}
