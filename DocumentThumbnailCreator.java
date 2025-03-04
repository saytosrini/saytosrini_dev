package ext.lev.viz;

import java.util.Vector;
import wt.doc.WTDocument;
import wt.representation.Representation;
import wt.util.WTException;
import wt.pom.Transaction;
import com.ptc.wvs.server.loader.EDRLoader;
import com.ptc.wvs.server.util.CommandProcessor;
import org.apache.log4j.Logger;
import wt.log4j.LogR;
import wt.fc.Persistable;
import wt.method.RemoteAccess;

public class DocumentThumbnailCreator implements RemoteAccess {
    private static final String CLASSNAME = DocumentThumbnailCreator.class.getName();
    private static final Logger LOGGER = LogR.getLogger(CLASSNAME);

    public static void createDocumentThumbnail(WTDocument document, Representation rep) throws WTException {
        LOGGER.debug("Starting thumbnail creation for document: " + document.getNumber());
        
        Transaction trx = new Transaction();
        try {
            trx.start();
            
            boolean thumbnailSuccess = generateThumbnails(rep);
            
            if (thumbnailSuccess) {
                LOGGER.debug("Successfully created thumbnails for document: " + document.getNumber());
            } else {
                LOGGER.error("Failed to create thumbnails for document: " + document.getNumber());
            }
            
            trx.commit();
            trx = null;
            
        } catch (WTException e) {
            LOGGER.error("Error creating thumbnails for document: " + document.getNumber(), e);
            e.printStackTrace();
        } finally {
            if (trx != null) {
                trx.rollback();
            }
        }
    }
    
    private static boolean generateThumbnails(Representation rep) throws WTException {
        boolean thumbnailSuccess = false;
        boolean smallThumbnailSuccess = false;
        
        // Set up EDRLoader with thumbnail creation flag
        Vector<Object> args = new Vector<Object>(1);
        args.addElement("thumbnailcreate=true");
        EDRLoader loader = new EDRLoader(new CommandProcessor(args));
        
        try {
            // Generate both standard and small thumbnails
            thumbnailSuccess = loader.regenerateThumbnail(rep);
            smallThumbnailSuccess = loader.addSmallThumbnail(rep, true);
            
            LOGGER.debug("Thumbnail generation results - Standard: " + thumbnailSuccess + ", Small: " + smallThumbnailSuccess);
            
        } catch (Exception e) {
            LOGGER.error("Error during thumbnail generation", e);
            throw new WTException(e);
        }
        
        return thumbnailSuccess && smallThumbnailSuccess;
    }
}