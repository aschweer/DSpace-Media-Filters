package nz.ac.lconz.irr.mediafilter;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.dspace.app.mediafilter.MediaFilter;
import org.dspace.app.mediafilter.MediaFilterManager;
import org.dspace.app.mediafilter.SelfRegisterInputFormats;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Utils;

import java.io.*;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz
 *
 * See http://blog.prashanthellina.com/2008/02/03/create-pdf-thumbnails-using-imagemagick-on-linux/
 */
public class ImageMagickThumbnailer extends MediaFilter implements SelfRegisterInputFormats {

    private static Logger log = Logger.getLogger(ImageMagickThumbnailer.class);

    private static final String CONVERT_COMMAND = "@COMMAND@ -thumbnail @WIDTH@x @INFILE@[0] @OUTFILE@";

    // from thumbnail.thumbWidth in config
    private int thumbWidth = 200;
    private String convertPath;

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

    private int convert(String inFileName, String outFileName) throws IOException {
        int status;
        String commandLine = CONVERT_COMMAND.replaceFirst("@COMMAND@", convertPath);
        commandLine = commandLine.replaceFirst("@WIDTH@", String.valueOf(thumbWidth));
        commandLine = commandLine.replaceFirst("@INFILE@", inFileName);
        commandLine = commandLine.replaceFirst("@OUTFILE@", outFileName);

        try {
            log.info("About to run " + commandLine);
            Process convertProc = Runtime.getRuntime().exec(commandLine);
            status = convertProc.waitFor();
        } catch (InterruptedException ie) {
            log.error("Failed to create thumbnail: ", ie);
            throw new IllegalArgumentException("Failed to create thumbnail: ", ie);
        }
        return status;
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
