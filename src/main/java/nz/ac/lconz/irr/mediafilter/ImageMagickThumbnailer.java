package nz.ac.lconz.irr.mediafilter;

import org.apache.commons.exec.*;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.dspace.app.mediafilter.MediaFilter;
import org.dspace.app.mediafilter.MediaFilterManager;
import org.dspace.app.mediafilter.SelfRegisterInputFormats;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Utils;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz for LCoNZ
 *
 * See http://blog.prashanthellina.com/2008/02/03/create-pdf-thumbnails-using-imagemagick-on-linux/
 */
public class ImageMagickThumbnailer extends MediaFilter implements SelfRegisterInputFormats {

    private static Logger log = Logger.getLogger(ImageMagickThumbnailer.class);

	// from thumbnail.thumbWidth in config
    private int thumbWidth = 200;
    private String convertPath;
	private static final int CONVERT_TIMEOUT = 120 * 1000; // 120 ms = 2 minutes

	public String getFilteredName(String oldFilename) {
        return oldFilename + ".png";
    }

    public String getBundleName() {
        return "THUMBNAIL";
    }

    public String getFormatString() {
        return "image/png";
    }

    public String getDescription() {
        return "Generated Thumbnail";
    }

    public InputStream getDestinationStream(InputStream sourceStream) throws Exception {
        if (convertPath == null) {
            loadSettings();
        }
        if (convertPath == null) {
            System.out.println("Cannot read configuration for ImageMagick Thumbnailer");
            throw new IllegalStateException("No value for key \"imagemagick.path.convert\" in DSpace configuration!  Should be path to ImageMagick convert executable.");
        }

        String inFileName = makeTempInFile(sourceStream);
        System.out.println("Made temporary input file " + inFileName);
        
        String outFileName = inFileName + ".png";
        System.out.println("Thumbnail will be in " + outFileName);

        int status = 0;
        status = convert(inFileName, outFileName);

        if (status != 0)
        {
            log.error("Thumbnail creation failed, exit status=" + status + ", file=" + inFileName);
        }
        else
        {
            log.info("Created thumbnail in file " + outFileName);
            System.out.println("Created thumbnail");
        }

        FileInputStream fis = new FileInputStream(outFileName);
        byte[] imageData = IOUtils.toByteArray(fis);
        IOUtils.closeQuietly(fis);
        new File(outFileName).deleteOnExit();

        return new ByteArrayInputStream(imageData);
    }

    private int convert(String inFileName, String outFileName) throws Exception {
	    // from http://commons.apache.org/exec/tutorial.html
	    CommandLine cmdLine = new CommandLine(convertPath);
	    cmdLine.addArgument("-thumbnail");
	    // maximum size thumbWidth x thumbWidth; keep ratio; don't enlarge: http://www.imagemagick.org/Usage/thumbnails/#fit
	    cmdLine.addArgument("'" + thumbWidth + "x" + thumbWidth + ">'");
	    cmdLine.addArgument("${infile}[0]");
	    cmdLine.addArgument("${outfile}");
	    Map<String, File> map = new HashMap();
	    map.put("infile", new File(inFileName));
	    map.put("outfile", new File(outFileName));
	    cmdLine.setSubstitutionMap(map);
	    log.debug("about to run " + cmdLine.toString());

	    Executor executor = new DefaultExecutor();
	    ExecuteWatchdog watchdog = new ExecuteWatchdog(CONVERT_TIMEOUT);
	    executor.setWatchdog(watchdog);
	    executor.setWorkingDirectory(new File(System.getProperty("java.io.tmpdir")));

	    DefaultExecuteResultHandler resultHandler;
	    try {
	        resultHandler = new FilterResultHandler(watchdog);
	        executor.execute(cmdLine, resultHandler);
	    } catch (Exception e) {
		    log.error("Problem converting " + inFileName + " to " + outFileName, e);
		    throw e;
	    }
	    resultHandler.waitFor();
	    return resultHandler.getExitValue();
    }

    private String makeTempInFile(InputStream sourceStream) throws IOException {
        File sourceTmp = File.createTempFile("IMthumbSource" + sourceStream.hashCode(),".tmp");
        sourceTmp.deleteOnExit();
        try
        {
            OutputStream sto = new FileOutputStream(sourceTmp);
            Utils.copy(sourceStream, sto);
            sto.close();
        }
        finally
        {
            sourceStream.close();
        }

        if (!sourceTmp.canRead()) {
            log.error("Cannot read temporary file for input: " + sourceTmp.getCanonicalPath());
        }

        return sourceTmp.getCanonicalPath();
    }

    private void loadSettings() {
        System.out.println("Loading settings for ImageMagick Thumbnailer");
        convertPath = ConfigurationManager.getProperty("imagemagick.path.convert");
        thumbWidth = ConfigurationManager.getIntProperty("thumbnail.maxwidth", thumbWidth);
    }

    public String[] getInputMIMETypes() {
        return null;
    }

    public String[] getInputDescriptions() {
        return null;
    }

    public String[] getInputExtensions() {
        return new String[] {".pdf", ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".tif", ".tiff"};
    }
}
