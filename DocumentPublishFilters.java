package com.ptc.wvs.util;


import wt.content.ApplicationData;
import wt.content.ContentRoleType;
import wt.content.ContentItem;
import wt.doc.WTDocument;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.ptc.core.meta.common.TypeIdentifierHelper;

/**
 * Administrator wants to control what types get published by
 * filtering publish requests based on the ContentRoleType
 * In this example, WTDocuments are filtered by their ContentRoleType and only
 * Primary content will be published to make sure the Secondary Attachments are not published
 * 
 * Add following lines to $wt_home/codebase/WEB-INF/conf/wvs.properties to execute this hook:
 * publish.service.filterdocumentpublishmethod=com.ptc.wvs.util.DocumentPublishFilters/filterWTDocumentPublish
 * 
 * See the documentation in $wt_home/codebase/WEB-INF/conf/wvs.properties.xconf for more information
 */

public class DocumentPublishFilters {
    private static final Logger logger = LogManager.getLogger(DocumentPublishFilters.class.getName());
	private static final String TYPE_WORKAUTHORIZATION = "wt.doc.WTDocument|ext.sni.SNIDocument|com.smith_nephew.WorkAuthorization";
	/**
     * Method to do custom filtering of the WTDocuments that will be published
     * as a result of file upload or checkin
	 * This method is called for each ContentItem associated to the WTDocument on **Check-in**.
	 * It is also called for **Uploads**. For example if you had a WTDocument with
	 * .doc file as primary content and a .xls file as secondary content, this method would
	 * be called twice; once with each content item (pending a worker was associated to
	 * both types of content)
     * 
     * @param doc
     *            The WTDocument to filter
	 * @param ci
	 *			  This method differs from the EPMDocument filter method signature
	 *			  by including ContentItem as a parameter
     * @return True indicates the WTDocument should be published, FALSE that it should not
     */
	public static Boolean filterWTDocumentPublish(WTDocument doc, ContentItem ci) {
		boolean result = Boolean.TRUE;
		logger.info("Doing the DocumentPublishFilters filterWTDocumentPublish");
		if(isWorkAuthorizationDocumentType(doc)){
			ApplicationData appData = (ApplicationData) ci;
			if ( appData.getRole().equals(ContentRoleType.PRIMARY) ) {
				logger.info("This object is a Primary content of Work Authorization Document, publishing");
			}
			else{
				result = Boolean.FALSE;
			}
		}
		
		
		return result;
	}
	
	/**
	 * This Method checks if the Document Type is WorkAuthorization. If Yes, returns
	 * true.
	 * @param doc
	 * @return
	 */
	public static boolean isWorkAuthorizationDocumentType(WTDocument doc) {
		boolean isWorkAuthorizationDoc = false;
		logger.debug("Document TYpe ID  - " + TypeIdentifierHelper.getType(doc).getTypename());
		if (TypeIdentifierHelper.getType(doc).getTypename().toLowerCase()
				.contains(TYPE_WORKAUTHORIZATION.toLowerCase())) {
			isWorkAuthorizationDoc = true;
		}

		return isWorkAuthorizationDoc;
	}

    /**
     * Method called before all publishing (i.g. **Publish Scheduler**) to do custom filtering
	 * of Objects that can be published.
     * 
     * If required, configure with property:
     * publish.service.filterpublishmethod
     * 
     * @param persistable
     *            The persistable object to filter
     * @param fromDB
     *            is this data stored in Windchill
     * @return True if the object should be published, false otherwise
     */
/**
    public static Boolean filterPublish(Persistable persistable, Boolean fromDB) {
        logger.info("Doing the DocumentPublishFilters filterPublish");
		System.out.println("Doing the DocumentPublishFilters filterPublish");

		return Boolean.TRUE;
	}
**/

}